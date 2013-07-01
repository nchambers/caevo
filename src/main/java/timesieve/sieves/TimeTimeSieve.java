package timesieve.sieves;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.Timex;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;
import timesieve.util.Pair;

/**
 * Order normalized date and time expressions by their Timex values
 * 	- Currently does not consider durations or sets
 * 		- Durations require start and end times that we currently don't have
 * 
 * @author Bill McDowell
 */
public class TimeTimeSieve implements Sieve {

	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<List<Timex>> allTimexes = this.allTimexesBySentencePair(doc.getTimexesBySentence());
		List<TLink> proposed = new ArrayList<TLink>();
		Timex creationTime = (doc.getDocstamp() ==  null || doc.getDocstamp().isEmpty()) ? null : doc.getDocstamp().get(0);
		
		for (List<Timex> closeTimexes : allTimexes) {
			for (int t1 = 0; t1 < closeTimexes.size(); t1++) {				
				for (int t2 = t1 + 1; t2 < closeTimexes.size(); t2++) {
					TLink link = this.orderTimexes(closeTimexes.get(t1), closeTimexes.get(t2), creationTime);
					if (link != null) 
						proposed.add(link);
				}
			}
		}
	
		return proposed;
	}

	private TLink orderTimexes(Timex t1, Timex t2, Timex ct) {				
		if (t1.getType() != Timex.Type.DATE && t1.getType() != Timex.Type.TIME)
			return null;
		if (t2.getType() != Timex.Type.DATE && t2.getType() != Timex.Type.TIME)
			return null;
		
		Pair<Calendar, Calendar> interval1 = null;
		Pair<Calendar, Calendar> interval2 = null;
		
		try {
			interval1 = t1.getRange(ct);
			interval2 = t2.getRange(ct);
		} catch (Exception e) {
			System.out.println("TimeTimeSieve Error: " + e.getMessage());
			return null;
		}
		
		if (interval1 == null || interval2 == null)
			return null;
		
		int startStart = interval1.first().compareTo(interval2.first());
		int startEnd = interval1.first().compareTo(interval2.second());
		int endStart = interval1.second().compareTo(interval2.first());
		int endEnd = interval1.second().compareTo(interval2.second());
		
		TLink.Type lType = TLink.Type.VAGUE;
		
		if (startStart == 0 && endEnd == 0)
			lType = TLink.Type.SIMULTANEOUS;
		else if (endStart <= 0)
			lType = TLink.Type.BEFORE;
		else if (startEnd >= 0)
			lType = TLink.Type.AFTER;
		else if (startStart < 0 && endEnd > 0)
			lType = TLink.Type.INCLUDES;
		else if (startStart > 0 && endEnd < 0)
			lType = TLink.Type.IS_INCLUDED;
		else if (startStart > 0 && startEnd < 0 && endEnd > 0)
			lType = TLink.Type.VAGUE;
		else if (startStart < 0 && endStart > 0 && endEnd < 0)
			lType = TLink.Type.VAGUE;
		else
			return null;

		//System.out.println(timex1.value() + "\t" + timex2.value() + "\t" + lType);
		
		return new TimeTimeLink(t1.getTid(), t2.getTid(), lType);
	}
	
	private List<List<Timex>> allTimexesBySentencePair(List<List<Timex>> allTimexesBySentence) {
		List<List<Timex>> allTimexes = new ArrayList<List<Timex>>();
		
		if (allTimexesBySentence.size() == 1)
			allTimexes.add(allTimexesBySentence.get(0));
		
		for (int i = 0; i < allTimexesBySentence.size() - 1; i++) {
			List<Timex> curTimexes = new ArrayList<Timex>();
			curTimexes.addAll(allTimexesBySentence.get(i));
			curTimexes.addAll(allTimexesBySentence.get(i+1));
			allTimexes.add(curTimexes);
		}
		
		return allTimexes;
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
