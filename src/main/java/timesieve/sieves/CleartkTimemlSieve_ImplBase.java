package timesieve.sieves;

import java.util.List;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.jcas.JCas;
import org.cleartk.timeml.type.Anchor;
import org.cleartk.timeml.type.Event;
import org.cleartk.timeml.type.TemporalLink;
import org.cleartk.timeml.type.Time;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.util.JCasUtil;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.EventTimeLink;
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
				Anchor source = cleartkTLink.getSource();
				Anchor target = cleartkTLink.getTarget();
				String sourceId = source.getId();
				String targetId = target.getId();
				String relation = cleartkTLink.getRelationType();
				// create different types of TLinks -- necessary only because Evaluate
				// does not properly handle general TLinks, only the specific subclasses
				TLink tlink;
				if (source instanceof Event && target instanceof Event) {
					tlink = new EventEventLink(sourceId, targetId, relation);
				} else if ((source instanceof Event && target instanceof Time)
						|| (target instanceof Event && source instanceof Time)) {
					tlink = new EventTimeLink(sourceId, targetId, relation);
				} else {
					throw new IllegalArgumentException(String.format(
							"can't create TLink for %s and %s", source.getClass().getName(),
							target.getClass().getName()));
				}
				tlinks.add(tlink);
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
