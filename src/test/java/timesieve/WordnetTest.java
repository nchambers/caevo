package timesieve;

import timesieve.util.WordNet;
import junit.framework.TestCase;

/**
 * A few quick WordNet operations to make sure it is loaded properly.
 */
public class WordnetTest extends TestCase {

	public void testWordnet() {
		WordNet wordnet = new WordNet();
		
		testLemmas(wordnet);
		testSynsets(wordnet);		
	}
	
	private void testLemmas(WordNet wordnet) {
		assertEquals("Checking WordNet lemmatization.", wordnet.lemmatizeTaggedWord("running", "VBG"), "run");
		assertEquals("Checking WordNet lemmatization.", wordnet.lemmatizeTaggedWord("were", "VBG"), "be");
		assertEquals("Checking WordNet lemmatization.", wordnet.lemmatizeTaggedWord("men", "NNS"), "man");
	}
	
	private void testSynsets(WordNet wordnet) {
		assertEquals("Checking WordNet synset paths for physical objects.", true, wordnet.isPhysicalObject("house"));
		assertEquals("Checking WordNet synset paths for persons.", true, wordnet.isNounPersonOrGroup("soldier"));
		assertEquals("Checking WordNet synset paths for persons.", false, wordnet.isNounPersonOrGroup("knife"));
	}

}
