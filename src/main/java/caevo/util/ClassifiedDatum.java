package caevo.util;

import edu.stanford.nlp.ling.Datum;

public class ClassifiedDatum {
	Datum<String,String> datum;
	String predictedLabel;

	public ClassifiedDatum(Datum<String,String> datum, String predicted) {
		this.datum = datum;
		this.predictedLabel = predicted;
	}
	
	public String label() { return datum.label(); }
	
	public String predictedLabel() { return predictedLabel; }
	
	public boolean isCorrect() {
		return predictedLabel.equals(datum.label());
	}
}
