package caevo.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Calendar;

import caevo.SieveDocument;
import caevo.Timex;
import caevo.Timex.DocumentFunction;

/**
 * Various heuristics for setting the DCT. 
 * TODO: implement this feature with sieve architecture.
 * 
 * 
 * @author tcassidy
 *
 */

public class DCTHeursitics {
	

	/**
	 * NOT YET TESTED
	 * @param doc
	 */
	public static void setTodaysDateAsDCT(SieveDocument doc) {
		Date today = Calendar.getInstance().getTime();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String val = sdf.format(today);
		Timex dct = new Timex(val);
		dct.setDocumentFunction(DocumentFunction.CREATION_TIME);
		doc.addCreationTime(dct);
	}
	
	/**
	 * This method returns the most common day in the document.
	 * If there is a tie, the day from the set of most common days is chosen. 
	 * If none are specified, today's date is returned.
	 * @param doc
	 * @return
	 */
	public static void setMostCommonDateAsDCT(SieveDocument doc) {
			List<Timex> timexes = doc.getTimexes();
			LinkedHashMap<String, Integer> valToCount = new LinkedHashMap<String, Integer>();  //LinkedHashMap ensures that iteration occurs in order of key insertion
			// count the number of times each day occurs
			for (Timex timex : timexes) {  // note that timexes are in order of appearance in document.
				String val = timex.getValue();
				String valDay = Timex.dateFromValue(val);
				if (valDay == null) continue;
				if (valToCount.containsKey(valDay)) {
					valToCount.put(valDay, valToCount.get(valDay) + 1);
				}
				else {
					valToCount.put(valDay, 1);
				}
			}
			
			// find the day that occurs the most.
			int max = 0; // number of occurrences for the most common day
			ArrayList<String> topDayVals = new ArrayList<String>();
			for (String valKey : valToCount.keySet()) {  // Iteration order is determined by the location of the first timex associated with key in the Timexes ArrayList, which should correspond to document appearance order
				if (valToCount.get(valKey) > max) {
					max = valToCount.get(valKey);  //update max number of occurrences for the value
					topDayVals.add(valKey);
				}
				else if (valToCount.get(valKey) == max) 
					topDayVals.add(valKey);
			}
			
			if (topDayVals.size() > 0){
				Timex dct = new Timex(topDayVals.get(topDayVals.size() - 1));
			  dct.setDocumentFunction(DocumentFunction.CREATION_TIME);
				doc.addCreationTime(dct);
			}
		}
  
  /**
   * @desc Sets the first timex of type DATE in the document timex list as the document creation time
   * TODO: make sure "first" in list is necessarily first that appears in document! Should be, unless
   * the relevant string is somehow dropped during sentence tokenization.
   */
  public static void setFirstDateAsDCT(SieveDocument doc) {
  	if( doc.getDocstamp() == null ) {
  		for (Timex nextTimex : doc.getTimexes()){
  			if (nextTimex.getType().equals(Timex.Type.DATE)) {
  				nextTimex.setDocumentFunction(DocumentFunction.CREATION_TIME);
  	  		doc.addCreationTime(nextTimex);
  				break;
  			}
  		}
  	}
  }

  
}
