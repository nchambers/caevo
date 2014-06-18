package caevo.tlink;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CollectionUtils;

/**
 * Class to represent a time expression's Class in relation to its document's
 * timestamp, and its features. Each feature can be seen more than once, these
 * aren't binary features.
 */
public class TLinkDatum {
  public static enum TYPE { EESAMENODOMINATE, EESAMEDOMINATES, EEDIFF, EDCT, ETSAME, ETDIFF };
  TYPE _type = null;
  TLink.Type _relation;
  String _docSource = null;
  Counter<String> _featureCounts = null;
  public TLink _originalTLink = null; // for debugging
  
  public TLinkDatum() { }
  public TLinkDatum(TLink.Type yclass) {
    _relation = yclass;
  }

  /**
   * For debugging, keep the TimeBank object around...
   */
  public void setOriginalTLink(TLink link) {
    _originalTLink = link;
  }
  
  public void setDocSource(String doc) {
    _docSource = doc;
  }
  
  public void setType(TYPE type) {
  	_type = type;
  }
  
  public void setLabel(TLink.Type label) {
  	this._relation = label;
  }
  
  public Set<String> getFeatureKeys() {
    if( _featureCounts == null ) 
      return null;
    else
      return _featureCounts.keySet();
  }
  
  public Counter<String> getFeatures() {
  	return _featureCounts;
  }
  
  public double getCount(String feat) {
    return _featureCounts.getCount(feat);
  }
  
  public double totalCount() {
    if( _featureCounts == null ) 
      return 0.0;
    else
      return _featureCounts.totalCount();
  }
  
  public void addFeature(String feat) {
    if( _featureCounts == null ) _featureCounts = new ClassicCounter<String>();
    if( feat != null )
      _featureCounts.incrementCount(feat);
  }
  
  public void addFeature(String feat, double count) {
    if( _featureCounts == null ) _featureCounts = new ClassicCounter<String>();
    if( feat != null )
      _featureCounts.incrementCount(feat, count);
  }
  
  public void addFeatures(Collection<String> feats) {
    if( _featureCounts == null ) _featureCounts = new ClassicCounter<String>();
    if( feats != null ) {
      for( String feat : feats )
        addFeature(feat);
    }
  }
  
  public void addFeatures(Counter<String> feats) {
    if( feats != null ) {
      if( _featureCounts == null ) _featureCounts = new ClassicCounter<String>();
      _featureCounts.addAll(feats);
    }
  }
  
  public void removeFeature(String feat) {
    if( _featureCounts != null )
      _featureCounts.remove(feat);
  }
  
  /**
   * Create an RVFDatum object from our current feature set.
   * @return An RVFDatum object for classification/training.
   */
  public RVFDatum<String,String> createRVFDatum() {
    if( _featureCounts == null )
      return new RVFDatum<String,String>(new ClassicCounter<String>(), (_relation == null ? "null" : _relation.toString()));
    else
      return new RVFDatum<String,String>(_featureCounts, (_relation == null ? "null" : _relation.toString()));
  }
  
  public BasicDatum<String,String> createBasicDatum() {
    if( _featureCounts == null )
      return new BasicDatum<String,String>(new HashSet<String>(), (_relation == null ? "null" : _relation.toString()));
    else
      return new BasicDatum<String,String>(_featureCounts.keySet(), (_relation == null ? "null" : _relation.toString()));
  }
  
  public String getLabelAsString() { return _relation.toString(); }
  public TLink.Type getLabel() { return _relation; }
  public String getSourceDoc() { return _docSource; }
  public TYPE getType() { return _type; };
  
  /**
   * Creates an object from its string form, which should be on one line tab-separated.
   */
  public TLinkDatum valueOf(String str) {
    return valueOf(str, null);
  }
  public TLinkDatum valueOf(String str, Set<String> keepFeats) {
    String[] parts = str.split("\t");
    if( parts.length > 1 ) {
      TLinkDatum datum = new TLinkDatum(TLink.Type.valueOf(parts[0]));
      for( int i = 1; i < parts.length; i += 2 ) {
        try {
          if( keepFeats == null || keepFeats.contains(parts[i]) )
            datum.addFeature(parts[i], Double.valueOf(parts[i+1]));
        } catch( Exception ex ) {
          System.out.println("Error on " + parts[i] + " with " + parts[i+1]);
          ex.printStackTrace();
          System.exit(-1);
        }
      }
//      for( int i = 1; i < parts.length; i++ )
//        datum.addFeature(parts[i]);
      return datum;
    }
    return null;
  }

  /**
   * Reinstantiate this instance as if it is brand new from a String representation.
   * @param str The full datum in string form.
   * @param keepFeats Features to load. null means accept all
   */
  public void valueFrom(String str, Set<String> keepFeats) {
    String[] parts = str.split("\t");
    _relation = TLink.Type.valueOf(parts[0]);
    if( parts.length > 1 ) {
      _featureCounts = null;
      for( int i = 1; i < parts.length; i += 2 ) {
        try {
          if( keepFeats == null || keepFeats.contains(parts[i]) )
            addFeature(parts[i], Double.valueOf(parts[i+1]));
        } catch( Exception ex ) {
          System.out.println("Error on " + parts[i] + " with " + parts[i+1]);
          ex.printStackTrace();
          System.exit(-1);
        }
      }
    }
    else System.out.println("WARNING: no datum on the given line! *" + str + "*");
  }
  
  /**
   * Create an object from its string representation, features separated by tabs.
   * e.g., apw_eng_12323.4124    BEFORE   sameSent    dominates    POS1-1-NN    ...
   * @param str The string representation of the object from toString().
   * @return The new object from the given string.
   */
  public static TLinkDatum fromString(String str) {
    if( str == null ) return null;
    
    try {
      String[] parts = str.split("\t");
      TLinkDatum datum = new TLinkDatum(TLink.Type.valueOf(parts[1]));
      datum.setDocSource(parts[0]);
      for( int ii = 2; ii < parts.length; ii++ )
        datum.addFeature(parts[ii]);
    
      return datum;
    } catch( Exception ex ) {
      System.out.println("ERROR parsing TLinkDatum from string: " + str);
      System.exit(-1);
    }
    return null;
  }
  
  /**
   * Write the features in text order.
   */
  public String toStringSorted() {
    StringBuffer sb = new StringBuffer(_docSource + "\t" + _relation.toString());
    
    for (String key : CollectionUtils.sorted(_featureCounts.keySet())) {
      sb.append("\t");
      sb.append(key);
//      sb.append("\t");
//      sb.append((int)_featureCounts.getCount(key));
    }
//    sb.append(Counters.toSortedByKeysString(_featureCounts, "%1$s %2$f", "\t", "HAPPY"));
    
    return sb.toString();
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer(_docSource + "\t" + (_relation != null ? _relation.toString() : "null"));
    sb.append('\t');
    sb.append((_type != null ? _type.toString() : "null"));
    for( Entry<String,Double> entry : _featureCounts.entrySet() ) {
      sb.append('\t');
      sb.append(entry.getKey());
//      sb.append('\t');
//      sb.append(entry.getValue().intValue());
    }
    return sb.toString();
  }
}
