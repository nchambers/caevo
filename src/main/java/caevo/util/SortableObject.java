package caevo.util;

/**
 * Convenience class that can be sorted by score.
 */
public class SortableObject<E> implements Comparable<SortableObject<E>> {
  double score = 0.0f;
  E key;

  public SortableObject(double s, E k) {
    score = s;
    key = k;
  }
  public SortableObject() { }

  public int compareTo(SortableObject<E> b) {
    if( b == null ) return -1;
    if( score < b.score() ) return 1;
    else if( b.score() < score ) return -1;
    else return 0;
  }

  public void setScore(double s) { score = s; }
  public void setKey(E k) { key = k; }

  public double score() { return score; }
  public E key() { return key; }
  public String toString() {
    return key + " " + score;
  }
}
