package caevo.util;

import java.util.List;
import java.util.Map;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.cleartk.syntax.constituent.type.TerminalTreebankNode;
import org.cleartk.syntax.constituent.type.TopTreebankNode;
import org.cleartk.syntax.constituent.type.TreebankNode;
import org.cleartk.timeml.type.DocumentCreationTime;
import org.cleartk.timeml.type.Event;
import org.cleartk.timeml.type.Time;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.uimafit.util.JCasUtil;

import caevo.SieveDocument;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.Timex;

import com.google.common.collect.Maps;

import edu.stanford.nlp.ling.CoreAnnotations.AfterAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.BeforeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.BeginIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.EndIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.Tree;

public class SieveJCasUtil {

	public static void fillJCasFromSieveDocument(JCas jCas, SieveDocument document) {
		// create sentences, tokens and document text
		StringBuffer documentText = new StringBuffer();
		Map<SieveSentence, Sentence> toCleartk = Maps.newHashMap();
		for (SieveSentence sentence : document.getSentences()) {
			List<CoreLabel> tokens = sentence.tokens();
			int sentBegin = documentText.length();
			int sentEnd = sentBegin;
			for (CoreLabel token : tokens) {
				if (documentText.length() == 0) {
					documentText.append(token.getString(BeforeAnnotation.class));
				}
				int tokenBegin = documentText.length();
				documentText.append(token.getString(OriginalTextAnnotation.class));
				int tokenEnd = documentText.length();
				sentEnd = documentText.length();
				documentText.append(token.getString(AfterAnnotation.class));
				Token cleartkToken = new Token(jCas, tokenBegin, tokenEnd);
				cleartkToken.addToIndexes();
			}
			Sentence cleartkSentence = new Sentence(jCas, sentBegin, sentEnd);
			cleartkSentence.addToIndexes();
			toCleartk.put(sentence, cleartkSentence);
		}
		jCas.setDocumentText(documentText.toString());

		// add document creation time
		for (Timex docTime : document.getDocstamp()) {
			DocumentCreationTime cleartkDocTime = new DocumentCreationTime(jCas, 0, 0);
			cleartkDocTime.setId(docTime.getTid());
			cleartkDocTime.setTimeType(docTime.getType().name());
			cleartkDocTime.addToIndexes();
		}

		// add events, times, POS tags and constituents
		for (SieveSentence sentence : document.getSentences()) {
			Sentence cleartkSentence = toCleartk.get(sentence);
			List<Token> cleartkTokens = JCasUtil.selectCovered(jCas, Token.class,
					cleartkSentence);

			// add events
			for (TextEvent event : sentence.events()) {
				Token token = cleartkTokens.get(event.getIndex() - 1);
				Event cleartkEvent = new Event(jCas, token.getBegin(), token.getEnd());
				cleartkEvent.setId(event.getEiid());
				cleartkEvent.setAspect(event.getAspect().name());
				cleartkEvent.setEventClass(event.getTheClass().name());
				cleartkEvent.setModality(event.getModality());
				cleartkEvent.setPolarity(event.getPolarity().name());
				cleartkEvent.setTense(event.getTense().name());
				cleartkEvent.addToIndexes();
				if (!cleartkEvent.getCoveredText().equals(event.getString())) {
					throw new RuntimeException(String.format(
							"expected event '%s', found '%s'", event.getString(),
							cleartkEvent.getCoveredText()));
				}
			}

			// add times
			for (Timex time : sentence.timexes()) {
				Token beginToken = cleartkTokens.get(time.getTokenOffset() - 1);
				Token endToken = cleartkTokens.get(time.getTokenOffset()
						+ time.getTokenLength() - 2);
				Time cleartkTime = new Time(jCas, beginToken.getBegin(),
						endToken.getEnd());
				cleartkTime.setId(time.getTid());
				cleartkTime.setTimeType(time.getType().name());
				cleartkTime.addToIndexes();
				// use \p{Z} instead of \s because there are non-breaking spaces sometimes
				if (!cleartkTime.getCoveredText().replaceAll("\\p{Z}", "")
						// the latter .replaceAll handles the buggy "2 1\/2 years" in Timex
						.equals(time.getText().replaceAll("\\p{Z}", "").replaceAll("\\\\/", "/"))) {
					throw new RuntimeException(String.format(
							"expected time '%s', found '%s'", time.getText(),
							cleartkTime.getCoveredText()));
				}
			}

			// add part-of-speech tags
			Tree tree = sentence.getParseTree();
			List<Label> preTerminals = tree.preTerminalYield();
			for (int i = 0; i < preTerminals.size(); ++i) {
				String pos = preTerminals.get(i).value();
				cleartkTokens.get(i).setPos(pos);
			}

			// add constituents
			tree.indexSpans();
			toTreebankNode(tree, jCas, null, cleartkTokens);
		}
	}

	private static TreebankNode toTreebankNode(Tree tree, JCas jCas,
			TreebankNode parent, List<Token> tokens) {
		CoreLabel label = (CoreLabel) tree.label();
		int beginToken = label.get(BeginIndexAnnotation.class);
		int endToken = label.get(EndIndexAnnotation.class) - 1;
		int begin = tokens.get(beginToken).getBegin();
		int end = tokens.get(endToken).getEnd();
		TreebankNode node;
		if (parent == null) {
			node = new TopTreebankNode(jCas, begin, end);
			node.setLeaf(false);
		} else if (tree.isPreTerminal()) {
			node = new TerminalTreebankNode(jCas, begin, end);
			node.setLeaf(true);
			node.setParent(parent);
		} else {
			node = new TreebankNode(jCas, begin, end);
			node.setLeaf(false);
			node.setParent(parent);
		}
		node.setNodeType(label.value());
		Tree[] children = tree.children();
		node.setChildren(new FSArray(jCas, children.length));
		for (int i = 0; i < children.length; ++i) {
			node.setChildren(i, toTreebankNode(children[i], jCas, node, tokens));
		}
		node.addToIndexes();
		return node;
	}

}
