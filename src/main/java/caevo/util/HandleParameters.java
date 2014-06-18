package caevo.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to read a program's command-line arguments and provide easy lookup.
 */
public class HandleParameters {
  Map<String,String> params;
  List<String> values;

  public HandleParameters(String[] args) {
    params = new HashMap<String, String>();
    values = new ArrayList<String>();
    parseArgs(args);
  }
  
  public HandleParameters(String filepath) {
    params = new HashMap<String, String>();
    values = new ArrayList<String>();
    fromFile(filepath);
  }

  /**
   * Main function that parses the args into flags and values.
   * @param args one flag or one value per cell in the array.
   */
  private void parseArgs(String args[]) {
    String param = null;
    int i = 0;
    while( i < args.length ) {
      String arg = args[i];

      // flag
      if( arg.startsWith("-") ) {
        // if param is set, then we had a value-less parameter
        if( param != null ) {
          params.put(param, "");
        }
        param = args[i];
        // if we have values set, then we had a non-param value earlier
        if( values.size() > 0 ) {
          System.out.println("Bad program parameter: " + values.get(0));
          System.exit(1);
        }
      }
      else if( param == null ) {
        values.add(arg);
      }
      else {
        params.put(param, arg);
        param = null;
      }
      i++;
    }

    // Cleanup hanging flag.
    if( param != null ) params.put(param, "");
  }
  
  /**
   * Reads flags from a file on disc (like a properties file).
   * @param path
   */
  public void fromFile(String path) {
    System.out.println("Loading parameters from " + path);
    List<String> args = new ArrayList<String>();
    
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line = in.readLine();
      while( line != null ) {
//        System.out.println("line: " + line);
        // skip lines that are commented out with leading # characters
        if( !line.matches("\\s*\\#.*") ) {
//          System.out.println(" - processing");
          if( line.length() > 1 ) {
            String[] parts = line.split("\\s+");
            for( String part : parts )
              args.add(part);
          }
        }
//        System.out.println(" - done");
        line = in.readLine();
      }
      in.close();

      // Parse the arguments.
      String[] arr = new String[args.size()];
      arr = args.toArray(arr);
      parseArgs(arr);
    } catch (Exception e) { e.printStackTrace(); }
  }
  
  /**
   * @returns The value listed after a flag.
   */
  public boolean hasFlag(String flag) {
    if( params.containsKey(flag) ) return true;
    else return false;
  }

  /**
   * @returns The value listed after a flag.
   */
  public String get(String flag) {
    return params.get(flag);
  }

}