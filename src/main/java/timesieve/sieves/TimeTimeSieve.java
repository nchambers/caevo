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
 * TimeTimeSieve orders date and time expressions by their Timex normalized values
 * 	- Currently does not consider durations or sets
 * 		- Durations require start and end times that we currently don't have
 *  - Only orders FUTURE_REF with times in the past, and PAST_REF with times in the future
 *  - Ignores PRESENT_REF due to inconsistent annotations of "now"
 *  - Currently, all imprecision is due to incorrect annotations
 *  - Also orders all times with document creation time
 * 
 * Chambers note: make sure DCT times are 24-hour periods as that is the annotation standard
 *                This sieve uses what it gets, so change to 24-hours before calling it.
 * 
 * Current results on various data sets:
 * 	- Train: 0.920	 160 of 174
 * 	- Dev:   0.882	 15 of 17
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
		List<List<Timex>> sentenceTimexes = doc.getTimexesBySentence();
		List<TLink> proposed = new ArrayList<TLink>();
		Timex creationTime = (doc.getDocstamp() ==  null || doc.getDocstamp().isEmpty()) ? null : doc.getDocstamp().get(0);
		
		for (int s = 0; s < sentenceTimexes.size(); s++) {
			for (int t1 = 0; t1 < sentenceTimexes.get(s).size(); t1++) {						
				for (int t2 = t1 + 1; t2 < sentenceTimexes.get(s).size(); t2++) {
					TLink link = this.orderTimexes(sentenceTimexes.get(s).get(t1), sentenceTimexes.get(s).get(t2), creationTime);
					if (link != null) 
						proposed.add(link);
				}
				
				if (s + 1 < sentenceTimexes.size()) {
					for (int t2 = 0; t2 < sentenceTimexes.get(s+1).size(); t2++) {
						TLink link = this.orderTimexes(sentenceTimexes.get(s).get(t1), sentenceTimexes.get(s+1).get(t2), creationTime);
						if (link != null) 
							proposed.add(link);
					}
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
		
//		System.out.println("Checking time-time: " + t1 + "\t" + t2);
		
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
		
//		System.out.println("\tintervals: " + interval1 + "\t" + interval2);
		
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

//		System.out.println(t1.getValue() + "\t" + t2.getValue() + "\t" + lType);
		
		return new TimeTimeLink(t1.getTid(), t2.getTid(), lType);
	}
	
	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}
