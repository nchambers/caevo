package timesieve;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

import junit.framework.TestCase;

public class TimexExtractionTest extends TestCase {
	
	public void testRawToTimex() {
		
		String text = "Libya brought the case in 2003 to Britain because of 11/25/1980 and complained.";
		
		Main main = new Main();

		// Create a temporary file.
		String tempfile = "testing-timex.txt";
		try {
			PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(tempfile)));
			writer.write(text);
			writer.close();
		} catch( Exception ex ) { 
			ex.printStackTrace();
			assert(false);
		}

		// Run the full markup pipeline.
		SieveDocuments docs = main.markupRawTextFile(tempfile);

		assertNotNull(docs);
		
    // Number of expected sentences.
		assertEquals("Number of generated documents.", docs.getDocuments().size(), 1);

    SieveDocument doc = docs.getDocuments().get(0);
    assertEquals("Number of sentences.", doc.getSentences().size(), 1);
    
    SieveSentence sent = doc.getSentences().get(0);
    assertEquals("Number of created timexes.", sent.timexes().size(), 2);
	}

}
