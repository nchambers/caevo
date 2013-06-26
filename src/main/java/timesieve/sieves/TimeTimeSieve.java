package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import timesieve.InfoFile;
import timesieve.Sentence;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;

/**
 * Order all normalized date and time expressions
 * 
 * @author Bill McDowell
 */
public class TimeTimeSieve implements Sieve {

	public List<TLink> annotate(InfoFile info, String docname, List<TLink> currentTLinks) {
		List<List<Timex>> allTimexes = info.getTimexesBySentence(docname);
		List<TLink> proposed = new ArrayList<TLink>();
		
		for (int i = 0; i < allTimexes.size() - 1; i++) {
			for (int j = 0; j < allTimexes.get(i).size(); j++) {
				if (allTimexes.get(i).get(j).type().equals("DATE") || allTimexes.get(i).get(j).type().equals("TIME"))
					System.out.println(allTimexes.get(i).get(j).text() + "\t" + allTimexes.get(i).get(j).value());
			}
		}
	
		return proposed;
	}

	/**
	 * No training. Just rule-based.
	 */
	public void train(InfoFile trainingInfo) {
		// no training
	}

}
