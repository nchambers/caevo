package caevo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * Timebank Corpus file that stores in an easier to read format, all the sentences and docs
 * from the TimeBank corpus, as well as event information on a per-sentence basis.
 * @author chambers
 */
public class SieveDocuments {
	private List<SieveDocument> documents;
	private Map<String,SieveDocument> nameToDocument;
	
	// XML Constants
	public static String INFO_NS = "http://chambers.com/corpusinfo";
  public static String SENT_ELEM = "sentence";
  public static String TOKENS_ELEM = "tokens";
  public static String TOKEN_ELEM = "t";
  public static String PARSE_ELEM = "parse";
  public static String DEPS_ELEM = "deps";
  public static String ENTRY_ELEM = "entry";
  public static String EVENTS_ELEM = "events";
  public static String TIMEXES_ELEM = "timexes";
  public static String SID_ELEM = "sid";
  public static String FILE_ELEM = "file";
  public static String FILENAME_ELEM = "name";
    

  public SieveDocuments() {
  }

  public SieveDocuments(String filepath) {
	  readFromXML(filepath);
	}
  
  /**
   * @return A Vector of String file names
   */
  public Set<String> getFileNames() {
  	if( nameToDocument != null )
  		return nameToDocument.keySet();
  	else return null;
  }
  
  public List<SieveDocument> getDocuments() {
  	return documents;
  }
  
  public SieveDocument getDocument(String docname) {
  	if( nameToDocument != null )
  		return nameToDocument.get(docname);
  	else return null;
  }

  public void addDocument(SieveDocument doc) {
  	if( documents == null ) documents = new ArrayList<SieveDocument>();
	if( nameToDocument == null ) nameToDocument = new HashMap<String,SieveDocument>();
  	documents.add(doc);
	nameToDocument.put(doc.getDocname(), doc);
  }
  
  public void readFromXML(String path) {
    readFromXML(new File(path));
  }
  
  public void readFromXML(File file) {
  	// Reset the documents list.
  	if( documents == null ) documents = new ArrayList<SieveDocument>();
  	else documents.clear();
  	if( nameToDocument == null ) nameToDocument = new HashMap<String,SieveDocument>();
  	else nameToDocument.clear();
  	
  	// Read the XML file.
    SAXBuilder builder = new SAXBuilder();
    try {
      Namespace ns = Namespace.getNamespace(INFO_NS);
      Document jdomDoc = builder.build(file);

      Element root = jdomDoc.getRootElement();
      List children = root.getChildren(FILE_ELEM, ns);
      System.out.println("Got " + children.size() + " file elements.");
      for( Object obj : children ) {
      	SieveDocument doc = SieveDocument.fromXML((Element)obj); 
      	documents.add(doc);
      	nameToDocument.put(doc.getDocname(), doc);
      }      
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Clear out all TLinks in all of the documents.
   */
  public void removeAllTLinks() {
  	if( documents != null )
  		for( SieveDocument doc : documents )
  			doc.removeTlinks();
  }
  
  /**
   * Create an XML Document out of the sieve documents.
   * @return An XML Document
   */
  public Document toXML() {
    Namespace ns = Namespace.getNamespace(INFO_NS);
    Document jdomDoc = new Document();
    Element root = new Element("root",ns);
    jdomDoc.setRootElement(root);

    for( SieveDocument doc : documents )
    	root.addContent(doc.toXML());
    
    return jdomDoc;
  }

  public void writeToXML(String path) {
  	writeToXML(new File(path));
  }
  public void writeToXML(File file) {
    try {
      FileOutputStream out = new FileOutputStream(file);
      XMLOutputter op = new XMLOutputter(Format.getPrettyFormat());
      Document jdomDoc = toXML();
      op.output(jdomDoc, out);
      out.flush();
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  public void outputMarkedUp(String dirpath) {
    // Create the directory.
    try {
      System.out.println("Creating directory: " + dirpath);
      File dir = new File(dirpath);
      dir.mkdir();
    } catch( Exception ex ) { ex.printStackTrace(); }

    if( documents != null )
    	for( SieveDocument doc : documents )
    		doc.outputMarkedUp(dirpath);
  }

  
  /**
   * Debugging and statistics use only.
   */
  private int numPossiblePairs(int num1) {
  	int num = 0;
  	for( int x = num1-1; x > 0; x-- ) {
  		num += x;
  	}
  	return num;
  }
  
  public void countEventPairLinks() {
  	int total2 = 0;
  	int total3 = 0;
  	int docs = 0;
    for( String docname : getFileNames() ) {
    	SieveDocument doc = nameToDocument.get(docname); 
      List<SieveSentence> sentences = doc.getSentences();
      for( int sid = 0; sid < sentences.size()-1; sid++ ) {
      	SieveSentence sent = sentences.get(sid);
      	int num = numPossiblePairs(sent.events().size());
      	int num2 = sent.events().size() * sentences.get(sid+1).events().size();
      	int num3 = 0;
      	if( sid < sentences.size()-2 )
      		num3 = sent.events().size() * sentences.get(sid+2).events().size();
      	total2 += num + num2;
      	total3 += num + num2 + num3;
      }
      docs++;
    }
    System.out.println("checked " + docs + " docs.");
    System.out.println("total event pairs between 2 sentences: " + total2);
    System.out.println("total event pairs between 3 sentences: " + total3);
  }
  
  
  /**
   * This main function is only here to print out automatic time-time links!
   */
  public static void main(String[] args) {
    if( args.length < 1 ) {
      System.err.println("InfoFile <infopath> <markup-out-dir> markup");
    }

    else if( args[args.length-1].equals("markup") ) {
    	SieveDocuments docs = new SieveDocuments();
      docs.readFromXML(args[0]);
      docs.outputMarkedUp(args[1]);
    }

    else if( args[args.length-1].equals("inout") ) {
    	SieveDocuments docs = new SieveDocuments();
      docs.readFromXML(args[0]);
      docs.writeToXML(args[1]);
    }
    
    // InfoFile <info> count
    else if( args[args.length-1].equals("count") ) {
    	SieveDocuments docs = new SieveDocuments();
      docs.readFromXML(args[0]);
//      info.countEventDCTLinks();
      docs.countEventPairLinks();
    }
  }

}	
