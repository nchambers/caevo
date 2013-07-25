package timesieve.util;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import timesieve.Evaluate;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.EventTimeLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;

public class SieveStats {
	String sieveName = "";
	List<TLink> correctLinks = new ArrayList<TLink>();
	List<TLink> incorrectLinks = new ArrayList<TLink>();
	List<TLink> lonelyLinks = new ArrayList<TLink>();
	Counter<String> guessCounts = new ClassicCounter<String>();
	int numProposed = 0, numRemoved = 0, numFromClosure = 0;
	
	public SieveStats() { }
	
	public SieveStats(String name) { 
		sieveName = name; 
	}
	
	public void setName(String name) { sieveName = name; }
	public String getName() { return sieveName; }
	
	public void addProposedCount(int num) { numProposed += num; }
	public void addRemovedCount(int num) { numRemoved += num; }
	public void addClosureCount(int num) { numFromClosure += num; }
	
	public void addCorrect(TLink link) {
		correctLinks.add(link);
		guessCounts.incrementCount(link.getRelation()+" "+link.getRelation());
	}
	
	public void addIncorrect(TLink link, TLink goldLink) {
		incorrectLinks.add(link);
		guessCounts.incrementCount(goldLink.getRelation()+" "+link.getRelation());
	}

	public void addNoGold(TLink link) {
		lonelyLinks.add(link);
	}

	private Counter<String> correctByLinkType() {
		Counter<String> counts = new ClassicCounter<String>();
		for( TLink link : correctLinks ) {
			if( link instanceof EventEventLink )
				counts.incrementCount("EventEvent");
			else if( link instanceof EventTimeLink ) {
				if( link.getId1().equals("t0") || link.getId2().equals("t0") )
					counts.incrementCount("EventDCT");
				else
					counts.incrementCount("EventTime");
			}
			else if( link instanceof TimeTimeLink )
				counts.incrementCount("TimeTime");
			else
				counts.incrementCount("Other");
		}
		return counts;
	}

	private Counter<String> totalGuessedByLinkType() {
		Counter<String> counts = new ClassicCounter<String>();
		List<TLink> all = new ArrayList<TLink>();
		all.addAll(incorrectLinks);
		all.addAll(correctLinks);
		for( TLink link : all ) {
			if( link instanceof EventEventLink )
				counts.incrementCount("EventEvent");
			else if( link instanceof EventTimeLink ) {
				if( link.getId1().equals("t0") || link.getId2().equals("t0") )
					counts.incrementCount("EventDCT");
				else
					counts.incrementCount("EventTime");
			}
			else if( link instanceof TimeTimeLink )
				counts.incrementCount("TimeTime");
			else
				counts.incrementCount("Other");
		}
		return counts;
	}

	private int numCorrectFromClosure() {
		int correct = 0;
		for( TLink link : correctLinks )
			if( link.isFromClosure() )
				correct++;
		return correct;
	}

	private int totalFromClosure() {
		int count = 0;
		List<TLink> all = new ArrayList<TLink>();
		all.addAll(incorrectLinks);
		all.addAll(correctLinks);
		for( TLink link : all )
			if( link.isFromClosure() ) {
				count++;
//				System.out.println("STATS: from closure: " + link);
			}
		return count;
	}

	public void printStats() {
		System.out.println("=======================================");
		System.out.println("-------- " + sieveName + " --------");

		// Basic stats.
		System.out.println("Total # of links proposed:\t" + numProposed);
		System.out.println("Links ignored:\t" + numRemoved);
		System.out.println("Links produced from closure:\t" + numFromClosure);
		System.out.println("Links not in gold:\t" + lonelyLinks.size());
		
//		System.out.println("Correct links: " + correctLinks);
//		System.out.println("Incorrect links: " + incorrectLinks);
			
		// Overall precision.
		double totalGuessed = correctLinks.size() + incorrectLinks.size();
		double precision = correctLinks.size() / totalGuessed;
		System.out.printf("PRECISION (overall):\t%.2f\t(%d of %d)\n", precision, (int)correctLinks.size(), (int)totalGuessed);

		// Raw vs Closure precision.
		double closedCorrect = numCorrectFromClosure();
		double unclosedCorrect = correctLinks.size() - closedCorrect;
		double totalClosed   = totalFromClosure();
		double totalUnclosed = totalGuessed - totalClosed;
		double closedP = (totalClosed == 0.0 ? 0.0 : closedCorrect / totalClosed);
		double unclosedP = (totalUnclosed == 0.0 ? 0.0 : unclosedCorrect / totalUnclosed);
		System.out.printf("\tnon-closed:\t%.2f\t(%d of %d)\n", unclosedP, (int)unclosedCorrect, (int)totalUnclosed);
		System.out.printf("\tclosed:\t\t%.2f\t(%d of %d)\n", closedP, (int)closedCorrect, (int)totalClosed);
		
		// Precision by tlink type.
		Counter<String> correctTyped = correctByLinkType();
		Counter<String> totalTyped = totalGuessedByLinkType();
		for( String type : totalTyped.keySet() ) {
			double tcorrect = correctTyped.getCount(type);
			double ttotal = totalTyped.getCount(type);
			double tprecision = (ttotal == 0.0 ? 0.0 : tcorrect / ttotal);
			System.out.printf("PRECISION %s:\t%.2f\t(%d of %d)\n", type, tprecision, (int)tcorrect, (int)ttotal);
		}
		
		Evaluate.confusionMatrix(guessCounts);
	}
}
