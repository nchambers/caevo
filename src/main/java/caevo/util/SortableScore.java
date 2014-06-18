package caevo.util;

/**
 * Convenience class that can be sorted by score.
 */
public class SortableScore implements Comparable<SortableScore> {
  double score = 0.0f;
  String key;

  public SortableScore(double s, String k) {
    score = s;
    key = k;
  }
  public SortableScore() { }

  public int compareTo(SortableScore b) {
    if( b == null ) return -1;
    if( score < ((SortableScore)b).score() ) return 1;
    else if( ((SortableScore)b).score() > score ) return -1;
    else return 0;
  }

  public void setScore(double s) { score = s; }
  public void setKey(String k) { key = k; }

  public double score() { return score; }
  public String key() { return key; }
  public String toString() {
    return key + " " + score;
  }
}
