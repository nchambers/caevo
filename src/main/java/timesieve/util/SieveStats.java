package timesieve.util;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import timesieve.Evaluate;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.EventTimeLink;
import timesieve.tlink.TLink;
import timesieve.tlink.TimeTimeLink;

/**
 * Class to hold all links that are guessed by a specific sieve, as well as
 * compute its statistics for printing/saving.
 * 
 * @author chambers
 *
 */
public class SieveStats {
	String sieveName = "";
	List<TLink> correctLinks = new ArrayList<TLink>();
	List<TLink> incorrectLinks = new ArrayList<TLink>();
	List<TLink> lonelyLinks = new ArrayList<TLink>();
	Counter<String> guessCounts = new ClassicCounter<String>();
	int numProposed = 0, numRemoved = 0, numFromClosure = 0;
	
	String statsOutputDir = "sievestats";
	
	
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

	public void printOneLineStats() {
		System.out.print(sieveName + "\t");
		if( sieveName.length() < 16 ) System.out.print("\t");
		if( sieveName.length() < 8 ) System.out.print("\t");
		// Overall precision.
		double totalGuessed = correctLinks.size() + incorrectLinks.size();
		double precision = correctLinks.size() / totalGuessed;
		System.out.printf("p = %.2f\t(%d of %d)\n", precision, (int)correctLinks.size(), (int)totalGuessed);
	}
	
	public void printStats() {
		printStats(System.out);
	}
	
	public void printStats(PrintStream printer) {
		printer.println("=======================================");
		printer.println("-------- " + sieveName + " --------");

		// Basic stats.
		printer.println("Total # of links proposed:\t" + numProposed);
		printer.println("Links ignored:\t" + numRemoved);
		printer.println("Links produced from closure:\t" + numFromClosure);
		printer.println("Links not in gold:\t" + lonelyLinks.size());
		
		// Overall precision.
		double totalGuessed = correctLinks.size() + incorrectLinks.size();
		double precision = correctLinks.size() / totalGuessed;
		printer.printf("PRECISION (overall):\t%.2f\t(%d of %d)\n", precision, (int)correctLinks.size(), (int)totalGuessed);

		// Raw vs Closure precision.
		double closedCorrect = numCorrectFromClosure();
		double unclosedCorrect = correctLinks.size() - closedCorrect;
		double totalClosed   = totalFromClosure();
		double totalUnclosed = totalGuessed - totalClosed;
		double closedP = (totalClosed == 0.0 ? 0.0 : closedCorrect / totalClosed);
		double unclosedP = (totalUnclosed == 0.0 ? 0.0 : unclosedCorrect / totalUnclosed);
		printer.printf("\tnon-closed:\t%.2f\t(%d of %d)\n", unclosedP, (int)unclosedCorrect, (int)totalUnclosed);
		printer.printf("\tclosed:\t\t%.2f\t(%d of %d)\n", closedP, (int)closedCorrect, (int)totalClosed);
		
		// Precision by tlink type.
		Counter<String> correctTyped = correctByLinkType();
		Counter<String> totalTyped = totalGuessedByLinkType();
		for( String type : totalTyped.keySet() ) {
			double tcorrect = correctTyped.getCount(type);
			double ttotal = totalTyped.getCount(type);
			double tprecision = (ttotal == 0.0 ? 0.0 : tcorrect / ttotal);
			printer.printf("PRECISION %s:\t%.2f\t(%d of %d)\n", type, tprecision, (int)tcorrect, (int)ttotal);
		}
		
		Evaluate.printConfusionMatrix(guessCounts, printer);
		printer.flush();
	}
	
	/**
	 * Create a file with the statistics for this sieve including all guessed links.
	 */
	public void dumpStatsToFile() {
		try {
			// Create the directory to store the .stats file, if it doesn't yet exist.
			if( !(new File(statsOutputDir)).exists() )
				new File(statsOutputDir).mkdir();
			
			PrintStream writer = new PrintStream(new File(statsOutputDir + File.separator + this.sieveName + ".stats"));

      // Print the summary statistics.
      printStats(writer);
      
      // Print the individual TLinks that were guessed.
      writer.print("CORRECT LINKS (" + correctLinks.size() + ")\n");
      for( TLink link : correctLinks ) {
      	String eiid1 = link.getId1();
      	String eiid2 = link.getId2();
//      	writer.print(link.getDocument().getDocname() + "\t" + link + "\n");
      	writer.print(link.getDocument().getDocname() + "\t");
      	if( eiid1.startsWith("t") ) writer.print(eiid1);
      	else writer.print(link.getDocument().getEventByEiid(link.getId1()));
      	writer.print("\t");
      	if( eiid2.startsWith("t") ) writer.print(eiid2);
      	else writer.print(link.getDocument().getEventByEiid(link.getId2()) + "\t=" + link.getRelation());
      	writer.print("\n");
      }
      
      
      writer.print("INCORRECT LINKS (" + incorrectLinks.size() + ")\n");
      for( TLink link : incorrectLinks ) 
      	writer.print(link.getDocument().getDocname() + "\t" + link.getDocument().getEventByEiid(link.getId1()) + "\t" +
      			link.getDocument().getEventByEiid(link.getId2()) + "\t=" + link.getRelation() + "\n");
//      	writer.print(link.getDocument().getDocname() + "\t" + link + "\t (orig=" + link.getOriginalRelation() + ")\n");
      
      writer.flush();
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
	}
}
