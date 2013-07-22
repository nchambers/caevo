package timesieve.sieves;

import org.apache.uima.UIMAException;
import org.cleartk.timeml.tlink.TemporalLinkEventToDocumentCreationTimeAnnotator;

public class CleartkEventDocTimeSieve extends CleartkTimemlSieve_ImplBase {

	public CleartkEventDocTimeSieve() throws UIMAException {
		super(TemporalLinkEventToDocumentCreationTimeAnnotator.FACTORY
				.getAnnotatorDescription());
	}
}
