package caevo.sieves;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.tlink.EventEventLink;
import caevo.tlink.TLink;
import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Pair;

public class MLEventGovDep implements Sieve {

	private static final int FEATURE_COUNT_THRESHOLD = 10;

	private static final String MODEL_PATH_BASE = "/models/tlinks";

	private String modelPath;

	private Classifier<TLink.Type, String> classifier;

	public MLEventGovDep() {
		this.modelPath = String.format("%s/tlink.%s.classifier", MODEL_PATH_BASE,
				this.getClass().getSimpleName());
	}

	@Override
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {

		// if not yet loaded, load the classifier from the classpath
		if (this.classifier == null) {
			URL classifierURL = this.getClass().getResource(this.modelPath);
			if (classifierURL == null) {
				throw new IllegalArgumentException(
						"No classifier found on classpath at " + this.modelPath);
			}
			System.out.printf("Loading %s model from %s\n", this.getClass()
					.getSimpleName(), classifierURL);
			this.classifier = readClassifierFromURL(classifierURL);
		}

		// use the classifier to predict a TLink type for each event pair
		List<TLink> tlinks = new ArrayList<TLink>();
		for (Pair<Pair<TextEvent, TextEvent>, Counter<String>> pair : this
				.getEventPairFeatures(doc)) {
			TLink.Type relation = this.classifier
					.classOf(new RVFDatum<TLink.Type, String>(pair.second));
			tlinks.add(new EventEventLink(pair.first.first.getEiid(),
					pair.first.second.getEiid(), relation));
		}
		return tlinks;
	}

	@Override
	public void train(SieveDocuments infoDocs) {
		RVFDataset<TLink.Type, String> data = new RVFDataset<TLink.Type, String>();
		for (SieveDocument doc : infoDocs.getDocuments()) {

			// map pairs of events to their TLink types
			Map<Pair<TextEvent, TextEvent>, TLink.Type> labels = new HashMap<Pair<TextEvent, TextEvent>, TLink.Type>();
			for (TLink untypedTlink : doc.getTlinks()) {
				if (untypedTlink instanceof EventEventLink) {
					EventEventLink tlink = (EventEventLink) untypedTlink;
					TextEvent event1 = tlink.getEvent1();
					TextEvent event2 = tlink.getEvent2();
					TLink.Type relation = tlink.getRelation();
					labels.put(Pair.makePair(event1, event2), relation);
					labels.put(Pair.makePair(event2, event1),
							TLink.invertRelation(relation));
				}
			}

			// get the event pairs and their features, and add features+labels to the
			// training data
			for (Pair<Pair<TextEvent, TextEvent>, Counter<String>> eventPairFeatures : this
					.getEventPairFeatures(doc)) {
				TLink.Type label = labels.get(eventPairFeatures.first);
				data.add(new RVFDatum<TLink.Type, String>(eventPairFeatures.second,
						label));
			}
		}

		// train the classifier (applying the feature count threshold to the data
		// first)
		System.out.printf("Training %s classifier on %d instances\n", this
				.getClass().getSimpleName(), data.size());
		data.applyFeatureCountThreshold(FEATURE_COUNT_THRESHOLD);
		LinearClassifierFactory<TLink.Type, String> linearFactory = new LinearClassifierFactory<TLink.Type, String>();
		Classifier<TLink.Type, String> classifier = linearFactory
				.trainClassifier(data);

		// write the classifier to the resources directory
		try {
			IOUtils.writeObjectToFile(classifier, "src/main/resources"
					+ this.modelPath);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected List<Pair<Pair<TextEvent, TextEvent>, Counter<String>>> getEventPairFeatures(
			SieveDocument doc) {

		// collect pairs of events and features
		List<Pair<Pair<TextEvent, TextEvent>, Counter<String>>> eventPairFeatures = new ArrayList<Pair<Pair<TextEvent, TextEvent>, Counter<String>>>();
		for (SieveSentence sent : doc.getSentences()) {

			// map indexes to events for later lookup when working with dependencies
			List<TextEvent> events = sent.events();
			Map<Integer, TextEvent> indexToEvent = new HashMap<Integer, TextEvent>();
			for (TextEvent event : events) {
				indexToEvent.put(event.getIndex(), event);
			}

			// Find all event-event pairs with a typed dependency where one is
			// governor of the other.
			List<TypedDependency> deps = sent.getDeps();
			for (TypedDependency dep : deps) {
				TextEvent event1 = indexToEvent.get(dep.gov().index());
				TextEvent event2 = indexToEvent.get(dep.dep().index());
				if (event1 != null && event2 != null) {

					// information from the first event
					String class1 = event1.getTheClass().name();
					String tense1 = event1.getTense().name();
					String aspect1 = event1.getAspect().name();

					// information from the second event
					String class2 = event2.getTheClass().name();
					String tense2 = event2.getTense().name();
					String aspect2 = event2.getAspect().name();

					// extract features for the event pair
					Counter<String> features = new ClassicCounter<String>();
					features.incrementCount(String.format("deprel:%s", dep.reln()));

					// single features for each event
					features.incrementCount(String.format("tense1:%s", tense1));
					features.incrementCount(String.format("tense2:%s", tense2));
					features.incrementCount(String.format("aspect1:%s", aspect1));
					features.incrementCount(String.format("aspect2:%s", aspect2));
					features.incrementCount(String.format("class1:%s", class1));
					features.incrementCount(String.format("class2:%s", class2));

					// combined features for the two events
					features
							.incrementCount(String.format("tenses:%s_%s", tense1, tense2));
					features.incrementCount(String.format("aspects:%s_%s", aspect1,
							aspect2));
					features.incrementCount(String
							.format("classes:%s_%s", class1, class2));

					// pairs of combined features
					features.incrementCount(String.format("tenses_aspects:%s_%s_%s_%s",
							tense1, tense2, aspect1, aspect2));
					features.incrementCount(String.format("tenses_classes:%s_%s_%s_%s",
							tense1, tense2, class1, class2));
					features.incrementCount(String.format("aspects_classes:%s_%s_%s_%s",
							aspect1, aspect2, class1, class2));

					// triples of combined features
					features.incrementCount(String.format(
							"tenses_aspects_classes:%s_%s_%s_%s_%s_%s", tense1, tense2,
							aspect1, aspect2, class1, class2));

					// add the event pair and its features to the list
					eventPairFeatures.add(Pair.makePair(Pair.makePair(event1, event2),
							features));
				}
			}
		}
		return eventPairFeatures;
	}

	@SuppressWarnings("unchecked")
	private static Classifier<TLink.Type, String> readClassifierFromURL(URL url) {
		try {
			return (Classifier<TLink.Type, String>) readObjectFromURL(url);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private static Object readObjectFromURL(URL url) throws IOException,
			ClassNotFoundException {
		ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
				new GZIPInputStream(url.openStream())));
		Object o = ois.readObject();
		ois.close();
		return o;
	}
}
