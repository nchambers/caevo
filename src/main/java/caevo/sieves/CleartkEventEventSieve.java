package caevo.sieves;

import java.io.IOException;

import org.apache.uima.UIMAException;
import org.cleartk.timeml.tlink.TemporalLinkEventToSubordinatedEventAnnotator;

public class CleartkEventEventSieve extends CleartkTimemlSieve_ImplBase {

	public CleartkEventEventSieve() throws UIMAException, IOException {
		super(getAnnotatorDescription(CleartkEventEventSieve.class,
				TemporalLinkEventToSubordinatedEventAnnotator.FACTORY));
	}

}
