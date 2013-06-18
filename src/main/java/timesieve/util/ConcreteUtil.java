package timesieve.util;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import timesieve.*;
import timesieve.tlink.TLink;
import edu.jhu.hlt.concrete.Concrete.*;
import edu.jhu.hlt.concrete.Concrete.Sentence;
import edu.jhu.hlt.concrete.Concrete.TokenTagging.TaggedToken;
import edu.jhu.hlt.concrete.Concrete.Tokenization.Kind;
import edu.jhu.hlt.concrete.Concrete.Tokenization.TokenLattice;
import edu.stanford.nlp.ling.CoreLabel;

/**
 * Methods to help with using Concrete (https://github.com/hltcoe/concrete) data sources
 * @author Bill McDowell
 */
public class ConcreteUtil {
	private static final int NONE_ID = -1;
	
	/*
	 *  Methods for transforming communication to info file
	 */
	
	public static InfoFile communicationToInfoFile(Collection<Communication> comms, InfoFile info) {
		for (Communication comm : comms)
			info = ConcreteUtil.communicationToInfoFile(comm, info);
		return info;
	}
	
	public static InfoFile communicationToInfoFile(Collection<Communication> comms) {
		InfoFile info = new InfoFile();
		
		for (Communication comm : comms)
			info = ConcreteUtil.communicationToInfoFile(comm, info);
		
		return info;
	}
	
	public static InfoFile communicationToInfoFile(Communication comm) {
		InfoFile info = new InfoFile();
		return ConcreteUtil.communicationToInfoFile(comm, info);
	}
	
	public static InfoFile communicationToInfoFile(Communication comm, InfoFile info) {
		String file = ConcreteUtil.filenameFromCommunication(comm);
		HashMap<Integer, List<Timex>> timexes = ConcreteUtil.timexesFromCommunication(comm);
		HashMap<Integer, List<TextEvent>> textEvents = ConcreteUtil.textEventsFromCommunication(comm);
		List<TLink> tlinks = ConcreteUtil.tlinksFromCommunication(comm);

		if (file == null)
			throw new IllegalArgumentException();
		
		/* Transfer Sentences */
		ConcreteUtil.sentencesCommunicationToInfoFile(comm, info);
		
		/* Transfer Timexes */
		for (Entry<Integer, List<Timex>> eTimex : timexes.entrySet()) {
			if (eTimex.getKey() >= 0)
				info.addTimexes(file, eTimex.getKey(), eTimex.getValue());
			else
				info.addTimexes(file, eTimex.getValue());
		}
		
		/* Transfer TextEvents */
		for (Entry<Integer, List<TextEvent>> eTextEvent : textEvents.entrySet()) {
			if (eTextEvent.getKey() >= 0)
				info.addEvents(file, eTextEvent.getKey(), eTextEvent.getValue());
		}
		
		/* Transfer TLinks */
		info.addTlinks(file, tlinks);
		
		return info;
	}
	
	public static String filenameFromCommunication(Communication comm) {
		String file = null;
		if (comm.hasGuid())
			file = comm.getGuid().getCommunicationId();
		else if (comm.hasUuid())
			file = comm.getUuid().toString();
		
		return file;
	}
	
	public static HashMap<Integer, List<Timex>> timexesFromCommunication(Communication comm) {
		/* FIXME: Outstanding issues and questions
		 *  - Is entity mention sentence index starting at 0, and increasing with consecutive sentences?
		 *  - Timex type should be based on Entity.Type, but need DURATION... Need to add to Concrete?
		 *  - What to use for TID?  Need to add to Concrete?
		 *  - What to use for document function? Need to add to Concrete?
		 *  - What to use for timex preposition? Do we need this?
		 * 	- The code below assumes that all entity mentions have corresponding entities.
		 * 	- What is the difference between Entity.Type.X_VALUE and Entity.Type.X?
		 *  - Get document creation time separately from timexes? Based on start time field (getStartTime())?
		 */
		
		HashMap<Integer, List<Timex>> timexes = new HashMap<Integer, List<Timex>>();
		
		/* Extract parts of communication for easy access */
		List<Entity> entities = ConcreteUtil.entitiesFromCommunication(comm);
		HashMap<UUID, EntityMention> entityMentionMap = ConcreteUtil.entityMentionsFromCommuncation(comm);
		
		for (Entity entity : entities) {
			if (!entity.hasEntityType())
				continue;
			
			Entity.Type eType = entity.getEntityType();
			if (eType.equals(Entity.Type.DATE) ||
				eType.equals(Entity.Type.DATE_VALUE) ||
				eType.equals(Entity.Type.TIME) ||
				eType.equals(Entity.Type.TIME_VALUE)) {

				for (UUID mentionID : entity.getMentionList()) {
					EntityMention mention = entityMentionMap.get(mentionID);
				
					Timex time = new Timex();
					int groupId = ConcreteUtil.NONE_ID;
					
					if (mention.hasText())
						time.setText(mention.getText());
					
					if (mention.hasTextSpan()) {
						TextSpan span = mention.getTextSpan();
						time.setSpan(span.getStart(), span.getEnd());
					}
					
					/* FIXME: Add in time.setTID() */
					/* FIXME: Add in time.setType() */
					/* FIXME: Add in time.setDocFunction() */
					/* FIXME: Add in time.setPrep() */
					
					if (entity.hasCanonicalName())
						time.setValue(entity.getCanonicalName());
				

					if (mention.hasSentenceIndex()) {
						time.setSID(mention.getSentenceIndex());
						groupId = time.sid();
					} 
					
					if (!timexes.containsKey(groupId))
						timexes.put(groupId, new ArrayList<Timex>());
					timexes.get(groupId).add(time);
				}
			}
		}
	
		return timexes;
	}
	
	public static HashMap<Integer, List<TextEvent>> textEventsFromCommunication(Communication comm) {
		/* FIXME */
		/*Situation s;
		SituaionMention se;
		se.*/; 
		// Sentences: comm.getSectionSegmentationList();
		// TLinks and Events: comm.getSituationMentionSetList();
		// TLinks and Events: comm.getSituationSetList();
		// Plain text: comm.getText();
		return null;
	}
	
	public static List<TLink> tlinksFromCommunication(Communication comm) {
		/* FIXME */
		return null;
	}
	
	private static List<Entity> entitiesFromCommunication(Communication comm) {
		List<Entity> entities = new ArrayList<Entity>();
		List<EntitySet> entitySets = comm.getEntitySetList();
		for (EntitySet entitySet : entitySets) {
			entities.addAll(entitySet.getEntityList());
		}
		return entities;
	}
	
	private static HashMap<UUID, EntityMention> entityMentionsFromCommuncation(Communication comm) {
		HashMap<UUID, EntityMention> mentionMap = new HashMap<UUID, EntityMention>();
		List<EntityMentionSet> mentionSets = comm.getEntityMentionSetList();
		for (EntityMentionSet mentionSet : mentionSets) {
			List<EntityMention> mentionList = mentionSet.getMentionList();
			for (EntityMention mention : mentionList) {
				if (mention.hasUuid())
					mentionMap.put(mention.getUuid(), mention);
			}
		}
		
		return mentionMap;
	}
	
	private static void sentencesCommunicationToInfoFile(Communication comm, InfoFile info) {
		/* FIXME: Outstanding issues and questions
		 * 	- Currently, this just takes the first segmentation from the communication.  Is this okay?
		 *		- Same with tokenizations and parses
		 *  - Ignores concrete lattice tokenizations
		 *  - Ignored the following fields in CoreLabels:
		 *  	  	.setAfter(after);
		 *				.setBefore(before);
		 *				.setCapacity(newSize);
		 *				.setCategory(category);
		 *				.setDocID(docID);
		 *				.setFromString(labelStr);
		 *				.setIndex(index);								
		 *				.setSentIndex(sentIndex);			
		 *				.setValue(value);
		 *				.setWord(word);
		 *   - Generally not sure about what each field in CoreLabel is for
		 */
		if (comm.getSectionSegmentationCount() == 0)
			return;
		
		String commText = comm.getText();
		
		List<Section> sections = comm.getSectionSegmentationList().get(0).getSectionList();
		int sid = 0;
		for (Section section : sections) {
			if (section.getSentenceSegmentationCount() == 0)
				continue;
			
			List<Sentence> sentences = section.getSentenceSegmentationList().get(0).getSentenceList();
			for (Sentence sentence : sentences) {
				String sText = "";
				List<CoreLabel> sTokens = new ArrayList<CoreLabel>();
				String sParse = "";
				String sDeps = "";
				
				if (sentence.hasTextSpan())
					sText = commText.substring(sentence.getTextSpan().getStart(), sentence.getTextSpan().getEnd());
				
				if (sentence.getTokenizationCount() > 0) {
					List<Tokenization> toks = sentence.getTokenizationList();
					for (Tokenization tok : toks) {
						if (!tok.hasKind() || tok.getKind() == Tokenization.Kind.TOKEN_LIST) {
							List<Token> tokens = tok.getTokenList();
							HashMap<Integer, CoreLabel> tokenIdsToCoreLabels = new HashMap<Integer, CoreLabel>();
							for (int i = 0; i < tokens.size(); i++) {
								Token token = tokens.get(i);
								CoreLabel sToken = new CoreLabel();
								if (!token.hasTokenId() || (!token.hasText() && !token.hasTextSpan()))
									continue;
								
								if (token.hasText())
									sToken.setOriginalText(token.getText());
								
								if (token.hasTextSpan()) {
									sToken.setBeginPosition(token.getTextSpan().getStart());
									sToken.setEndPosition(token.getTextSpan().getEnd());
								}
								
								sTokens.add(sToken);
								tokenIdsToCoreLabels.put(token.getTokenId(), sToken);
							}
							
							if (tok.getLemmasCount() > 0) {
								List<TaggedToken> lemmas = tok.getLemmasList().get(0).getTaggedTokenList();
								for (TaggedToken lemma : lemmas) {
									if (lemma.hasTokenId() && lemma.hasTag() && tokenIdsToCoreLabels.containsKey(lemma.getTokenId()))
										tokenIdsToCoreLabels.get(lemma.getTokenId()).setLemma(lemma.getTag());
								}
							}
							
							if (tok.getNerTagsCount() > 0) {
								List<TaggedToken> ners = tok.getNerTagsList().get(0).getTaggedTokenList();
								for (TaggedToken ner : ners) {
									if (ner.hasTokenId() && ner.hasTag() && tokenIdsToCoreLabels.containsKey(ner.getTokenId()))
										tokenIdsToCoreLabels.get(ner.getTokenId()).setNER(ner.getTag());
								}
							}
							
							if (tok.getPosTagsCount() > 0) {
								List<TaggedToken> poss = tok.getPosTagsList().get(0).getTaggedTokenList();
								for (TaggedToken pos : poss) {
									if (pos.hasTokenId() && pos.hasTag() && tokenIdsToCoreLabels.containsKey(pos.getTokenId()))
										tokenIdsToCoreLabels.get(pos.getTokenId()).setTag(pos.getTag());
								}
							}
							
							break;
						}
					}				
				}
					
				if (sentence.getParseCount() > 0) {
					/* FIXME */
				}
				
				if (sentence.getDependencyParseCount() > 0) {
					/* FIXME */
				}
					
				info.addSentence(ConcreteUtil.filenameFromCommunication(comm), 
												sid, 
												sText, 
												sTokens, 
												sParse, 
												sDeps, 
												new ArrayList<TextEvent>(), 
												new ArrayList<Timex>());
				sid++;
			}
		}
		
	}
	
	/*
	 *  Methods for transforming info file to communication
	 */
	
	public static Communication infoFileToCommunication(InfoFile info) {
		return null;
	}
}
