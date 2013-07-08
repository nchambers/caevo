package timesieve.sieves;

import org.apache.uima.UIMAException;
import org.cleartk.timeml.tlink.TemporalLinkEventToSubordinatedEventAnnotator;

public class CleartkEventEventSieve extends CleartkTimemlSieve_ImplBase {

	public CleartkEventEventSieve() throws UIMAException {
		super(TemporalLinkEventToSubordinatedEventAnnotator.FACTORY
				.getAnnotatorDescription());
	}

}
