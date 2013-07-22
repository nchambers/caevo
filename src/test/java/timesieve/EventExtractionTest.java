package timesieve;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

import junit.framework.TestCase;

/**
 * Run the event classifier on some raw text and make sure the expected number of events come out.
 */
public class EventExtractionTest extends TestCase {

	public void testRawToEvents() throws Exception {
		
		String text = "Libya, which brought the case to the United Nations' highest judicial body in its dispute with the United States and Britain, hailed the ruling and said it would press anew for a trial in a third neutral country. Britain will complain because they always complain.";

		String args[] = { "raw" };
		TextEventClassifier classifier = new TextEventClassifier(args);
		classifier.loadClassifiers();

		// Create a temporary file.
		String tempfile = "testing-events.txt";
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(tempfile)));
		writer.write(text);
		writer.close();
		
    SieveDocument doc = classifier.markupRawTextToSieveDocument(tempfile);
    assertNotNull(doc);
    // Number of expected sentences.
    assertEquals("Number of sentences.", doc.getSentences().size(), 2);
    // Number of events per sentence.
    assertEquals("Number of events in 1st sentence.", doc.getSentences().get(0).events().size(), 7);
    assertEquals("Number of events in 2nd sentence.", doc.getSentences().get(1).events().size(), 2);
    // Some tense/aspect attributes.
    assertEquals("Event Tense check", doc.getSentences().get(0).events().get(0).getTense(), TextEvent.Tense.PAST);
    assertEquals("Event Class check", doc.getSentences().get(0).events().get(0).getTheClass(), TextEvent.Class.OCCURRENCE);
    assertEquals("Event Aspect check", doc.getSentences().get(0).events().get(0).getAspect(), TextEvent.Aspect.NONE);
	}

}
