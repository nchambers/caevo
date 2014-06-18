package caevo.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.util.StringUtils;


public class Directory {

  public static String stripGZ(String filename) {
    if( filename.endsWith(".gz") )
      return filename.substring(0, filename.length()-3);
    else return filename;
  }
  
  /**
   * @return True if the given path is an existing file.  False otherwise.
   */
  public static boolean fileExists(String filename) {
    try {
      File file = new File(filename);
      if( file.exists() ) return true;
    } catch(Exception ex) { ex.printStackTrace(); }
    return false;
  }
  
  public static boolean isDirectory(String path) {
    try {
      File file = new File(path);
      if( file.isDirectory() ) return true;
    } catch(Exception ex) { ex.printStackTrace(); }
    return false;
  }

  /**
   * @param fileName The file to remove.
   * @return True if the file was deleted or never existed in the first place. False otherwise.
   */
  public static boolean deleteFile(String fileName) {
    if( fileName == null ) return true;
    
    // A File object to represent the filename
    File f = new File(fileName);

    // Make sure the file or directory exists and isn't write protected
    if (f.exists()) {

      if (!f.canWrite())
        throw new IllegalArgumentException("Delete: write protected: " + fileName);

      // If it is a directory, make sure it is empty
      if (f.isDirectory())
        throw new IllegalArgumentException("Delete: path is a directory: " + fileName);

      // Attempt to delete it
      return f.delete();
    }
    return true;
  }
  
  /**
   * Read a directory and return all files.
   */
  public static List<String> getFiles(String dirPath) {
    return getFiles(new File(dirPath));
  }
  public static List<String> getFiles(File dir) {
    if( dir.isDirectory() ) {
      List<String> files = new LinkedList<String>();
      for( String file : dir.list() ) {
        if( !file.startsWith(".") )
          files.add(file);
      }
      return files;
    }

    return null;
  }

  /**
   * Read a directory and return all files in sorted order.
   */
  public static String[] getFilesSorted(String dirPath) {
    List<String> unsorted = getFiles(dirPath);

    if( unsorted == null )
      System.out.println("ERROR: Directory.getFilesSorted() path is not known: " + dirPath);
    
    String[] sorted = new String[unsorted.size()];
    sorted = unsorted.toArray(sorted);
    Arrays.sort(sorted);

    return sorted;
  }

  /**
   * Finds the closest file in name in the given directory based on string
   * edit distance.
   * @param name The name that we want to find.
   * @param directory A directory.
   * @return A file in the directory.
   */
  public static String nearestFile(String name, String directory) {
      return nearestFile(name, directory, null);
  }

    public static String nearestFile(String name, String directory, String badSubstring) {
    name = name.toLowerCase();
    File dir = new File(directory);
    if( dir.isDirectory() ) {
      float best = Float.MAX_VALUE;
      String bestName = null;
      for( String file : getFiles(dir) ) {
        file = file.toLowerCase();
        // edit distance?
        float editscore = StringUtils.editDistance(name, file);
	//        System.out.println("name=" + name + "\tsimilar file " + file + " score = " + editscore);
        if( editscore < best && (badSubstring == null || !file.contains(badSubstring)) ) {
          best = editscore;
          bestName = file;
        }
      }
      return bestName;
    } else {
      System.out.println("(Directory) Not a directory: " + dir);
      System.exit(-1);
    }
    return null;
  }
  
  public static String lastSubdirectory(String path) {
    if( path == null ) return null;
    int last = path.lastIndexOf(File.separator);
    if( last > -1 )
      path = path.substring(0, last);
    else return path;
    
    last = path.lastIndexOf(File.separator);
    if( last > -1 )
      path = path.substring(last+1);
    
    return path;
  }
  
  /**
   * Create a file and put the given string into it.
   * @param path Path to create the file.
   * @param str The string to write.
   */
  public static void stringToFile(String path, String str) {
    try {
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(path)));
      writer.write(str);
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }
}
