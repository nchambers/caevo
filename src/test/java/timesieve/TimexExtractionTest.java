package timesieve;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

import junit.framework.TestCase;

public class TimexExtractionTest extends TestCase {
	
	public void testRawToTimex() throws Exception {
		
		String text = "Libya brought the case in 2003 to Britain because of 11/25/1980 and complained.";
		
		Main main = new Main();

		// Create a temporary file.
		String tempfile = "testing-timex.txt";
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(tempfile)));
		writer.write(text);
		writer.close();

		// Run the full markup pipeline.
		SieveDocuments docs = main.markupRawTextFile(tempfile);

		assertNotNull(docs);
		
    // Number of expected sentences.
		assertEquals("Number of generated documents.", 1, docs.getDocuments().size());

    SieveDocument doc = docs.getDocuments().get(0);
    assertEquals("Number of sentences.", 1, doc.getSentences().size());
    
    SieveSentence sent = doc.getSentences().get(0);
    assertEquals("Number of created timexes.", 2, sent.timexes().size());
	}

}
