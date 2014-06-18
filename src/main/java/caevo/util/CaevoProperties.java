// Copyright 2010-2012 Benjamin Van Durme. All rights reserved.
// This software is released under the 2-clause BSD license.
// See jerboa/LICENSE, or http://cs.jhu.edu/~vandurme/jerboa/LICENSE

// Benjamin Van Durme, vandurme@cs.jhu.edu, 26 Oct 2010

package caevo.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Benjamin Van Durme
 * 
 *         A utility wrapper around {@link java.util.Properties}, supporting type specific querying
 *         on property values, and throwing Exception when properties are not found in cases with no
 *         default value.
 *         <p>
 *         Uses System level properties if they exist, then checks the property file that was loaded
 *         into this object as backup (so command line specified properties can supersede a
 *         configuration file).
 *         <p>
 *         Contains basic functionality for referencing other system properties, meant for things
 *         like setting a root directory just once, and making other values relative to that. Syntax
 *         is: (via example)
 *         <p>
 *         ROOT = /home/joe/project Data = {ROOT}/data
 */
public class CaevoProperties {

  private static final Logger logger = Logger.getLogger(CaevoProperties.class.getName());

  static Properties properties;
  static boolean isLoaded = false;
  static Hashtable<String, String> variables;

  static Pattern variablePattern = Pattern.compile("\\{[^\\\\}]+\\}");
  
  //static {
  //  load();
  //}

  private static String parsePropertyValue(String value) throws IOException {
    String group, replacement;
    Matcher m = variablePattern.matcher(value);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      group = m.group();
      group = group.substring(1, group.length() - 1);
      replacement = CaevoProperties.getString(group, null);
      if (replacement != null)
        m.appendReplacement(sb, replacement);
      else {
        logger
            .warning("Cannot parse property [" + value + "], as [" + group + "] does not resolve");
        return null;
      }
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * Calls {@link #load(String) load()} with the property
   * JerboaProperties.filename as the argument.
   * 
   * @throws IOException
   */
  public static void load() throws IOException {
    String filename = System.getProperty("props");
    logger.info("Loading properties file: " + filename);
    if (filename != null) 
      load(filename);

    isLoaded = true;
  }

  /**
   * Loads a static {@link Properties} object via {@link FileManager#getReader(String)}.
   * 
   * @param filename - the name of the file to be loaded in the properties object
   * @throws IOException
   */
  public static void load(String filename) throws IOException {
    logger.config("Reading JerboaProperty file [" + filename + "]");
    if (properties == null) properties = new java.util.Properties();
    // properties.load(new BufferedReader(new FileReader(filename)));
    properties.load(FileManager.getReader(filename));
    isLoaded = true;
  }
  
  public static boolean hasProperty(String key) {
    return System.getProperty(key) != null || (properties != null && properties.containsKey(key));
  }

  public static double getDouble(String key) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null)
      throw new IOException("Key not found in property specification: [" + key + "]");
    if ((value = parsePropertyValue(value)) == null)
      throw new IOException("Key not resolvable in property specification: [" + key + "]");

    else
      return Double.parseDouble(value);
  }

  public static double getDouble(String key, double defaultValue) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null) {
      logger.config("Returning default value for " + key + " : " + defaultValue);
      return defaultValue;
    }
    if ((value = parsePropertyValue(value)) == null) {
      logger.config("Key not fully resolvable, returning default value for " + key + " : "
          + defaultValue);
      return defaultValue;
    } else
      return Double.parseDouble(value);
  }

  public static long getLong(String key) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null)
      throw new IOException("Key not found in property specification: [" + key + "]");
    if ((value = parsePropertyValue(value)) == null)
      throw new IOException("Key not resolvable in property specification: [" + key + "]");

    return Long.parseLong(value);
  }

  public static long getLong(String key, long defaultValue) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null) {
      logger.config("Returning default value for " + key + " : " + defaultValue);
      return defaultValue;
    }
    if ((value = parsePropertyValue(value)) == null) {
      logger.config("Key not fully resolvable, returning default value for " + key + " : "
          + defaultValue);
      return defaultValue;
    } else
      return Long.parseLong(value);
  }

  public static int getInt(String key) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null)
      throw new IOException("Key not found in property specification: [" + key + "]");
    if ((value = parsePropertyValue(value)) == null)
      throw new IOException("Key not resolvable in property specification: [" + key + "]");

    logger.config("Returning value for " + key + " : " + value);
    return Integer.parseInt(value);
  }

  public static int getInt(String key, int defaultValue) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null) {
      logger.config("Returning default value for " + key + " : " + defaultValue);
      return defaultValue;
    }
    if ((value = parsePropertyValue(value)) == null) {
      logger.config("Key not fully resolvable, returning default value for " + key + " : "
          + defaultValue);
      return defaultValue;
    } else
      return Integer.parseInt(value);
  }

  public static String getString(String key, String defaultValue) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null) {
      logger.config("Returning default value for " + key + " : " + defaultValue);
      return defaultValue;
    }
    if ((value = parsePropertyValue(value)) == null) {
      logger.config("Key not fully resolvable, returning default value for " + key + " : "
          + defaultValue);
      return defaultValue;
    } else
      return value;
  }

  public static String getString(String key) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null)
      throw new IOException("Key not found in property specification: [" + key + "]");
    if ((value = parsePropertyValue(value)) == null)
      throw new IOException("Key not resolvable in property specification: [" + key + "]");

    return value;
  }

  public static String[] getStrings(String key, String[] defaultValue) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null) {
      logger.config("Returning default value for " + key + " : " + Arrays.toString(defaultValue));
      return defaultValue;
    }
    if ((value = parsePropertyValue(value)) == null) {
      logger.config("Key not fully resolvable, returning default value for " + key + " : "
          + Arrays.toString(defaultValue));
      return defaultValue;
    } else
      return value.split("\\s");
  }

  public static String[] getStrings(String key) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null)
      throw new IOException("Key not found in property specification: [" + key + "]");
    if ((value = parsePropertyValue(value)) == null)
      throw new IOException("Key not resolvable in property specification: [" + key + "]");

    return value.split("\\s");
  }

  public static boolean getBoolean(String key, boolean defaultValue) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null) {
      logger.config("Returning default value for " + key + " : " + defaultValue);
      return defaultValue;
    }
    if ((value = parsePropertyValue(value)) == null) {
      logger.config("Key not fully resolvable, returning default value for " + key + " : "
          + defaultValue);
      return defaultValue;
    } else
      return Boolean.valueOf(value);
  }

  public static boolean getBoolean(String key) throws IOException {
    if (!isLoaded) load();

    String value = System.getProperty(key);
    if (value == null && properties != null) value = properties.getProperty(key);
    if (value == null)
      throw new IOException("Key not found in property specification: [" + key + "]");
    if ((value = parsePropertyValue(value)) == null)
      throw new IOException("Key not resolvable in property specification: [" + key + "]");
    return Boolean.valueOf(value);
  }
}