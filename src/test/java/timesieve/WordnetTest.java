package timesieve;

import timesieve.util.WordNet;
import junit.framework.TestCase;

/**
 * A few quick WordNet operations to make sure it is loaded properly.
 */
public class WordnetTest extends TestCase {

	public void testWordnet() {
		WordNet wordnet = new WordNet();
		
		testLemmas(wordnet);
		testSynsets(wordnet);		
	}
	
	private void testLemmas(WordNet wordnet) {
		assertEquals(wordnet.lemmatizeTaggedWord("running", "VBG"), "run");
		assertEquals(wordnet.lemmatizeTaggedWord("were", "VBG"), "be");
		assertEquals(wordnet.lemmatizeTaggedWord("men", "NNS"), "man");
	}
	
	private void testSynsets(WordNet wordnet) {
		assertEquals(wordnet.isPhysicalObject("house"), true);
		assertEquals(wordnet.isNounPersonOrGroup("soldier"), true);
		assertEquals(wordnet.isNounPersonOrGroup("knife"), false);
	}

}
