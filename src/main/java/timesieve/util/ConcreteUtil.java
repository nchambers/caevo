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
import edu.jhu.hlt.concrete.util.IdUtil;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TypedDependency;

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
				
				Tokenization tokenization = null;
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
									sToken.setValue(token.getText());
								
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
							
							tokenization = tok;
							
							break;
						}
					}				
				}
				
				if (tokenization != null && sentence.getParseCount() > 0) {
						Tree parseTree = ConcreteUtil.communicationParseToTree(sentence.getParse(0), tokenization);
						if (parseTree != null)
							sParse = parseTree.toString();
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
	
	private static Tree communicationParseToTree(Parse parse, Tokenization tokens) {
		/* FIXME: Outstanding issues and questions
		 * - Why is parse stored as a string
		 * 		- Used Tree.toString to get this... is this the right thing to do?
		 * - Used LabeledScoredTreeFactory
		 * - Assumed that sentences are not extremely long, making ginormous parse trees
		 * 		- Used straightforward recursive implementation which could cause stack overflow
		 * - Only added token ref sequence to leaf constituents
		 * - Ignored the head constituent designation in converting to tree
		 * - Just used first tokenization for now... and threw exception if it doesn't line up
		 * - Assumed I should just concatenate the tokens in token ref seq to make the leaf strings
		 * - Assumed the number of tokens in a tokenization is small, and just iterated to find tokens by token id
		 */
		
		if (!parse.hasRoot())
			return null;
		TreeFactory treeFactory = new LabeledScoredTreeFactory();
		return ConcreteUtil.communicationParseToTreeHelper(parse.getRoot(), tokens, treeFactory);
	}
	
	private static Tree communicationParseToTreeHelper(Parse.Constituent constituent, Tokenization tokens, TreeFactory factory) {
		if (constituent.getChildCount() == 0) { // Leaf
			TokenRefSequence tokenRefSeq = constituent.getTokenSequence();
			if (tokenRefSeq.hasTokenization() && !tokenRefSeq.getTokenization().equals(tokens.getUuid()))
				throw new IllegalArgumentException();
			
			List<Integer> tokenRefs = tokenRefSeq.getTokenIdList();
			StringBuilder leafLabel = new StringBuilder();
			for (Integer tokenRef : tokenRefs) {
				for (Token token : tokens.getTokenList())
					if (token.hasText() && token.hasTokenId() && token.getTokenId() == tokenRef)
						leafLabel.append(token.getText());
			}
			
			return factory.newLeaf(leafLabel.toString());
		} else { // Not leaf
			List<Parse.Constituent> children = constituent.getChildList();
			List<Tree> treeChildren = new ArrayList<Tree>();
			for (Parse.Constituent child : children) {
				treeChildren.add(ConcreteUtil.communicationParseToTreeHelper(child, tokens, factory));
			}
			return factory.newTreeNode(constituent.getTag(), treeChildren);
		}
	}
	
	/*
	 *  Methods for transforming info file to communication
	 */
	
	public static List<Communication> infoFileToCommunication(String corpusName, InfoFile info) {
		/* FIXME: Unresolved issues and questions
		 * 	- Used IdUtil to generate UUID... is this the right thing to do?
		 *  - Communications require a corpus name in the identifier... should InfoFile also have this
		 *  	- Right now, I just have it passed into the function separately from the InfoFile
		 *  	- And InfoFile file names line up with Communication GUID communicationIds
		 *  - Why is KnowledgeGraph required in Communication?
		 *  - I left AnnotationMetaData fields blank for now... should these be filled in?
		 *  	- For example, what tool outputs the sentence segmentation?
		 */
		
		List<String> files = info.getFiles();
		List<Communication> communications = new ArrayList<Communication>();
		
		for (String file : files) {
			List<Sentence> sentences = new ArrayList<Sentence>();
			String text = ConcreteUtil.textDataFromInfoFile(info, file, sentences);
			
			List<Entity> timexEntities = new ArrayList<Entity>();
			List<EntityMention> timexEntityMentions = new ArrayList<EntityMention>();
			HashMap<String, UUID> timexIdMap = new HashMap<String, UUID>();
			ConcreteUtil.timexEntitiesFromInfoFile(info, file, timexEntities, timexEntityMentions, timexIdMap);

			List<Situation> eventSituations = new ArrayList<Situation>();
			List<SituationMention> eventSituationMentions = new ArrayList<SituationMention>();
			HashMap<String, UUID> eventIdMap = new HashMap<String, UUID>();		
			ConcreteUtil.eventSituationsFromInfoFile(info, file, eventSituations, eventSituationMentions, eventIdMap);
			
			List<Situation> tlinkSituations = new ArrayList<Situation>();
			ConcreteUtil.tlinkSituationsFromInfoFile(info, file, tlinkSituations, timexIdMap, eventIdMap);
																
			communications.add(
				Communication.newBuilder()
					.setUuid(IdUtil.generateUUID())
					.setGuid(
						CommunicationGUID.newBuilder()
							.setCorpusName(corpusName)
							.setCommunicationId(file)
							.build()
					)
					.setText(text)
					.addSectionSegmentation(
						SectionSegmentation.newBuilder()
						.setUuid(IdUtil.generateUUID())
						.addSection(
							Section.newBuilder()
								.setUuid(IdUtil.generateUUID())
								.setTextSpan(
									TextSpan.newBuilder()
										.setStart(0)
										.setEnd(text.length())
										.build()
								)
								.addSentenceSegmentation(
									SentenceSegmentation.newBuilder()
										.setUuid(IdUtil.generateUUID())
										.addAllSentence(sentences)
								)
								.build()
						)
						.build()
					)
					.addEntityMentionSet(
						EntityMentionSet.newBuilder()
							.setUuid(IdUtil.generateUUID())
							.addAllMention(timexEntityMentions)
							.build()
					)
					.addEntitySet(
						EntitySet.newBuilder()
							.setUuid(IdUtil.generateUUID())
							.addAllEntity(timexEntities)
							.build()
					)
					.addSituationMentionSet(
						SituationMentionSet.newBuilder()
							.setUuid(IdUtil.generateUUID())
							.addAllMention(eventSituationMentions)
							.build()
					)
					.addSituationSet(
						SituationSet.newBuilder()
							.setUuid(IdUtil.generateUUID())
							.addAllSituation(eventSituations)
							.addAllSituation(tlinkSituations)
							.build()
					)
					.build()
			);
		}
		
		return communications;
	}
	
	private static String textDataFromInfoFile(InfoFile info, String file, List<Sentence> sentences) {
		/* FIXME: Outstanding issues and questions
		 * - Still need to do dependency and parse conversion
		 * - Assumed token text is just the coreLabel's "value" (instead of "original text")
		 * 	- Not sure how begin and end position in core label relate to this?  Are they what should be used by text span?
		 * - Used iterator to fill in token_ids
		 * - It seems like InfoFile should have a method for returning just the text of the file
		 *    - Instead of just in terms of CoreLabels and sentences...
		 *    - It looks like the sentences in the example info file are constructed incorrectly (there is a space before each period)
		 *    - I reconstructed text by taking each CoreLabels middle value and last value (since first and last
		 *      values of consecutive CoreLabels overlap
		 *  - Why are the dependency trees not part of the infofile sentence objects?
		 *  - Currently, I assume that all InfoFile sentences have parse and dependency trees, is that okay?
		 */
		
		StringBuilder text = new StringBuilder();
		List<timesieve.Sentence> infoSentences = info.getSentences(file);
		List<String> dependencyStrs = info.getDependencies(file);
		
		if (infoSentences.size() != dependencyStrs.size())
			throw new IllegalArgumentException();
		
		for (int i = 0; i < infoSentences.size(); i++) {
			List<Token> tokens = new ArrayList<Token>();
			List<TaggedToken> lemmas = new ArrayList<TaggedToken>();
			List<TaggedToken> nerTags = new ArrayList<TaggedToken>();
			List<TaggedToken> posTags = new ArrayList<TaggedToken>();
			
			timesieve.Sentence infoSentence = infoSentences.get(i);
			List<CoreLabel> coreLabels = infoSentence.tokens();
			int tokenId = 0;
			for (CoreLabel coreLabel : coreLabels) {
				if (coreLabel.lemma() != null) {
					lemmas.add(
						TaggedToken.newBuilder()
							.setTag(coreLabel.lemma())
							.setTokenId(tokenId)
							.build()
					);
				}
				
				if (coreLabel.ner() != null) {
					nerTags.add(
						TaggedToken.newBuilder()
							.setTag(coreLabel.ner())
							.setTokenId(tokenId)
							.build()
					);					
				}
				
				if (coreLabel.tag() != null) {
					posTags.add(
						TaggedToken.newBuilder()
							.setTag(coreLabel.tag())
							.setTokenId(tokenId)
							.build()
					);						
				}
				
				tokens.add(
					Token.newBuilder()
						.setTokenId(tokenId)
						.setTextSpan(
							TextSpan.newBuilder()
								.setStart(coreLabel.beginPosition())
								.setEnd(coreLabel.endPosition())
								.build()
					  )
						.setText(coreLabel.value())
						.build()
				);
				
				tokenId++;
			}
			
			//Tree parseTree = Tree.valueOf(parseStr);
			Parse parse = null;
			/* FIXME: Do Parse */
			
			//TypedDependency dependencyTree = TreeOperator.stringToDependency(dependencyStr);
			DependencyParse dependencyParse = null;
			/* FIXME: Do Dependency */
			
			text.append(infoSentence.sentence());
			
			sentences.add(
				Sentence.newBuilder()
					.setUuid(IdUtil.generateUUID())
					.setTextSpan(
						TextSpan.newBuilder()
							.setStart(text.length()-infoSentence.sentence().length())
							.setEnd(text.length())
							.build()
					)
					.addTokenization(
						Tokenization.newBuilder()
							.setUuid(IdUtil.generateUUID())
							.setKind(Kind.TOKEN_LIST)
							.addPosTags(
								TokenTagging.newBuilder()
									.setUuid(IdUtil.generateUUID())
									.addAllTaggedToken(posTags)
									.build()
							)
							.addNerTags(
								TokenTagging.newBuilder()
									.setUuid(IdUtil.generateUUID())
									.addAllTaggedToken(nerTags)
									.build()
							)
							.addLemmas(
									TokenTagging.newBuilder()
										.setUuid(IdUtil.generateUUID())
										.addAllTaggedToken(lemmas)
										.build()
							)
							.addAllToken(tokens)
							.build()
					)
					.addParse(parse)
					.addDependencyParse(dependencyParse)
					.build()
			);
		}
		
		return text.toString();
	}
	
	private static void timexEntitiesFromInfoFile(InfoFile info, String file, List<Entity> entities, List<EntityMention> entityMentions, HashMap<String, UUID> timexIdMap) {
		/* FIXME: Outstanding issues and questions
		 * 	- This makes one entity mention per timex entity (even if same time has multiple mentions)
		 *  - Left out TextSpan and HeadIndex (timex doesn't have them)
		 *  - Mapped duration to time for now
		 *  - Why no enum for timex types?
		 */
		
		List<Timex> timexes = info.getTimexes(file);
		for (Timex timex : timexes) {
			UUID entityMentionId = IdUtil.generateUUID();
			UUID entityId = IdUtil.generateUUID();
			
			timexIdMap.put(timex.tid(), entityId);
			
			entityMentions.add(
				EntityMention.newBuilder()
					.setUuid(entityMentionId)
					.setText(timex.text())
					.setSentenceIndex(timex.sid())
					.setEntityType((timex.type().equals("DATE") ? Entity.Type.DATE : Entity.Type.TIME))
					.build()
			);
			
			entities.add(
				Entity.newBuilder()
					.setUuid(entityId)
					.setEntityType((timex.type().equals("DATE") ? Entity.Type.DATE : Entity.Type.TIME))
					.setCanonicalName(timex.value())
					.addMention(entityMentions.get(entityMentions.size() - 1).getUuid())
					.build()
			);
		}
	}
	
	private static void eventSituationsFromInfoFile(InfoFile info, String file, List<Situation> situations, List<SituationMention> situationMentions, HashMap<String, UUID> eventIdMap) {
		/* FIXME: Outstanding issues and questions
		 * - Event type and state type and arguments left out for now
		 * - Left out arguments, tokens, class, entities, situationkindlemma etc.
		 * - Situation mentions take either textspan(s) or pointers to tokens.
		 * 	- Neither of these are directly available through TextEvent 
		 * - Situation type is either state or event for now... (but should map to the class)
		 * 	- Why are textEvent enums converted to strings?
		 * - Assumed all justification types should be direct_mention
		 * - Only added polarity to SituationMention and not Situation.
		 * 	- Seems odd for situations to also have polarity
		 */
		
		List<TextEvent> textEvents = info.getEvents(file);
		for (TextEvent textEvent : textEvents) {
			UUID situationId = IdUtil.generateUUID();
			UUID situationMentionId = IdUtil.generateUUID();
			
			Situation.Type situationType = Situation.Type.EVENT;
			if (textEvent.getClass() != null && textEvent.getClass().equals("STATE"))
				situationType = Situation.Type.STATE;
			
			
			SituationMention.Builder situationMentionBuilder =
				SituationMention.newBuilder()
					.setUuid(situationMentionId)
					.setSituationType(situationType);
			
			if (textEvent.getPolarity() != null) {
				situationMentionBuilder.setPolarity(
					textEvent.getPolarity().equals("POS") ? 
						Situation.Polarity.POSITIVE_POLARITY 
					: Situation.Polarity.NEGATIVE_POLARITY);
			}
			
			SituationMention situationMention = situationMentionBuilder.build();
			
			
			eventIdMap.put(textEvent.id(), situationId);
			
			situationMentions.add(situationMention);
			
			situations.add(
				Situation.newBuilder()
					.setUuid(situationId)
					.addJustification(
						Situation.Justification.newBuilder()
							.setMention(situationMention.getUuid())
							.setJustificationType(Situation.Justification.Type.DIRECT_MENTION)
							.build()
					)
					.build()
			);
		}
	}
	
	private static void tlinkSituationsFromInfoFile(InfoFile info, String file, List<Situation> situations, HashMap<String, UUID> timexIdMap, HashMap<String, UUID> eventIdMap) {
		/* FIXME: Outstanding issues and questions
		 * - Didn't add any justifications 
		 * - If TLink is not one of the 6 acceptable types, it's just counted as vague.  Should I do something different?
		 * - If first or second event/time invalid, currently just skip over the link
		 */
		
		List<TLink> tlinks = info.getTlinks(file);
		for (TLink tlink : tlinks) {			
			Situation.TemporalFactType temporalFactType = Situation.TemporalFactType.VAGUE_TEMPORAL_FACT;
			if (tlink.relation() == TLink.TYPE.AFTER)
				temporalFactType = Situation.TemporalFactType.AFTER_TEMPORAL_FACT;
			else if (tlink.relation() == TLink.TYPE.BEFORE)
				temporalFactType = Situation.TemporalFactType.BEFORE_TEMPORAL_FACT;
			else if (tlink.relation() == TLink.TYPE.INCLUDES)
				temporalFactType = Situation.TemporalFactType.INCLUDES_TEMPORAL_FACT;
			else if (tlink.relation() == TLink.TYPE.IS_INCLUDED)
				temporalFactType = Situation.TemporalFactType.IS_INCLUDED_BY_TEMPORAL_FACT;
			else if (tlink.relation() == TLink.TYPE.SIMULTANEOUS)
				temporalFactType = Situation.TemporalFactType.SIMULTANEOUS_TEMPORAL_FACT;
			
			Situation.Argument.ValueType firstArgumentType = Situation.Argument.ValueType.UNKNOWN_ARG;
			UUID firstArgumentId = null;
			if (timexIdMap.containsKey(tlink.event1())) {
				firstArgumentType = Situation.Argument.ValueType.ENTITY_ARG;
				firstArgumentId = timexIdMap.get(tlink.event1());
			} else if (eventIdMap.containsKey(tlink.event1())) {
				firstArgumentType = Situation.Argument.ValueType.SITUATION_ARG;			
				firstArgumentId = eventIdMap.get(tlink.event1());
			} else {
				continue;
			}
			
			Situation.Argument.ValueType secondArgumentType = Situation.Argument.ValueType.UNKNOWN_ARG;
			UUID secondArgumentId = null;
			if (eventIdMap.containsKey(tlink.event2())) {
				secondArgumentType = Situation.Argument.ValueType.ENTITY_ARG;
				secondArgumentId = timexIdMap.get(tlink.event2());
			} else if (eventIdMap.containsKey(tlink.event1())) {
				secondArgumentType = Situation.Argument.ValueType.SITUATION_ARG;			
				secondArgumentId = eventIdMap.get(tlink.event2());
			} else {
				continue;
			}
			
			situations.add(
				Situation.newBuilder()
					.setUuid(IdUtil.generateUUID())
					.setSituationType(Situation.Type.TEMPORAL_FACT)
					.addArgument(
						Situation.Argument.newBuilder()
							.setRole(Situation.Argument.Role.RELATION_SOURCE_ROLE)
							.setValueType(firstArgumentType)
							.setValue(firstArgumentId)
							.build()
					)
					.addArgument(
							Situation.Argument.newBuilder()
								.setRole(Situation.Argument.Role.RELATION_TARGET_ROLE)
								.setValueType(secondArgumentType)
								.setValue(secondArgumentId)
								.build()
					)
					.setTemporalFactType(temporalFactType)
					.setConfidence((float)tlink.relationConfidence())
					.build()
			);
		}
	}
}
