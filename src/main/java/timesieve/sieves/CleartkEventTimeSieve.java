package timesieve.sieves;

import org.apache.uima.UIMAException;
import org.cleartk.timeml.tlink.TemporalLinkEventToSameSentenceTimeAnnotator;

public class CleartkEventTimeSieve extends CleartkTimemlSieve_ImplBase {

	public CleartkEventTimeSieve() throws UIMAException {
		super(TemporalLinkEventToSameSentenceTimeAnnotator.FACTORY
				.getAnnotatorDescription());
	}

}
