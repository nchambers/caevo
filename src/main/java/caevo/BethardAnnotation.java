package caevo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import caevo.tlink.TLink;

/**
 * A simple interface to read Steven Bethard's annotation of TimeBank.
 * It assumes the annotation is a single file.
 * 
 * @author Chambers
 */
public class BethardAnnotation {
  Map<String,List<TLink>> _docTLinks;
  
  public BethardAnnotation(String path) {
    fromFile(path);
  }

  /**
   * Get all of the TLinks in Bethard's annotation for a single TimeBank document.
   * @param docnaame The document name in TimeBank.
   * @return The list of TLinks for that document.
   */
  public List<TLink> getTLinks(String docname) {
    System.out.println("getTLinks with " + _docTLinks.size() + " docs");
    String docbase = docname;
    int index = docname.indexOf('.');
    if( index > -1 )
      docbase = docname.substring(0, index);
    
    System.out.println("basename = " + docbase);
    return _docTLinks.get(docbase);
  }

  /**
   * Read Bethard's file format into memory, creating TLink objects.
   * @param path The path to Bethard's annotation file.
   */
  public void fromFile(String path) {
    System.out.println("Loading Bethard data from " + path);
    _docTLinks = new HashMap<String,List<TLink>>();
    
    BufferedReader in = null;
    try {
      in = new BufferedReader(new FileReader(path));
      String line;
      while ((line = in.readLine()) != null) {
        if( !line.startsWith("#") ) {
          String[] parts = line.split("\\s+");
          TLink tlink = new TLink(parts[1], parts[2], parts[3]);
          
          // Add the new link to the document's list.
          List<TLink> doclinks = _docTLinks.get(parts[0]);
          if( doclinks == null ) {
            doclinks = new ArrayList<TLink>();
            _docTLinks.put(parts[0], doclinks);
          }
          doclinks.add(tlink);          
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);      
    } finally {
    	if (in != null)
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
    }
  }
}
