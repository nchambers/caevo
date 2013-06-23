package timesieve;

import java.util.List;

import timesieve.tlink.TLink;

/**
 * Evaluation functions for TLink classification.
 *
 * @author chambers
 */
public class Evaluate {

	/**
	 * Determines if the given TLink exists in the goldLinks, and its relation is equal
	 * to or compatible (invertible or a more general relation) with the gold link.
	 * @param guessed A single TLink between two events.
	 * @param goldLinks A list of gold tlinks.
	 * @return True if guessed appears as is or inverted in goldLinks.
	 */
	public static boolean isLinkCorrect(TLink guessed, List<TLink> goldLinks) {
		if( guessed == null || goldLinks == null || goldLinks.size() == 0 ) 
			return false;
		
		for( TLink gold : goldLinks ) {
			if( gold.compareToTLink(guessed) ) {
//				System.out.println("Match! guess=" + guessed + "\tgold=" + gold);
				return true;
			}
		}
		
		return false;
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
	}

}
