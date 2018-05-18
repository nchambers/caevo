package caevo;

import java.util.ArrayList;
import java.util.List;

import caevo.Closure;
import caevo.Evaluate;
import caevo.tlink.EventEventLink;
import caevo.tlink.TLink;
import junit.framework.TestCase;

public class ClosureTest extends TestCase {

  public void testClosure() throws Exception {
    String rules[] = { "e1 e2 BEFORE", "e3 e7 SIMULTANEOUS", "e2 e3 INCLUDES",
        "e3 e4 AFTER", "e5 e3 IS_INCLUDED", "e10 e11 SIMULTANEOUS",
        "e12 e11 SIMULTANEOUS" };

    String expected[] = { "e1 e3 BEFORE", "e1 e5 BEFORE", "e1 e7 BEFORE",
        "e4 e5 BEFORE", "e4 e7 BEFORE", "e2 e5 INCLUDES", "e2 e7 INCLUDES",
        "e7 e5 INCLUDES", "e10 e12 SIMULTANEOUS" };

    // Create the TLink objects.
    List<TLink> links = new ArrayList<TLink>();
    for (String rule : rules) {
      String[] arr = rule.split(" ");
      links.add(new EventEventLink(arr[0], arr[1], TLink.Type.valueOf(arr[2])));
    }
    List<TLink> expectedLinks = new ArrayList<TLink>();
    for (String rule : expected) {
      String[] arr = rule.split(" ");
      expectedLinks
          .add(new EventEventLink(arr[0], arr[1], TLink.Type.valueOf(arr[2])));
    }

    // Load Closure rules.
    Closure closure = new Closure();

    // Check that each closed link appears in the expected list!
    List<TLink> newClosed = closure.computeClosure(links);
    for (TLink link : newClosed)
      assertTrue("Checking generated link (" + link + ") against correct list",
          Evaluate.isLinkCorrect(link, expectedLinks));

    // Check that each expected link appears in the closed list!
    for (TLink link : expectedLinks)
      assertTrue(
          "Didn't find (" + link + ") in the auto-generated closure list.",
          Evaluate.isLinkCorrect(link, newClosed));
  }

}
