package timesieve.sieves;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.InfoFile;
import timesieve.Timex;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;
import edu.stanford.nlp.util.Pair;

/**
 * Order normalized date and time expressions by their Timex values
 * 
 * @author Bill McDowell
 */
public class TimeTimeSieve implements Sieve {

	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<List<Timex>> allTimexes = this.allTimexesBySentencePair(doc.getTimexesBySentence());
		List<TLink> proposed = new ArrayList<TLink>();
		Timex creationTime = doc.getDocstamp().isEmpty() ? null : doc.getDocstamp().get(0);
		
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
		/*if (!t1.type().equals("DATE") && !t1.type().equals("TIME"))
			return null;
		if (!t2.type().equals("DATE") && !t2.type().equals("TIME"))
			return null;
		*/
		edu.stanford.nlp.time.Timex timex1 = this.convertToStanfordTimex(t1);
		edu.stanford.nlp.time.Timex timex2 = this.convertToStanfordTimex(t2);
		edu.stanford.nlp.time.Timex creationTime = this.convertToStanfordTimex(ct, true);
		
		Pair<Calendar, Calendar> interval1 = null;
		Pair<Calendar, Calendar> interval2 = null;
		
		try {
			interval1 = timex1.getRange(creationTime);
			interval2 = timex2.getRange(creationTime);
		} catch (Exception e) { 
			// System.out.println(t1.text() + "\t" + t2.text() + "\t" + t1.value() + "\t" + t2.value() + "\t" + e.getMessage());
			return null;
		}
		
		if (interval1 == null || interval2 == null)
			return null;
		
		int startStart = interval1.first().compareTo(interval2.first());
		int startEnd = interval1.first().compareTo(interval2.second());
		int endStart = interval1.second().compareTo(interval2.first());
		int endEnd = interval1.second().compareTo(interval2.second());
		
		TLink.TYPE lType = TLink.TYPE.VAGUE;
		
		if (startStart == 0 && endEnd == 0)
			lType = TLink.TYPE.SIMULTANEOUS;
		else if (endStart <= 0)
			lType = TLink.TYPE.BEFORE;
		else if (startEnd >= 0)
			lType = TLink.TYPE.AFTER;
		else if (startStart < 0 && endEnd > 0)
			lType = TLink.TYPE.INCLUDES;
		else if (startStart > 0 && endEnd < 0)
			lType = TLink.TYPE.IS_INCLUDED;
		else if (startStart > 0 && startEnd < 0 && endEnd > 0)
			lType = TLink.TYPE.VAGUE;
		else if (startStart < 0 && endStart > 0 && endEnd < 0)
			lType = TLink.TYPE.VAGUE;
		else
			return null;

		//System.out.println(timex1.value() + "\t" + timex2.value() + "\t" + lType);
		
		return new TimeTimeLink(t1.tid(), t2.tid(), lType);
	}
	
	private edu.stanford.nlp.time.Timex convertToStanfordTimex(Timex timex) {
		return this.convertToStanfordTimex(timex, false);
	}
	
	private edu.stanford.nlp.time.Timex convertToStanfordTimex(Timex timex, boolean noTime) {
		if (timex == null)
			return null;
		
		String value = timex.value();
		String type = timex.type();
		
		if (noTime && timex.type().equals("TIME") && value.contains("T")) {
			value = value.substring(0, value.indexOf("T"));
			type = "DATE";
		}
		
		return new edu.stanford.nlp.time.Timex(type, value);
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
