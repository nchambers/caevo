package caevo.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import caevo.SieveDocument;
import caevo.SieveDocuments;
import caevo.SieveSentence;
import caevo.TextEvent;
import caevo.Timex;
import caevo.tlink.EventTimeLink;
import caevo.tlink.TLink;
import caevo.tlink.TLinkClassifier;
import caevo.tlink.TLinkDatum;
import caevo.tlink.TLinkFeaturizer;
import caevo.util.Pair;
import caevo.util.CaevoProperties;
import caevo.util.TimebankUtil;
import caevo.util.Util;
import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;

/**
 * Machine learned event-DCT pairs.
 *
 * @author chambers
 */
public class MLEventDCT implements Sieve {
  Classifier<String, String> eDCTClassifier = null; // event-DCT links.
  Classifier<String, String> eDCTExistsClassifier = null; // binary, is there a
                                                          // link or not?
  TLinkFeaturizer featurizer;

  String eDCTName = "tlink.edct.classifier";

  boolean debug = true;
  int minFeatOccurrence = 2;
  double minProb = 0.0;

  /**
   * Constructor uses the global properties for parameters.
   */
  public MLEventDCT() {
    // Setup the featurizer for event-event intrasentence links.
    featurizer = new TLinkFeaturizer();
    featurizer._eventDCTOnly = true;
    featurizer._noEventDCT = false;
    featurizer._eventTimeOnly = true;
    featurizer._eventEventOnly = false;
    featurizer._sameSentenceOnly = false;
    featurizer._ignoreSameSentence = false;
    featurizer._diffSentenceOnly = false;
    featurizer._neighborSentenceOnly = false;

    featurizer.debug = false;

    init();
  }

  private void init() {
    // Flags
    try {
      debug = CaevoProperties.getBoolean("MLEventDCT.debug", false);
      minProb = CaevoProperties.getDouble("MLEventDCT.minProb", 0.0);
      minFeatOccurrence = CaevoProperties.getInt("MLEventDCT.minFeatCount", 2);
    } catch (IOException ex) {
    }

    readClassifiers();
  }

  /**
   * The main function. All sieves must have this.
   */
  public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
    // Classifier loading must have failed in init()
    if (eDCTClassifier == null)
      return null;

    List<TLink> labeled = extractEventDCTLinks(doc);

    TimebankUtil.trimLowProbability(labeled, minProb);
    return labeled;
  }

  /**
   * Put event-time links into the global .info file between different sentence
   * event-time links.
   */
  public List<TLink> extractEventDCTLinks(SieveDocument doc) {
    if (debug)
      System.out.println(doc.getSentences().size() + " sentences.");
    List<TLink> tlinks = new ArrayList<TLink>();

    // Get the DCT object.
    Timex dct = null;
    List<Timex> dcts = doc.getDocstamp();
    if (dcts != null && dcts.size() > 0)
      dct = dcts.get(0);

    // Loop over sentences and get TLinks that cross sentence boundaries between
    // events and times.
    if (dct != null) {
      for (SieveSentence sent : doc.getSentences()) {
        if (sent.events() != null) {
          for (TextEvent event : sent.events()) {
            TLinkDatum datum = featurizer.createEventDocumentTimeDatum(doc,
                event, dct, null);
            Pair<String, Double> labelProb = TLinkClassifier
                .getLabelProb(eDCTClassifier, datum.createRVFDatum());
            TLink link = new EventTimeLink(event.getEiid(), dct.getTid(),
                TLink.Type.valueOf(labelProb.first()));
            link.setRelationConfidence(labelProb.second());
            tlinks.add(link);
          }
        }
      }
    }
    if (debug)
      System.out.println("Returning e-dct tlinks: " + tlinks);
    return tlinks;
  }

  private void readClassifiers() {
    String path = "/models/tlinks/" + eDCTName;
    System.out.println("Loading edct from " + path);
    eDCTClassifier = Util
        .readClassifierFromFile(this.getClass().getResource(path));
  }

  /**
   * Train on the documents.
   */
  public void train(SieveDocuments docs) {
    featurizer.debug = true;
    List<TLinkDatum> data = featurizer.infoToTLinkFeatures(docs, null);
    System.out.println("Final training data size: " + data.size());

    if (debug) {
      for (TLinkDatum dd : data) {
        System.out.println("** " + dd._originalTLink);
        System.out.println(dd);
      }
    }

    eDCTClassifier = TLinkClassifier.train(data, minFeatOccurrence);

    try {
      IOUtils.writeObjectToFile(eDCTClassifier, eDCTName);
    } catch (Exception ex) {
      System.out
          .println("ERROR: couldn't write classifiers to file in MLEventDCT");
      ex.printStackTrace();
    }
  }

}
