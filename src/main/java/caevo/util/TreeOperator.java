package caevo.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import edu.stanford.nlp.ling.CoreAnnotations.CopyAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * Useful functions for processing Tree objects.
 * 
 * @author Nate Chambers
 */
public class TreeOperator {

  TreeOperator() { }


  public static String argsOfPhrase(Tree tree, Tree parent) {
    String args = "";
    List<Tree> children = parent.getChildrenAsList();
    for( Tree child : children ) {
      if( child != tree ) {
        if( args.length() == 1 ) args = child.label().toString();
        else args += ":" + child.label().toString();
      }
    }
    return args;
  }


  /**
   * @return A list of verb trees e.g. (VP (VBG running))
  public static Vector<Tree> verbTreesFromTree(Tree tree, Tree parent) {
    Vector<Tree> verbs = new Vector();
    // if tree is a leaf
    if( tree.isPreTerminal() && tree.label().toString().startsWith("VB") ) {
      // add the verb subtree
      verbs.add(parent);
    }
    // else scale the tree    
    else {
      List<Tree> children = tree.getChildrenAsList();
      for( Tree child : children ) {
	Vector temp = verbTreesFromTree(child, tree);
	verbs.addAll(temp);
      }
    }

    return verbs;
  }
   */

  /**
   * @return A list of verb subtrees e.g. (VBG running)
   */
  public static Vector<Tree> verbTreesFromTree(Tree tree) {
//    System.out.println("verbTree: " + tree);
    Vector<Tree> verbs = new Vector<Tree>();
//    System.out.println("  tree label: " + tree.label().value().toString());
    
    // if tree is a leaf
    if( tree.isPreTerminal() && tree.label().value().startsWith("VB") ) {
//      System.out.println("  if!!");
      // add the verb subtree
      verbs.add(tree);
    }
    // else scale the tree    
    else {
//      System.out.println("  else!!");
      List<Tree> children = tree.getChildrenAsList();
      for( Tree child : children ) {
        Vector<Tree> temp = verbTreesFromTree(child);
        verbs.addAll(temp);
      }
    }

    return verbs;
  }


  /**
   * @return A list of verbs in the tree
   */
  public static Vector<String> verbsFromTree(Tree tree, Tree parent) {
    Vector<String> verbs = new Vector<String>();
    // if tree is a leaf
    if( tree.isPreTerminal() && tree.label().toString().startsWith("VB") ) {
      String verb = tree.firstChild().value();
      // get arguments
      //      System.out.println("parent of " + verb + " is " + parent);
      //      verb += argsOfPhrase(tree, parent);
      //      System.out.println("now: " + verb);
      // add the verb
      verbs.add(verb);
    }
    // else scale the tree    
    else {
      List<Tree> children = tree.getChildrenAsList();
      for( Tree child : children ) {
        Vector<String> temp = verbsFromTree(child, tree);
        verbs.addAll(temp);
      }
    }

    return verbs;
  }

  public static Vector<String> verbsFromTree(Tree tree) {
    return verbsFromTree(tree, null);
  }

  /**
   * @param index The index of the verb token in the sentence.
   * @param tree The entire parse tree.
   * @return The tense of the verb using hand-crafted rules.
   */
  public static String tenseOfVerb(int index, Tree tree) {
    if( index > 1000 ) index -= 1000;
    
//    Tree subtree = indexToSubtree(tree, index);
    List<Tree> leaves = leavesFromTree(tree);
//    System.out.println("leaves: " + leaves);
    String leftmostVerbTag = null;    
    String secondLeftmostVerbTag = null;
    int i = index-1; // word indexes start at 1, not 0
    
  
    // Move left until no more verbs.
    while( i > -1 && 
        (leaves.get(i).label().value().startsWith("VB") || leaves.get(i).label().value().startsWith("TO") ||
            leaves.get(i).label().value().startsWith("RB")) ) {
      if( leaves.get(i).label().value().startsWith("VB") ) {
        secondLeftmostVerbTag = leftmostVerbTag; 
        leftmostVerbTag = leaves.get(i).label().value();
      }
//      System.out.println("just checked " + leaves.get(i));        
      i--;
    }
    
    if( leftmostVerbTag == null ) {
      System.out.println("ERROR: null lastVerb. Leaf i=" + i + " =" + leaves.get(i) + 
          " from index=" + index + " tree=" + tree);
      System.out.println("  leaves = " + leaves);
      System.out.println("  indexToPOSTag = " + indexToPOSTag(tree, index));
      System.out.println("  countLeaves = " + countLeaves(tree));
      System.exit(-1);
    }
    
    String tag = (i >= 0 ? leaves.get(i).label().value() : null);
    String token = (i >= 0 ? leaves.get(i).firstChild().value() : null);
    //    System.out.println("tag=" + tag + " token=" + token);
    if( tag != null && tag.equals("MD") && (token.equals("will") || token.equals("wo")) ) {
      return "FUTURE";
    }
    else if( leftmostVerbTag != null && 
        (leftmostVerbTag.equals("VBD") || 
            // "He might have/VB left/VBN by midnight."
            (leftmostVerbTag.equals("VB") && secondLeftmostVerbTag != null && secondLeftmostVerbTag.equals("VBN"))) )
      return "PAST";
    else {
      // "He should leave soon."  ("He should have left" is covered by past tense "VB VBN" rule above.)      
      if( token != null && (token.equals("should") || token.equals("shall") || token.equals("could")) )
        return "FUTURE";
      
      if( tag != null && tag.equals("MD") ) // some modal we don't know what to do with
        return null;
        
      else
        return "PRESENT";
    }
  }

  public static List<String> stringLeavesFromTree(Tree tree) {
    List<String> strs = new ArrayList<String>();
    List<Tree> leaves = leavesFromTree(tree);
    for( Tree leaf : leaves )
      strs.add(leaf.children()[0].nodeString());
    return strs;
  }
  
  public static List<String> posTagsFromTree(Tree tree) {
    List<String> strs = new ArrayList<String>();
    List<Tree> leaves = leavesFromTree(tree);
    for( Tree leaf : leaves )
      strs.add(leaf.label().value());
    return strs;
  }
  
  /**
   * @return A Vector of all the leaves ... we basically flatten the tree
   */
  public static List<Tree> leavesFromTree(Tree tree) {
    List<Tree> leaves = new ArrayList<Tree>();
//    System.out.println("myt=" + tree);
    // if tree is a leaf
//  if( tree.isPreTerminal() || tree.firstChild().isLeaf() ) {
    if( tree.firstChild().isLeaf() ) {
//      System.out.println(" * preterm");
      // add the verb subtree
      leaves.add(tree);
    }
    // else scale the tree
    else {
//      System.out.println(" * scaling");
      List<Tree> children = tree.getChildrenAsList();
      for( Tree child : children ) {
        List<Tree> temp = leavesFromTree(child);
        leaves.addAll(temp);
      }
    }

    return leaves;
  }

  /**
   * @return The raw text of a parse tree's nodes
   */
  public static String toRaw(Tree full) {
    if( full == null ) return "";

    if( full.isPreTerminal() ) return full.firstChild().value();
    else {
      String str = "";
      for( Tree child : full.getChildrenAsList() ) {
        if( str.length() == 0 ) str = toRaw(child);
        else str += " " + toRaw(child);
      }
      return str;
    }
  }


  /**
   * @return The WORD INDEX (starting at 0) where the stop tree begins
   */
  public static int wordIndex(Tree full, Tree stop) {
    if( full == null || full == stop ) return 0;

    int sum = 0;
//  if( full.isPreTerminal() ) {
    if( full.firstChild().isLeaf() ) {
      return 1;
    }
    else {
      for( Tree child : full.getChildrenAsList() ) {
        if( child == stop ) {
          //	  System.out.println("Stopping at " + child);
          return sum;
        }
        sum += wordIndex(child, stop);
        if( child.contains(stop) ) return sum;
      }
    }
    return sum;
  }

  /**
   * Retrieve the POS tag of the token at the given index in the given tree. Indices start at 1.
   * @param full The full parse tree.
   * @param goal The index of the desired token.
   * @return The token's POS tag in String form.
   */
  public static String indexToPOSTag(Tree full, int goal) {
    // Hack fix for old parser error with indices from conjunctions.
    if( goal > 1000 ) goal -= 1000;
    
    Tree subtree = indexToSubtree(full, goal);
    if( subtree == null )
      return null;
    else return subtree.label().value();
  }
  
  public static String indexToToken(Tree full, int goal) {
    // Hack fix for old parser error with indices from conjunctions.
    if( goal > 1000 ) goal -= 1000;
    
    Tree subtree = indexToSubtree(full, goal);
    if( subtree == null )
      return null;
    else return subtree.children()[0].nodeString();//.value().toString();
  }

  /**
   * Assumes the goal is a word index in the sentence, and the first word starts
   * at index 1.
   * @return Subtree rooted where the index begins.  It's basically the
   * index's individual POS tree: (NNP June) or (CD 13)
   */
  public static Tree indexToSubtree(Tree full, int goal) {
    return indexToSubtreeHelp(full,0,goal);
  }
  public static Tree indexToSubtreeHelp(Tree full, int current, int goal) {
//    System.out.println("--" + current + "-" + full + "-preterm" + full.isPreTerminal() + "-goal" + goal);
    if( full == null ) return null;

    if( (current+1) == goal && 
//        (full.isPreTerminal() || full.label().value().equals("CD")) )
        full.firstChild().isLeaf() )
      return full;
    else {
      for( Tree child : full.getChildrenAsList() ) {
        int length = countLeaves(child);
        //	System.out.println("--Child length " + length);
        if( goal <= current+length )
          return indexToSubtreeHelp(child, current, goal);
        else
          current += length;
      }
    }
    return null;
  }


  /**
   * @return The CHARACTER OFFSET where the stop tree begins
   */
  public static int inorderTraverse(Tree full, Tree stop) {
    if( full == null || full == stop ) return 0;

    int sum = 0;
    if( full.isPreTerminal() ) {
      String value = full.firstChild().value();
      //      System.out.println(value + " is " + value.length());
      return value.length() + 1; // +1 for space character
      //      return full.firstChild().value().length() + 1;
    }
    else {
      for( Tree child : full.getChildrenAsList() ) {
        if( child == stop ) {
          //	  System.out.println("Stopping at " + child);
          return sum;
        }
        sum += inorderTraverse(child, stop);
        if( child.contains(stop) ) return sum;
      }
    }
    return sum;
  }

  public static String pathNodeToNode(Tree tree, Tree sub1, Tree sub2, boolean noPOS) {
    List<Tree> path = tree.pathNodeToNode(sub1, sub2);
    if( noPOS ) {
      if( path.size() < 2 ) { // probably comparing the same two subtrees
        System.out.println("ERROR: pathNodeToNode length too short: sub1=" + sub1 + " sub2=" + sub2 + " path=" + path + "\ttree" + tree);
        path = new ArrayList<Tree>();
      }
      else
        path = path.subList(1, path.size()-1);
    }
    
    int x = 0;
    String stringpath = "";
    for( Tree part : path ) {
      if( x == 0 )
        stringpath += part.label().value();
      else
        stringpath += "-" + part.label().value();
      x++;
    }
    return stringpath;
  }
  
  /**
   * @return The number of leaves (words) in this tree
   */
  //  public static int treeWordLength(Tree tree) {
  //    return countLeaves(tree)-1;
  //  }
  public static int countLeaves(Tree tree) {
    int sum = 0;
    //    System.out.println("top with tree " + tree);
    //    if( tree.isPreTerminal() ) {
    // Phone numbers are (CD (727) 894-1000) ... but they aren't in the
    // "preterminal" definition of the function, so we much explicitly check.
//    if( tree.isPreTerminal() || tree.label().value().equals("CD") ) {
    if( tree.firstChild() != null && tree.firstChild().isLeaf() ) {
      //      System.out.println("leaf: " + tree);
      return 1;
    }
    else {
      //      System.out.println("getchildren " + tree.getChildrenAsList().size());
      for( Tree child : tree.getChildrenAsList() ) {
        sum += countLeaves(child);
      }
    }
    return sum;
  }
  
  /**
   * @return The char length of the string represented by this tree
   */
  public static int treeStringLength(Tree tree) {
    return getTreeLength(tree)-1;
  }

  public static int getTreeLength(Tree tree) {
    int sum = 0;
    if( tree.firstChild().isLeaf() )
      return 1;
    else
      for( Tree child : tree.getChildrenAsList() )
        sum += getTreeLength(child);
    return sum;
  }

  /**
   * Build the Tree object from its string form.
   */
  public static Tree stringToTree(String str, TreeFactory tf) {
    PennTreeReader ptr = null;
  	try {
      ptr = new PennTreeReader(new BufferedReader(new StringReader(str)), tf);
      Tree parseTree = ptr.readTree();
      return parseTree;
    } catch( Exception ex ) { ex.printStackTrace(); }
  	finally {
  		if (ptr != null)
				try {
					ptr.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
  	}
    return null;
  }

  /**
   * Build Tree objects from a collection of parses in string form.
   */
  public static List<Tree> stringsToTrees(Collection<String> strings) {
    if( strings != null ) {
      List<Tree> trees = new ArrayList<Tree>();
      TreeFactory tf = new LabeledScoredTreeFactory();
      for( String str : strings )
        trees.add(stringToTree(str, tf));
      return trees;
    }
    else return null;
  }
  
  public static boolean flatNP(Tree tree, int start, int end) {
    Tree startTree = indexToSubtree(tree, start);
    Tree endTree = indexToSubtree(tree, end-1);

    Tree startParent = startTree.parent(tree);
    Tree endParent = endTree.parent(tree);
    
//    if( startParent == endParent ) System.out.println("  same!!");
//    else System.out.println("  diff!!");
    
    if( startParent == endParent )
      return true;
    else return false;
  }
  
  /**
   * Turn a string representation of a dependency into the JavaNLP object.
   * @param line String of dep: e.g. nsubj testified-17 dealer-16
   */
  public static TypedDependency stringToDependency(String line) {
    String parts[] = line.split("\\s+");
    if( parts.length != 3 ) {
      System.out.println("ERROR: unknown dep format: " + line);
      System.exit(-1);
    }
    return createDependency(parts[0], parts[1], parts[2]);
  }
  
  /**
   * Turn a string representation of a dependency into the JavaNLP object.
   * @param line String of dep: e.g. nsubj(testified-17, dealer-16)
   */
  public static TypedDependency stringParensToDependency(String line) {
    String parts[] = line.split("\\s+");
    if( parts.length != 2 ) {
      System.out.println("ERROR: unknown dep format: " + line);
      System.exit(-1);
    }
    int openParen = parts[0].indexOf('(');
    return createDependency(parts[0].substring(0,openParen), parts[0].substring(openParen+1,parts[0].length()-1), parts[1].substring(0, parts[1].length()-1));
  }
  
  /**
   * Takes a single sentence's string from the .info file. It is multiple lines, each line is
   * a typed dependency. Convert this to a list of dependencies.
   * @param strdeps A single string with newlines, one dependency per line.
   * @return A single sentence's dependencies.
   */
  public static List<TypedDependency> stringToDependencies(String strdeps) {
    List<TypedDependency> deps = new ArrayList<TypedDependency>();
    String[] lines = strdeps.split("\n");
    for( String line : lines ) {
      line = line.trim();
      if( line.length() > 0 ) {
        TypedDependency dep = TreeOperator.stringParensToDependency(line);
        deps.add(dep);
      }
    }
    return deps;
  }
  
  public static List<List<TypedDependency>> stringsToDependencies(List<String> strdeps) {
    List<List<TypedDependency>> alldeps = new ArrayList<List<TypedDependency>>();
    for( String deps : strdeps )
      alldeps.add(stringToDependencies(deps));
    return alldeps;
  }
  
  /**
   * Given string representations of the dependency's relation, governor and dependent, create
   * the JavaNLP object.
   * @param strReln e.g., nsubj
   * @param strGov  e.g., testified-17
   * @param strDep  e.g., dealer-16
   */
  public static TypedDependency createDependency(String strReln, String strGov, String strDep) {
    if( strReln == null || strGov == null || strDep == null ) {
      System.out.println("ERROR: unknown dep format: " + strReln + " " + strGov + " " + strDep);
      System.exit(-1);
    }

    GrammaticalRelation rel = GrammaticalRelation.valueOf(strReln);

    try {
      // "happy-12"
      int hyphen = strGov.length()-2;
      while( hyphen > -1 && strGov.charAt(hyphen) != '-' ) hyphen--;
      if( hyphen < 0 ) return null;
      
      TreeGraphNode gov = new TreeGraphNode(new Word(strGov.substring(0,hyphen)));
      int end = strGov.length();
      // "happy-12'"  -- can have many apostrophes, each indicates the nth copy of this relation
      int copies = 0;
      while( strGov.charAt(end-1) == '\'' ) {
        copies++;
        end--;
      }
      if( copies > 0 ) gov.label().set(CopyAnnotation.class, copies);
      gov.label().setIndex(Integer.parseInt(strGov.substring(hyphen+1,end)));

      // "sad-3"
      hyphen = strDep.length()-2;
      while( hyphen > -1 && strDep.charAt(hyphen) != '-' ) hyphen--;
      if( hyphen < 0 ) return null;
      TreeGraphNode dep = new TreeGraphNode(new Word(strDep.substring(0,hyphen)));
      end = strDep.length();
      // "sad-3'"  -- can have many apostrophes, each indicates the nth copy of this relation
      copies = 0;
      while( strDep.charAt(end-1) == '\'' ) {
        copies++;
        end--;
      }
      if( copies > 0 ) dep.label().set(CopyAnnotation.class, copies);
      dep.label().setIndex(Integer.parseInt(strDep.substring(hyphen+1,end)));

      return new TypedDependency(rel,gov,dep);
    } catch( Exception ex ) {
      System.out.println("createDependency() error with input: " + strReln + " " + strGov + " " + strDep);
      ex.printStackTrace();
    }
    return null;
  }
  
  /**
   * Calculate the shortest dependency path from token index start to end.
   * Indices start at 1, so the first word in the sentence is index 1.
   * @return A single string representing the shortest path.
   */
  public static String dependencyPath(int start, int end, List<TypedDependency> deps) {
    List<String> paths = paths(start, end, deps, null);
    // One path? Return now!
    if( paths.size() == 1 )
      return paths.get(0);

    // More than one path. Find the shortest!
    String shortest = null;
    int dist = Integer.MAX_VALUE;
    for( String path : paths ) { 
      int count = path.split("->").length;
      count += path.split("<-").length;
      if( count < dist ) {
        dist = count;
        shortest = path;
      }
    }
    return shortest;
  }
  
  /**
   * Recursive helper function to the main dependency path. Builds all possible dependency paths between
   * two tokens.
   */
  private static List<String> paths(int start, int end, List<TypedDependency> deps, Set<Integer> visited) {
//    System.out.println("paths start=" + start + " end=" + end);
    List<String> paths = new ArrayList<String>();
    
    if( start == end ) {
      paths.add("");
      return paths;
    }

    if( visited == null ) visited = new HashSet<Integer>();
    visited.add(start);
    
    for( TypedDependency dep : deps ) {
      if( dep != null ) {
//        System.out.println("\tdep=" + dep);
//        System.out.println("\tvisited=" + visited);
        if( dep.gov().index() == start && !visited.contains(dep.dep().index()) ) {
          List<String> newpaths = paths(dep.dep().index(), end, deps, visited);
          for( String newpath : newpaths )
            paths.add(dep.reln() + "->" + newpath);
        }
        if( dep.dep().index() == start && !visited.contains(dep.gov().index()) ) {
          List<String> newpaths = paths(dep.gov().index(), end, deps, visited);
          for( String newpath : newpaths )
            paths.add(dep.reln() + "<-" + newpath);
        }
      }
    }

    return paths;
  }
  
  public static String directPath(int start, int end, List<TypedDependency> deps) {
  	String shortestPath = dependencyPath(start, end, deps);
  	if (shortestPath != null) {
  		int n = shortestPath.length();
  		for (int i = 1; i < n; i++){
  			if (shortestPath.charAt(i) == '-' && shortestPath.charAt(i-1) == '>') { // double check this
  				return null;
  				}
  			}
  		return shortestPath;
    	}
  	else return null;
  }
  
  /**
   * Given a token's index in a tree, treat that token as dominating everything under it in the 
   * syntactic tree. Return the token index span of the subtree under that token.   * 
   * @param tree The full sentence's tree.
   * @param index A token's index.
   * @return A [start,end) pair. The start index is inclusive, and the end index is exclusive.
   */
  public static Pair<Integer,Integer> tokenSpanUnderIndex(Tree tree, int index) {
  	Tree subtree = indexToSubtree(tree, index);
  	subtree = subtree.parent(tree);
  	List<Label> yield = subtree.yield();
  	if( yield != null ) {
  		CoreLabel first = (CoreLabel)yield.get(0);
  		CoreLabel last = (CoreLabel)yield.get(yield.size()-1);
  		return new Pair<Integer,Integer>(first.index(), last.index()+1);
  	}
  	else 
  		return null;
  }
}

