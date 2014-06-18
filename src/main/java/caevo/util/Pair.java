package caevo.util;



public class Pair<A,B> {
  A first;
  B second;

  public Pair(A one, B two) {
    first = one;
    second = two;
  }

  public A first() { return first; }
  public B second() { return second; }
  public void setFirst(A obj) { first = obj; }
  public void setSecond(B obj) { second = obj; }
  
  public String toString() {
    return (first + ":" + second);
  }
}