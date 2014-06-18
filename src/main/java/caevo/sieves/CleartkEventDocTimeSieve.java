package caevo.sieves;

import java.io.IOException;

import org.apache.uima.UIMAException;
import org.cleartk.timeml.tlink.TemporalLinkEventToDocumentCreationTimeAnnotator;

public class CleartkEventDocTimeSieve extends CleartkTimemlSieve_ImplBase {

	public CleartkEventDocTimeSieve() throws UIMAException, IOException {
		super(getAnnotatorDescription(CleartkEventDocTimeSieve.class,
				TemporalLinkEventToDocumentCreationTimeAnnotator.FACTORY));
	}
}
