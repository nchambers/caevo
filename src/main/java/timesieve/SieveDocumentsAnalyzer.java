package timesieve;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;

/**
 * For running various analyses on corpus to get ideas for Sieves
 * 
 * @author Bill McDowell
 */
public class SieveDocumentsAnalyzer {
	private SieveDocuments docs;
	
	public SieveDocumentsAnalyzer(SieveDocuments docs) {
		this.docs = docs;
	}
	
	public HashMap<TextEventPairPattern, HashMap<TLink.Type, Integer>> getEventEventLinkCounts(boolean distinguishClass, 
																																														 boolean distinguishTense, 
																																														 boolean distinguishAspect, 
																																														 boolean distinguishSentence) {
		
		HashMap<TextEventPairPattern, HashMap<TLink.Type, Integer>> linkCounts = new HashMap<TextEventPairPattern, HashMap<TLink.Type, Integer>>();
		
		for (SieveDocument doc : this.docs.getDocuments()) {
			List<TLink> links = doc.getTlinksOfType(EventEventLink.class);
			for (TLink link : links) {
				TextEvent e1 = doc.getEventByEiid(link.getId1());
				TextEvent e2 = doc.getEventByEiid(link.getId2());
				
				if (e1 == null || e2 == null) {
					System.err.println("WARNING: TLink points at " + doc.getDocname() + " missing events " + link.getId1() + " or " + link.getId2() + ".");
					continue;
				}
					
				TextEventPairPattern pairPattern = new TextEventPairPattern(e1, e2, distinguishClass, distinguishTense, distinguishAspect, distinguishSentence);
				if (!linkCounts.containsKey(pairPattern))
					linkCounts.put(pairPattern, new HashMap<TLink.Type, Integer>());
				if (!linkCounts.get(pairPattern).containsKey(link.getRelation()))
					linkCounts.get(pairPattern).put(link.getRelation(), 0);
				
				linkCounts.get(pairPattern).put(link.getRelation(),
						linkCounts.get(pairPattern).get(link.getRelation()) + 1);			
			}
		}
		
		return linkCounts;
	}
	
	public String getLinkDistributionsString(boolean distinguishClass, 
																					 boolean distinguishTense, 
																					 boolean distinguishAspect, 
																					 boolean distinguishSentence) {
		StringBuilder analysis = new StringBuilder();
		HashMap<TextEventPairPattern, HashMap<TLink.Type, Integer>> linkCounts = 
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
		
		for (Entry<TextEventPairPattern, HashMap<TLink.Type, Integer>> e : linkCounts.entrySet()) {
				analysis.append(e.getKey().toString()).append("\t");
				double totalCount = 0;
				for (int i = 0; i < linkTypes.length; i++)
					if (e.getValue().containsKey(linkTypes[i]))
						totalCount += e.getValue().get(linkTypes[i]);
				
				for (int i = 0; i < linkTypes.length; i++) {
					if (e.getValue().containsKey(linkTypes[i])) {
						analysis.append(e.getValue().get(linkTypes[i])/totalCount).append("\t");
					} else {
						analysis.append("0").append("\t");
					}
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
			bw.write(analyzer.getLinkDistributionsString(true, true, true, true));
			bw.close();
 
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
