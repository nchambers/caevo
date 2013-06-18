package timesieve.util;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import timesieve.*;
//import timesieve.Sentence;
import timesieve.tlink.TLink;
import edu.jhu.hlt.concrete.Concrete.*;
import edu.stanford.nlp.ling.Sentence;
/**
 * Methods to help with using Concrete (https://github.com/hltcoe/concrete) data sources
 * @author Bill McDowell
 */
public class ConcreteUtil {
	private static final int NONE_ID = -1;
	
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
		/* FIXME */List<Sentence> sentences = ConcreteUtil.sentencesFromCommunication(comm);
		HashMap<Integer, List<Timex>> timexes = ConcreteUtil.timexesFromCommunication(comm);
		List<TextEvent> textEvents = ConcreteUtil.textEventsFromCommunication(comm);
		List<TLink> tlinks = ConcreteUtil.tlinksFromCommunication(comm);

		if (file == null)
			throw new IllegalArgumentException();
		
		/* Transfer Sentences */
		/* Transfer Timexes */
		for (Entry<Integer, List<Timex>> eTimex : timexes.entrySet()) {
			if (eTimex.getKey() >= 0)
				info.addTimexes(file, eTimex.getKey(), eTimex.getValue());
			else
				info.addTimexes(file, eTimex.getValue());
		}
		
		/* Transfer TextEvents */
		/* Transfer TLinks */
		
		// Sentences: info.addSentence(file, sid, text, parse, deps, events, timexes)
		// Timexes: info.addTimexes(file, timexes) and info.addTimexes(docname, sid, timexes)
		// Document creation time: info.addCreationTime(file, timex)
		// Events: info.addEvents(docname, sid, events)
		// TLinks: info.addTlinks(file, tlinks)
		
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
		 *  - What to use for TID?  Need to add to Concrete?
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
					
					if (entity.hasCanonicalName())
						time.setValue(entity.getCanonicalName());
				
					
					//time.setType(v) /* FIXME */
					//time.setDocFunction(func); /* FIXME */
					//time.setPrep(prep); /* FIXME */
					
					
					
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
		
		  /*public void setText(String text) { this.text = text; }
		  public void setSID(int i) { sid = i; }
		  public void setTID(String id) { tid = id; }

		  public void setSpan(int s, int e) {
		    offset = s;
		    length = e - s;
		  }

		  public void setType(String v) { type = v; }
		  public void setValue(String v) { value = v; }
		  public void setPrep(String prep) { preposition = prep; }
		  public void setDocFunction(String func) { docFunction = func; }*/
	}
	
	public static List<TextEvent> textEventsFromCommunication(Communication comm) {
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
		return null;
	}
	
	public static List<Sentence> sentencesFromCommunication(Communication comm) {
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
	
	public static Communication infoFileToCommunication(InfoFile info) {
		return null;
	}
}
