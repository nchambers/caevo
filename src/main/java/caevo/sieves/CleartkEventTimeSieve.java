package caevo.sieves;

import java.io.IOException;

import org.apache.uima.UIMAException;
import org.cleartk.timeml.tlink.TemporalLinkEventToSameSentenceTimeAnnotator;

public class CleartkEventTimeSieve extends CleartkTimemlSieve_ImplBase {

	public CleartkEventTimeSieve() throws UIMAException, IOException {
		super(getAnnotatorDescription(CleartkEventTimeSieve.class,
				TemporalLinkEventToSameSentenceTimeAnnotator.FACTORY));
	}
}
