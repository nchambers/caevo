package timesieve;

import java.util.ArrayList;
import java.util.List;

import timesieve.tlink.TLink;

/**
 * Evaluation functions for TLink classification.
 *
 * @author chambers
 */
public class Evaluate {

	public static final String[] devDocs = { 
		"APW19980227.0487.tml",
		"CNN19980223.1130.0960.tml",
		"NYT19980212.0019.tml", 
		"PRI19980216.2000.0170.tml", 
		"ed980111.1130.0089.tml" 
		};
	
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
	
	public static SieveDocuments getTrainSet(SieveDocuments docs) {
		SieveDocuments newdocs = new SieveDocuments();
		for( SieveDocument doc : docs.getDocuments() )
			if( !exists(doc.getDocname(), devDocs) )
				newdocs.addDocument(doc);
		return newdocs;
	}

	public static SieveDocuments getDevSet(SieveDocuments docs) {
		SieveDocuments newdocs = new SieveDocuments();
		for( SieveDocument doc : docs.getDocuments() )
			if( exists(doc.getDocname(), devDocs) )
				newdocs.addDocument(doc);
		return newdocs;		
	}

	private static boolean exists(String name, String[] names) {
		for( String nn : names )
			if( name.equals(nn) ) return true;
		return false;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
	}

}
