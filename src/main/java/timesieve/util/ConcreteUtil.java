package timesieve.util;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import timesieve.*;
import timesieve.TextEvent.POLARITY;
import timesieve.tlink.TLink;
import edu.jhu.hlt.concrete.Concrete.*;
import edu.jhu.hlt.concrete.Concrete.Sentence;
import edu.jhu.hlt.concrete.Concrete.TokenTagging.TaggedToken;
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
		/* FIXME: Outstanding issues and questions
		 *  - Placed things like UUID in hashmaps... is this okay?
		 *  - If something is missing as part of a timex, entity, etc, should I just skip
		 *    over that entity? Or throw an exception?
		 */
		
		String file = ConcreteUtil.filenameFromCommunication(comm);
		HashMap<UUID, Timex> timexMap = new HashMap<UUID, Timex>();
		HashMap<UUID, TextEvent> textEventMap = new HashMap<UUID, TextEvent>();
		HashMap<Integer, List<Timex>> timexes = ConcreteUtil.timexesFromCommunication(comm, timexMap);
		HashMap<Integer, List<TextEvent>> textEvents = ConcreteUtil.textEventsFromCommunication(comm, textEventMap);
		List<TLink> tlinks = ConcreteUtil.tlinksFromCommunication(comm, textEventMap, timexMap);

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
		return ConcreteUtil.timexesFromCommunication(comm, new HashMap<UUID, Timex>());
	}
	
	private static HashMap<Integer, List<Timex>> timexesFromCommunication(Communication comm, HashMap<UUID, Timex> uuidOutputMap) {
		/* FIXME: Outstanding issues and questions
		 *  - Is entity mention sentence index starting at 0, and increasing with consecutive sentences?
		 *  - Timex type should be based on Entity.Type, but need DURATION... Need to add to Concrete?
		 *  - What to use for TID?  Currently just iterating.
		 *  - What to use for document function? Need to add to Concrete?
		 *  - What to use for timex preposition? Do we need this?
		 * 	- The code below assumes that all entity mentions have corresponding entities.
		 * 	- What is the difference between Entity.Type.X_VALUE and Entity.Type.X? Oh... one is int
		 *  - Get document creation time separately from timexes? Based on start time field (getStartTime())?
		 *  - Only gets first entitysetlist and first entitymentionlist
		 */
		
		HashMap<Integer, List<Timex>> timexes = new HashMap<Integer, List<Timex>>();
		int id = 0;
		
		if (comm.getEntitySetCount() == 0)
			return timexes;
		
		/* Extract parts of communication for easy access */
		List<Entity> entities = comm.getEntitySetList().get(0).getEntityList();
		HashMap<UUID, EntityMention> entityMentionMap = ConcreteUtil.entityMentionsFromCommuncation(comm);
		
		for (Entity entity : entities) {
			if (!entity.hasEntityType())
				continue;
			
			Entity.Type eType = entity.getEntityType();
			if (eType.equals(Entity.Type.DATE) ||
				eType.equals(Entity.Type.TIME)) {

				for (UUID mentionID : entity.getMentionList()) {
					if (!entityMentionMap.containsKey(mentionID))
						continue;
					
					EntityMention mention = entityMentionMap.get(mentionID);
				
					Timex time = new Timex();
					int groupId = ConcreteUtil.NONE_ID;
					
					if (mention.hasText())
						time.setText(mention.getText());
					
					if (mention.hasTextSpan()) {
						TextSpan span = mention.getTextSpan();
						time.setSpan(span.getStart(), span.getEnd());
					}
					
					time.setTID("t" + id);
					
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
					
					uuidOutputMap.put(entity.getUuid(), time);
					
					id++;
				}
			}
		}
	
		return timexes;
	}
	
	public static List<TLink> tlinksFromCommunication(Communication comm) {
		HashMap<UUID, TextEvent> textEvents = new HashMap<UUID, TextEvent>();
		HashMap<UUID, Timex> timexes = new HashMap<UUID, Timex>();
		
		ConcreteUtil.textEventsFromCommunication(comm, textEvents);
		ConcreteUtil.timexesFromCommunication(comm, timexes);
		
		return ConcreteUtil.tlinksFromCommunication(comm, textEvents, timexes);
	}
	
	private static List<TLink> tlinksFromCommunication(Communication comm, HashMap<UUID, TextEvent> textEvents, HashMap<UUID, Timex> timexes) {
		/* FIXME: Outstanding issues and questions
		 * 	- Only get first situation mention set and situation set... is that okay?
		 *  - Just want temporal fact type situations?
		 *  - Are TLink e1 and e2 the eids and tids?  Why not take events and timexes instead of strings?
		 *  - TLink closure is whether it's from transitive closure?
		 *  - Assumed that events and times linked by tlink are arguments of situation
		 *  	- Source and target determined by source role and target role?
		 *  			- Assumed that if first is not source role, then second is source role
		 */
		
		List<TLink> tlinks = new ArrayList<TLink>();
		
		if (comm.getSituationSetCount() == 0)
			return tlinks;
		
		List<Situation> situations = comm.getSituationSetList().get(0).getSituationList();		
		for (Situation situation : situations) {
			if (situation.getSituationType() != Situation.Type.TEMPORAL_FACT 
			|| !situation.hasTemporalFactType()
			|| situation.getArgumentCount() != 2
			|| !situation.getArgument(0).hasRole()
			|| !situation.getArgument(1).hasRole()
			|| !situation.getArgument(0).hasValueType()
			|| !situation.getArgument(1).hasValueType()
			|| !situation.getArgument(0).hasValue()
			|| !situation.getArgument(1).hasValue())
				continue;
			
			Situation.TemporalFactType temporalFactType = situation.getTemporalFactType();
			TLink.TYPE rel = TLink.TYPE.VAGUE;
			if (temporalFactType == Situation.TemporalFactType.AFTER_TEMPORAL_FACT)
				rel = TLink.TYPE.AFTER;
			else if (temporalFactType == Situation.TemporalFactType.BEFORE_TEMPORAL_FACT)
				rel = TLink.TYPE.BEFORE;
			else if (temporalFactType == Situation.TemporalFactType.INCLUDES_TEMPORAL_FACT)
				rel = TLink.TYPE.INCLUDES;
			else if (temporalFactType == Situation.TemporalFactType.IS_INCLUDED_BY_TEMPORAL_FACT)
				rel = TLink.TYPE.IS_INCLUDED;
			else if (temporalFactType == Situation.TemporalFactType.SIMULTANEOUS_TEMPORAL_FACT)
				rel = TLink.TYPE.SIMULTANEOUS;
			
			
			Situation.Argument sourceArgument = null, targetArgument = null;
			if (situation.getArgument(0).getRole() == Situation.Argument.Role.RELATION_SOURCE_ROLE) {
				sourceArgument = situation.getArgument(0);
				targetArgument = situation.getArgument(1);
			} else { // Assume other is source 
				sourceArgument = situation.getArgument(1);
				targetArgument = situation.getArgument(0);	
			}
			
			String sourceId = null;
			if (timexes.containsKey(sourceArgument.getValue()))
				sourceId = timexes.get(sourceArgument.getValue()).tid();
			else if (textEvents.containsKey(sourceArgument.getValue()))
				sourceId = textEvents.get(sourceArgument.getValue()).id();
			else
				continue;

			String targetId = null;
			if (timexes.containsKey(targetArgument.getValue()))
				sourceId = timexes.get(targetArgument.getValue()).tid();
			else if (textEvents.containsKey(targetArgument.getValue()))
				sourceId = textEvents.get(targetArgument.getValue()).id();
			else
				continue;
			
			TLink tLink = new TLink(sourceId, targetId, rel); 
			
			if (situation.hasConfidence()) {
				tLink.setRelationConfidence(situation.getConfidence());
			}
			
			tlinks.add(tLink);
		}
		
		return tlinks;
	}
	
	public static HashMap<Integer, List<TextEvent>> textEventsFromCommunication(Communication comm) {
		return ConcreteUtil.textEventsFromCommunication(comm, new HashMap<UUID, TextEvent>());
	}
	
	private static HashMap<Integer, List<TextEvent>> textEventsFromCommunication(Communication comm, HashMap<UUID, TextEvent> uuidOutputMap) {
		/* FIXME: Outstanding issues and questions
		 * 	- Only get first situation mention set and situation set... is that okay?
		 *  - Want both event and state type situations? What about situation type situations?
		 *  - Why can both situations and situation mentions both have arguments?
		 *  	- Skipped over entity arguments because not sure what TextEvent wants for an EntityRelation
		 *  - Most of the fields in textEvent don't allow for multiple mentions (instances), so just
		 *  	took first SituationMention for now 
		 *  - Assumed only want events that are mentioned in the text (at least one justification)
		 *  - Ignored the following text event properties for now:
		 *  				textEvent.addDominance(id);										(what is this?)
		 *					textEvent.addEntityRelation(index, relation); (what is relation supposed to be?)
		 *					textEvent.addPrepConstraint(prep);						(not in Concrete)
		 *					textEvent.setPOSTags(p);											(tags for what? each instance? or around single instance?)
		 *  - Is it possible to get pointer from SituationMention to sentence index in concrete?
		 *  		- Otherwise need to get it here... a bit cumbersome. (event mentions have sentence indexes)
		 *	- Used iterator to generate eids and eiids... is that okay? (eiid=eid for now)
		 *  - Easy way to get position of first word of event mention in sentence? (concrete)
		 *  - Just took first TextSpan from mention in concrete... since only one text field in TextEvent
		 *  - Concrete has neutral polarity, should text event have it?
		 *  - Why do all the text event methods take strings when there are enums available?
		 *  - Concrete doens't have event "class" (OCCURRENCE, ASPECTUAL, STATE, I_ACTION, I_STATE, REPORTING, PERCEPTION, NONE)
		 *  	- Also missing tense (PRESENT, PRESPART, PAST, PASTPART, INFINITIVE, FUTURE, PASSIVE, NONE)
		 *    - And aspect (PROGRESSIVE, PERFECTIVE, IMPERFECTIVE, PERFECTIVE_PROGRESSIVE, IMPERFECTIVE_PROGRESSIVE, NONE)
		 *		- And modality
		 *	- What is the happened property in TextEvent (TimeML and Concrete don't have this)
		 */
		
		HashMap<Integer, List<TextEvent>> textEvents = new HashMap<Integer, List<TextEvent>>();
		
		if (comm.getSituationSetCount() == 0)
			return textEvents;
		
		List<Situation> situations = comm.getSituationSetList().get(0).getSituationList();
		HashMap<UUID, SituationMention> situationMentionMap = ConcreteUtil.situationMentionsFromCommuncation(comm);
		int id = 0; // Used as both eid and eiid for now
		for (Situation situation : situations) {
			if (situation.getSituationType() != Situation.Type.EVENT && situation.getSituationType() != Situation.Type.STATE)
				continue;
			if (situation.getJustificationCount() == 0 || !situationMentionMap.containsKey(situation.getJustificationList().get(0).getMention()))
				continue;
			
			/* Just get first mention for now */
			SituationMention situationMention = situationMentionMap.get(situation.getJustificationList().get(0).getMention());
			
			if (situationMention.getTextSpanCount() == 0)
				continue;
			
			TextSpan mentionTextSpan = situationMention.getTextSpanList().get(0);
			int sid = ConcreteUtil.NONE_ID; /* FIXME*/
			TextEvent textEvent = new TextEvent(comm.getText().substring(mentionTextSpan.getStart(), mentionTextSpan.getEnd()),
																					"e" + id, sid, 0); /* FIXME: Need sentence id and index in sentence */
			textEvent.setEiid("ei" + id);
			
			if (situationMention.hasPolarity()) {
				Situation.Polarity polarity = situationMention.getPolarity();
				if (polarity == Situation.Polarity.POSITIVE_POLARITY)
					textEvent.setPolarity("POS");
				else if (polarity == Situation.Polarity.NEGATIVE_POLARITY)
					textEvent.setPolarity("NEG");
			}
			
			if (!textEvents.containsKey(sid))
				textEvents.put(sid, new ArrayList<TextEvent>());
			textEvents.get(sid).add(textEvent);
			
			uuidOutputMap.put(situation.getUuid(), textEvent);
			
			id++;
		}
		
		return textEvents;
	}
	
	private static HashMap<UUID, EntityMention> entityMentionsFromCommuncation(Communication comm) {
		HashMap<UUID, EntityMention> mentionMap = new HashMap<UUID, EntityMention>();
		
		if (comm.getEntityMentionSetCount() == 0)
			return mentionMap;
		
		List<EntityMention> mentionList = comm.getEntityMentionSetList().get(0).getMentionList();
		for (EntityMention mention : mentionList) {
			if (mention.hasUuid())
				mentionMap.put(mention.getUuid(), mention);
		}
		
		return mentionMap;
	}
	
	private static HashMap<UUID, SituationMention> situationMentionsFromCommuncation(Communication comm) {
		HashMap<UUID, SituationMention> mentionMap = new HashMap<UUID, SituationMention>();
		
		if (comm.getSituationMentionSetCount() == 0)
			return mentionMap;
		
		List<SituationMention> mentionList = comm.getSituationMentionSetList().get(0).getMentionList();
		for (SituationMention mention : mentionList) {
			if (mention.hasUuid())
				mentionMap.put(mention.getUuid(), mention);
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
		 *   - How to build dependency graph and parse tree for sentence?  How to turn into string?
		 *   		- Currently skipped this
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
		/* FIXME */
		
		return null;
	}
}
