package timesieve;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;

public class SieveDocumentsAnalyzer {
	private SieveDocuments docs;
	
	public SieveDocumentsAnalyzer(SieveDocuments docs) {
		this.docs = docs;
	}
	
	public HashMap<String, HashMap<String, HashMap<TLink.Type, Integer>>> getEventEventLinkCounts(boolean distinguishClass, 
																																															 boolean distinguishTense, 
																																															 boolean distinguishAspect, 
																																															 boolean distinguishSentence) {
		
		HashMap<String, HashMap<String, HashMap<TLink.Type, Integer>>> linkCounts = new HashMap<String, HashMap<String, HashMap<TLink.Type, Integer>>>();
		
		for (SieveDocument doc : this.docs.getDocuments()) {
			List<TLink> links = doc.getTlinksOfType(EventEventLink.class);
			for (TLink link : links) {
				TextEvent e1 = doc.getEventByEiid(link.getId1());
				TextEvent e2 = doc.getEventByEiid(link.getId2());
				
				if (e1 == null || e2 == null)
					continue;
				
				StringBuilder firstEventStr = new StringBuilder();
				StringBuilder secondEventStr = new StringBuilder();
				
				if (distinguishClass) {
					firstEventStr.append(e1.getTheClass()).append("\t");
					secondEventStr.append(e2.getTheClass()).append("\t");
				}
				
				if (distinguishTense) {
					firstEventStr.append(e1.getTense()).append("\t");
					secondEventStr.append(e2.getTense()).append("\t");					
				}
				
				if (distinguishAspect) {
					firstEventStr.append(e1.getAspect()).append("\t");
					secondEventStr.append(e2.getAspect()).append("\t");				
				}
				
				if (distinguishSentence) {
					if (e1.getSid() == e2.getSid()) {
						firstEventStr.append("SAME_SENTENCE").append("\t");
					} else {
						firstEventStr.append("DIFFERENT_SENTENCE").append("\t");
					}
				}
				
				if (!linkCounts.containsKey(firstEventStr.toString()))
					linkCounts.put(firstEventStr.toString(), new HashMap<String, HashMap<TLink.Type, Integer>>());
				if (!linkCounts.get(firstEventStr.toString()).containsKey(secondEventStr.toString()))
					linkCounts.get(firstEventStr.toString()).put(secondEventStr.toString(), new HashMap<TLink.Type, Integer>());
				if (!linkCounts.get(firstEventStr.toString()).get(secondEventStr.toString()).containsKey(link.getRelation()))
					linkCounts.get(firstEventStr.toString()).get(secondEventStr.toString()).put(link.getRelation(), 0);
				
				linkCounts.get(firstEventStr.toString()).get(secondEventStr.toString()).put(link.getRelation(),
						linkCounts.get(firstEventStr.toString()).get(secondEventStr.toString()).get(link.getRelation()) + 1);			
			}
		}
		
		return linkCounts;
	}
	
	
	public String getLinkCountsString(boolean distinguishClass, 
			 boolean distinguishTense, 
			 boolean distinguishAspect, 
			 boolean distinguishSentence) {
		StringBuilder analysis = new StringBuilder();
		HashMap<String, HashMap<String, HashMap<TLink.Type, Integer>>> linkCounts = 
				getEventEventLinkCounts(distinguishClass,
																distinguishTense,
																distinguishAspect,
																distinguishSentence);
		TLink.Type[] linkTypes = {TLink.Type.VAGUE, TLink.Type.BEFORE, TLink.Type.AFTER, TLink.Type.INCLUDES, TLink.Type.IS_INCLUDED, TLink.Type.SIMULTANEOUS};
		
		if (distinguishClass) analysis.append("Class 1").append("\t");
		if (distinguishTense) analysis.append("Tense 1").append("\t");
		if (distinguishAspect) analysis.append("Aspect 1").append("\t");
		if (distinguishSentence) analysis.append("Sentence").append("\t");
		if (distinguishClass) analysis.append("Class 2").append("\t");
		if (distinguishTense) analysis.append("Tense 2").append("\t");
		if (distinguishAspect) analysis.append("Aspect 2").append("\t");
		
		for (int i =0; i < linkTypes.length; i++)
			analysis.append(linkTypes[i]).append("\t");
		
		analysis.append("Count\n");
		
		for (Entry<String, HashMap<String, HashMap<TLink.Type, Integer>>> e1 : linkCounts.entrySet()) {
			for (Entry<String, HashMap<TLink.Type, Integer>> e2 : e1.getValue().entrySet()) {
				analysis.append(e1.getKey()).append(e2.getKey());
				double totalCount = 0;
				for (int i = 0; i < linkTypes.length; i++)
					if (e2.getValue().containsKey(linkTypes[i]))
						totalCount += e2.getValue().get(linkTypes[i]);
				
				for (int i = 0; i < linkTypes.length; i++) {
					if (e2.getValue().containsKey(linkTypes[i])) {
						analysis.append(e2.getValue().get(linkTypes[i])/totalCount).append("\t");
					} else {
						analysis.append("0").append("\t");
					}
				}
				analysis.append(totalCount).append("\n");
			}
		}
				
		return analysis.toString();
	}
	
	public static void main(String[] args) {
		if (args.length < 2)
			return;

		String inputFile = args[0];
		String outputFile = args[1];
		
		SieveDocumentsAnalyzer analyzer = new SieveDocumentsAnalyzer(new SieveDocuments(inputFile));
		
		try {
			File file = new File(outputFile);
			if (!file.exists()) {
				file.createNewFile();
			}
 
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(analyzer.getLinkCountsString(false, true, true, true));
			bw.close();
 
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
