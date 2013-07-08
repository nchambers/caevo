package timesieve.sieves;

import java.util.List;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.jcas.JCas;
import org.cleartk.timeml.type.TemporalLink;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.util.JCasUtil;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.tlink.TLink;
import timesieve.util.SieveJCasUtil;

import com.google.common.collect.Lists;

public class CleartkTimemlSieve_ImplBase implements Sieve {

	private AnalysisEngine engine;

	public CleartkTimemlSieve_ImplBase(AnalysisEngineDescription ... descriptions) throws UIMAException {
		AggregateBuilder aggregate = new AggregateBuilder();
		for (AnalysisEngineDescription description : descriptions) {
			aggregate.add(description);
		}
		this.engine = aggregate.createAggregate(); 
	}

	@Override
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		try {
			// run the annotator on the document
			JCas jCas = this.engine.newJCas();
			SieveJCasUtil.fillJCasFromSieveDocument(jCas, doc);
			this.engine.process(jCas);
			
			// create objects for each detected TLink
			List<TLink> tlinks = Lists.newArrayList();
			for (TemporalLink cleartkTLink : JCasUtil
					.select(jCas, TemporalLink.class)) {
				String sourceId = cleartkTLink.getSource().getId();
				String targetId = cleartkTLink.getTarget().getId();
				String relation = cleartkTLink.getRelationType();
				tlinks.add(new TLink(sourceId, targetId, relation));
			}
			return tlinks;
		} catch (UIMAException e) {
			throw new RuntimeException(e);
		}
	}
	@Override
	public void train(SieveDocuments infoDocs) {
		// trained previously on TimeBank+AQUAINT corpus
	}

}
