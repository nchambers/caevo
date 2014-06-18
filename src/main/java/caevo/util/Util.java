package caevo.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import caevo.Timex;
import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.Counter;


public class Util {
  public Util() { }

  public static void force24hrTimex(Timex timex) {
  	String value = timex.getValue();
  	if( !timex.isReference() ) {
  		if( value.matches("^\\d\\d\\d\\d-\\d\\d-\\d\\d.+") ) {
  			System.out.println("Changing timex " + value + " to " + value.substring(0, 10));
  			timex.setValue(value.substring(0, 10));
  		}
  	}
  }
  
  /**
   * Read a single serialized classifier into memory.
   * @param url The path to the model. 
   * @return The classifier object.
   */
  public static Classifier<String,String> readClassifierFromFile(URL url) {
  	if( url == null ) System.out.println("ERROR: null classifier path!");
  	else {
  		try {
  			ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(url.openStream())));
  			Object o = ois.readObject();
  			ois.close();
  			Classifier<String,String> classifier = (Classifier<String,String>)o;
  			return classifier;
  		} catch(Exception ex) { 
  			System.out.println("ERROR: Had fatal trouble loading url=" + url);
  			ex.printStackTrace(); System.exit(1); 
  		}
  	}
  	return null;
  }

  public static Classifier<String,String> readClassifierFromFile(String path) {
  	try {
  		Classifier<String,String> classifier = (Classifier<String,String>)IOUtils.readObjectFromFile(path);
  		return classifier;
  	} catch(Exception ex) { 
  		System.out.println("ERROR: Had fatal trouble loading path=" + path);
  		ex.printStackTrace(); System.exit(1); 
  	}
  	return null;
  }
  
  /**
   * Given an event, increment its count by the given amount.
   */
  public static <E> void incrementCount(Map<E,Integer> counts, E key, int count) {
    Integer currentCount = counts.get(key);
    if( currentCount == null ) currentCount = 0;
    counts.put(key, currentCount+count);
  }

  /**
   * Given an event, increment its count by the given amount.
   */
  public static void incrementCount(Map<String, Float> counts,
      String eventString, float count) {
    Float currentCount = counts.get(eventString);
    if( currentCount == null ) currentCount = 0.0f;
    counts.put(eventString, currentCount+count);
  }

  /**
   * Given an event, increment its count by the given amount.
   */
  public static void incrementCount(Map<String, Double> counts,
      String eventString, double count) {
    Double currentCount = counts.get(eventString);
    if( currentCount == null ) currentCount = 0.0;
    counts.put(eventString, currentCount+count);
  }

  /**
   * Given an Integer ID, increment its count by the given amount.
   */
  public static void incrementCount(Map<Integer, Integer> counts,
      Integer id, int count) {
    Integer currentCount = counts.get(id);
    if( currentCount == null ) currentCount = 0;
    counts.put(id, currentCount+count);
  }

  /**
   * Given an Integer ID, increment its count by the given amount.
   */
  public static void incrementCount(Map<Integer, Float> counts,
      Integer id, float count) {
    Float currentCount = counts.get(id);
    if( currentCount == null ) currentCount = 0.0f;
    counts.put(id, currentCount+count);
  }

  /**
   * Given an Integer ID, increment its count by the given amount.
   */
  public static void incrementCount(Map<Integer, Double> counts, Integer id, double count) {
    Double currentCount = counts.get(id);
    if( currentCount == null ) currentCount = 0.0;
    counts.put(id, currentCount+count);
  }

  public static int sumValues(Map<String,Integer> counts) {
    int sum = 0;
    if( counts != null )
      for( Map.Entry<String, Integer> entry : counts.entrySet() )
        sum += entry.getValue();
    return sum;
  }

  public static <E> void countsToFile(String path, Map<E,Integer> counts) {
    try {
      PrintWriter writer;
      writer = new PrintWriter(new BufferedWriter(new FileWriter(path, false)));
      for( E key : sortKeysByIntValues(counts) ) {
        writer.write(key + "\t" + counts.get(key) + "\n");
      }
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }    
  }
  
  public static Map<String,Integer> countsFromFile(String path) {
    Map<String,Integer> counts = new HashMap<String,Integer>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;
      while( (line = in.readLine()) != null ) {
        int tabindex = line.indexOf('\t');
        String key = line.substring(0,tabindex);
        int count = Integer.valueOf(line.substring(tabindex+1));
        counts.put(key, count);
      }
      in.close();
    } catch( Exception ex ) { 
      System.err.println("Error opening " + path);
      ex.printStackTrace();
    }
    return counts;    
  }
  
  public static List<String> readLinesFromFile(String path) {
    List<String> lines = new ArrayList<String>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;
      while( (line = in.readLine()) != null ) {
        lines.add(line);
      }
      in.close();
    } catch( Exception ex ) { 
      System.err.println("Error reading from " + path);
      ex.printStackTrace();
    }
    return lines;    
  }
  
  /**
   * Read a file of strings and counts. Return all strings that have a count greater
   * than or equal to the given threshold n.
   * @param path The file path.
   * @param n The threshold, all counts higher or equal are returned.
   * @return All keys in the file that have higher/equal counts to n.
   */
  public static Set<String> keysFromFileMeetingThreshold(String path, int n) {
    Set<String> keys = new HashSet<String>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;
      while( (line = in.readLine()) != null ) {
        int tabindex = line.indexOf('\t');
        String key = line.substring(0,tabindex);
        int count = Integer.valueOf(line.substring(tabindex+1));
        if( count >= n )
          keys.add(key);
      }
      in.close();
    } catch( Exception ex ) { 
      System.err.println("Error opening " + path);
      ex.printStackTrace();
    }
    return keys;    
  }
  
  /**
   * Scale the values of the map to be between [0,1].
   */
  public static void scaleToUnit(Map<Object, Double> counts) {
    double max = 0.0;
    double min = Double.MAX_VALUE;
    for( Map.Entry<Object,Double> entry : counts.entrySet() ) {
      double value = entry.getValue();
      if( value > max ) max = value;
      if( value < min ) min = value;
    }

    double range = max - min;

    // Scale between [0,1]
    for( Map.Entry<Object,Double> entry : counts.entrySet() ) {
      double value = entry.getValue();
      double scaledValue = (value - min) / range;
      entry.setValue(scaledValue);
    }
  }

  /**
   * Scale the values of the map to be between [0,1].
   */
  public static void scaleToUnit(SortableScore scores[]) {
    double max = 0.0;
    double min = Double.MAX_VALUE;
    for( SortableScore score : scores ) {
      double value = score.score();
      if( value > max ) max = value;
      if( value < min ) min = value;
    }

    double range = max - min;

    // Scale between [0,1]
    for( SortableScore score : scores ) {
      double value = score.score();
      double scaledValue = (value - min) / range;
      score.setScore(scaledValue);
    }
  }

  public static <E> void scaleToUnit(SortableObject<E> scores[]) {
    double max = 0.0;
    double min = Double.MAX_VALUE;
    for( SortableObject<E> score : scores ) {
      double value = score.score();
      if( value > max ) max = value;
      if( value < min ) min = value;
    }

    double range = max - min;

    // Scale between [0,1]
    for( SortableObject<E> score : scores ) {
      double value = score.score();
      double scaledValue = (value - min) / range;
      score.setScore(scaledValue);
    }
  }


  /**
   * Read integers from a file, one integer per line.
   */
  public static List<Integer> slurpIntegers(String filename) {
    List<Integer> guesses = new ArrayList<Integer>();

    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;

      while( (line = in.readLine()) != null ) {
        if( line.matches("-?\\d+") )
          guesses.add(Integer.parseInt(line));
        else
          System.out.println("WARNING: skipping line in " + filename + ": " + line);
      }

      in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }

    return guesses;
  }

  /**
   * @return A String array of length two, the directory and the file.
   *         Directory is null if there is none.
   */
  public static String[] splitDirectoryAndFile(String path) {
    int index = path.lastIndexOf(File.separatorChar);
    String[] pair = new String[2];

    // strip trailing separators
    while( index >= 0 && index == path.length()-1 ) {
      path = path.substring(0, path.length()-1);
      index = path.lastIndexOf(File.separatorChar);
    }

    // if no directory in the path
    if( index == -1 ) {
      pair[0] = null;
      pair[1] = path;
    }
    // split off the directory
    else {
      pair[0] = path.substring(0, index);
      pair[1] = path.substring(index+1);
    }
    return pair;
  }

  /**
   * @return A new list with only n elements.
   */
  public static <E> List<E> getFirstN(Collection<E> objects, int n) {
    if( objects == null ) return null;

    List<E> newlist = new ArrayList<E>();
    int i = 0;
    for( E item : objects ) {
      if( i < n )
        newlist.add(item);
      else break;
      i++;
    }
    return newlist;
  }

  /**
   * DESTRUCTIVE: Trim the list to just the first n objects.
   */
  public static <E> void firstN(List<E> objects, int n) {
    if( objects == null ) return;

    while( objects.size() > n ) {
      objects.remove(objects.size()-1);
    }
  }

  /**
   * Create a new array with just the first n objects.
   */
  public static SortableScore[] firstN(SortableScore[] scores, int n) {
    if( scores == null ) return null;

    int newsize = Math.min(n, scores.length);
    SortableScore[] arr = new SortableScore[newsize];
    for( int i = 0; i < newsize; i++ )
      arr[i] = scores[i];
    return arr;
  }

  public static <E> List<E> mergeLists(List<E> first, List<E> second) {
    List<E> merged = new ArrayList<E>(first);
    for( E obj : second ) {
      if( !merged.contains(obj) )
        merged.add(obj);
    }
    return merged;
  }
  
  public static <E> List<E> arrayToList(E[] arr) {
    List<E> thelist = new ArrayList<E>();
    for( E obj : arr )
      thelist.add(obj);
    return thelist;
  }

  /**
   * String match search for a string in a list.
   */
  public static boolean findInListOfStrings(List<String> items, String target) {
  	if( items == null || target == null )
  		return false;
  	
  	for( String item : items )
  		if( target.equalsIgnoreCase(item) )
  			return true;
  	
  	return false;
  }
  
  public static <E> List<E> collectionToList(Collection<E> theset) {
    List<E> thelist = new ArrayList<E>();
    for( E obj : theset ) thelist.add(obj);
    return thelist;
  }

  public static String[] sortStrings(Collection<String> unsorted) {
    String[] sorted = new String[unsorted.size()];
    sorted = unsorted.toArray(sorted);
    Arrays.sort(sorted);
    return sorted;
  }

  /**
   * Sort the keys of the map by their values, return the list in order.
   */
  public static <E> List<E> sortCounterKeys(Counter<E> map) {
    return sortCounterKeys(map, false);
  }
  public static <E> List<E> sortCounterKeys(Counter<E> map, boolean reverse) {
    SortableObject<E>[] sorted = new SortableObject[map.size()];
    int i = 0;
    for( Map.Entry<E, Double> entry : map.entrySet() )
      sorted[i++] = new SortableObject<E>(entry.getValue(), entry.getKey());
    if( !reverse ) Arrays.sort(sorted);
    else Arrays.sort(sorted, Collections.reverseOrder());

    List<E> strs = new ArrayList<E>(sorted.length);
    for( SortableObject<E> obj : sorted )
      strs.add(obj.key());
    return strs;
  }
  
  /**
   * Sort the keys of the map by their values, return the list in order.
   */
  public static <E> List<E> sortKeysByValues(Map<E,Double> map) {
    return sortKeysByValues(map, false);
  }
  public static <E> List<E> sortKeysByValues(Map<E,Double> map, boolean reverse) {
    SortableObject<E>[] sorted = new SortableObject[map.size()];
    int i = 0;
    for( Map.Entry<E, Double> entry : map.entrySet() )
      sorted[i++] = new SortableObject<E>(entry.getValue(), entry.getKey());
    if( !reverse ) Arrays.sort(sorted);
    else Arrays.sort(sorted, Collections.reverseOrder());

    List<E> strs = new ArrayList<E>(sorted.length);
    for( SortableObject<E> obj : sorted )
      strs.add(obj.key());
    return strs;
  }
    
  /**
   * Sort the keys of the map by their values, return the list in order.
   */
  public static <E> List<E> sortKeysByIntValues(Map<E,Integer> map) {
    return sortKeysByIntValues(map, false);
  }
  public static <E> List<E> sortKeysByIntValues(Map<E,Integer> map, boolean reverse) {
    if( map == null ) return null;    
    SortableObject<E>[] sorted = new SortableObject[map.size()];
    int i = 0;
    for( Map.Entry<E, Integer> entry : map.entrySet() )
      sorted[i++] = new SortableObject<E>(entry.getValue(), entry.getKey());
    if( !reverse ) Arrays.sort(sorted);
    else Arrays.sort(sorted, Collections.reverseOrder());
    
    List<E> strs = new ArrayList<E>(sorted.length);
    for( SortableObject<E> obj : sorted )
      strs.add((E)obj.key());
    return strs;
  }

  public static void printMapSortedByValue(Map<String,Integer> map) {
    printMapSortedByValue(map, (map == null ? 0 : map.size()));
  }

  public static void printMapSortedByValue(Map<String,Integer> map, int max) {
    if( map == null || map.size() == 0 )
      System.out.println("null");
    else {
      SortableObject<String>[] sorted = new SortableObject[map.size()];
      int i = 0;
      for( Map.Entry<String, Integer> entry : map.entrySet() )
        sorted[i++] = new SortableObject<String>(entry.getValue(), entry.getKey());
      Arrays.sort(sorted);

      int num = 0;
      for( SortableObject<String> obj : sorted ) {
        System.out.print(obj.key + " " + map.get(obj.key) + " ");
        num++;
        if( num == max ) break;
      }
      System.out.println();
    }
  }
  
  public static void printDoubleMapSortedByValue(Map<String,Double> map, int max) {
    if( map == null || map.size() == 0 )
      System.out.println("null");
    else {
      SortableObject<String>[] sorted = new SortableObject[map.size()];
      int i = 0;
      for( Map.Entry<String, Double> entry : map.entrySet() )
        sorted[i++] = new SortableObject<String>(entry.getValue(), entry.getKey());
      Arrays.sort(sorted);

      int num = 0;
      for( SortableObject<String> obj : sorted ) {
        System.out.printf("%s %.3f ", obj.key, map.get(obj.key));
        num++;
        if( num == max ) break;
      }
      System.out.println();
    }
  }
  
  /**
   * Removes all map entries whose integer count is the cutoff or less.
   */
  public static void trimIntegerCounts(Map<String,Integer> map, int cutoff) {
    List<String> removed = new ArrayList<String>();

    for( Map.Entry<String,Integer> entry : map.entrySet() ) {
      Integer count = entry.getValue();
      // Remove the pair if it is sparse
      if( count == null || count <= cutoff ) removed.add(entry.getKey());
    }

    for( String key : removed ) map.remove(key);
    //    System.out.println("Trimmed " + removed.size() + " pairs");
  }

  /**
   * Removes all map entries whose integer count is the cutoff or less.
   */
  public static void trimFloatCounts(Map<String,Float> map, float cutoff) {
    List<String> removed = new ArrayList<String>();

    for( Map.Entry<String,Float> entry : map.entrySet() ) {
      Float count = entry.getValue();
      // Remove the pair if it is sparse
      if( count == null || count <= cutoff ) removed.add(entry.getKey());
    }

    for( String key : removed ) map.remove(key);
    //    System.out.println("Trimmed " + removed.size() + " pairs");
  }
  
  /**
   * Create a string list of the items, up to n long.
   */
  public static String collectionToString(Collection<String> set, int n) {
    if( set == null ) return "";
    
    StringBuffer buf = new StringBuffer();
    int i = 0;
    for( String str : set ) {
      if( i > 0 ) buf.append(' ');
      buf.append(str);
      i++;
      if( i == n ) break;
    }
    return buf.toString();
  }
  
  public static double calculateEntropy(double[] probs) {
    double entropy = 0.0;
    for( int i = 0; i < probs.length; i++ ) {
      if( probs[i] > 0.0 )
        entropy -= probs[i] * Math.log(probs[i]);
    }
    return entropy;
  }
  
  /**
   * Standard factorial calculation.
   */
  public static int factorial(int n) {
    if( n == 0 ) return 1;
    int fact = 1;
    for( int i = 1; i <= n; i++ )
      fact *= i;
    return fact;
  }
  
  /**
   * Standard "n choose k" calculation.
   */
  public static int choose(int n, int k) {
    return factorial(n) / factorial(k)*factorial(n-k);
  }
  
  public static void reportMemory() {
    Runtime runtime = Runtime.getRuntime();
    long mb = 1024 * 1024;
    //    long max = runtime.maxMemory();
    long used = runtime.totalMemory() - runtime.freeMemory();
    System.out.printf("......................... memory in use: %d MB .........................%n", used / mb);
  }

  public static void reportElapsedTime(long startTime) {
    String str = timeString(System.currentTimeMillis()-startTime);
    System.out.println("............................ runtime " + str + " ..........................");
  }

    public static String timeString(long milli) {
	long seconds = milli / 1000;
	long minutes = seconds/60;
	long hours = minutes/60;
	minutes = minutes%60;
	seconds = seconds%60;
	return (hours + "h " + minutes + "m " + seconds + "s");
    }

  
  public static void main(String[] args) {
    double[] probs = new double[4];
    probs[0] = 0.7;
    probs[1] = 0.3;
    probs[2] = 0.45;
    probs[3] = 0.1;
    
    System.out.println("sorted = " + Arrays.toString(probs));
    
    String feature = "running somewhere";
    System.out.println("bytes = " + feature.getBytes().length);

//    System.out.println("entropy = " + calculateEntropy(probs));
  }
}
