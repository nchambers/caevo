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
 *  - Only orders FUTURE_REF with times in the past, and PAST_REF with times in the future
 *  - Ignores PRESENT_REF due to inconsistent annotations of "now"
 *  - Current precision: .86 (63 of 73)
 *  	- All imprecision is due to incorrect annotations
 * 
 * @author Bill McDowell
 */
public class TimeTimeSieve implements Sieve {

	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<TLink> proposed = new ArrayList<TLink>();
		
		List<TLink> sentencePairLinks = annotateBySentencePair(doc);
		List<TLink> creationTimeLinks = annotateByCreationTime(doc);
		
		if (sentencePairLinks != null)
			proposed.addAll(sentencePairLinks);
		if (creationTimeLinks != null)
			proposed.addAll(creationTimeLinks);
		
		return proposed;
	}
	
	public List<TLink> annotateBySentencePair(SieveDocument doc) {
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
	
	public List<TLink> annotateByCreationTime(SieveDocument doc) {
		if (doc.getDocstamp() == null || doc.getDocstamp().isEmpty())
			return null;
		
		Timex creationTime = doc.getDocstamp().get(0);
		
		List<List<Timex>> allTimexes = doc.getTimexesBySentence();
		List<TLink> proposed = new ArrayList<TLink>();
		
		for (List<Timex> timexes : allTimexes) {
			for (Timex timex : timexes) {
				TLink link = this.orderTimexes(creationTime, timex, creationTime);
				if (link != null) 
					proposed.add(link);
			}
		}
		
		return proposed;
	}

	private TLink orderTimexes(Timex t1, Timex t2, Timex ct) {	
		if (t1 == null || t2 == null)
			return null;
		if (t1.getType() != Timex.Type.DATE && t1.getType() != Timex.Type.TIME)
			return null;
		if (t2.getType() != Timex.Type.DATE && t2.getType() != Timex.Type.TIME)
			return null;
		
		if (t1.isReference() || t2.isReference()) {
			if (ct == null)
				return null;
			
			int t1Ct = 0, t2Ct = 0;
			
			if (t1.isReference()) {
				if (t1.isFutureReference())
					t1Ct = 1;
				else if (t1.isPastReference())
					t1Ct = -1;
				else
					return null;//t1Ct = 0;
			} else {
				TLink t1CtLink = orderTimexes(t1, ct, null);
				if (t1CtLink == null || t1CtLink.getRelation() == TLink.Type.VAGUE)
					return null;
				else if (t1CtLink.getRelation() == TLink.Type.BEFORE)
					t1Ct = -1;
				else if (t1CtLink.getRelation() == TLink.Type.AFTER)
					t1Ct = 1;
				else
					return null;//t1Ct = 0;
			}
			
			if (t2.isReference()) {
				if (t2.isFutureReference())
					t2Ct = 1;
				else if (t2.isPastReference())
					t2Ct = -1;
				else
					return null;//t2Ct = 0;
			} else {
				TLink t2CtLink = orderTimexes(t2, ct, null);
				if (t2CtLink == null || t2CtLink.getRelation() == TLink.Type.VAGUE)
					return null;
				else if (t2CtLink.getRelation() == TLink.Type.BEFORE)
					t2Ct = -1;
				else if (t2CtLink.getRelation() == TLink.Type.AFTER)
					t2Ct = 1;
				else
					return null;//t2Ct = 0;
			}
			
			if (t1Ct < t2Ct)
				return new TimeTimeLink(t1.getTid(), t2.getTid(), TLink.Type.BEFORE);
			else if (t2Ct < t1Ct)
				return new TimeTimeLink(t1.getTid(), t2.getTid(), TLink.Type.AFTER);
			else
				return null;
		}
		
		
		Pair<Calendar, Calendar> interval1 = null;
		Pair<Calendar, Calendar> interval2 = null;
		
		interval1 = t1.getRange(ct);
		interval2 = t2.getRange(ct);
		
		if (interval1 == null || interval2 == null)
			return null;
		
		int startStart = interval1.first().compareTo(interval2.first());
		int startEnd = interval1.first().compareTo(interval2.second());
		int endStart = interval1.second().compareTo(interval2.first());
		int endEnd = interval1.second().compareTo(interval2.second());
		
		//System.out.println(startStart + " " + t1.getTid() + " " + t2.getTid() + " "+ interval1.first().getTime().toString() + " " + interval1.first().getTimeInMillis() + " " + interval2.first().getTime().toString() + interval2.first().getTimeInMillis());
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
