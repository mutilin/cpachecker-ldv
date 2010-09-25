package org.sosy_lab.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

import com.google.common.base.Preconditions;

/**
 * Provides helper functions for file access.
 */
public final class Files {

  private Files() { /* utility class */ }
  
  /**
   * Creates a temporary file with an optional content. The file is marked for
   * deletion when the Java VM exits.
   * @param  prefix     The prefix string to be used in generating the file's
   *                    name; must be at least three characters long
   * @param  suffix     The suffix string to be used in generating the file's
   *                    name; may be <code>null</code>, in which case the
   *                    suffix <code>".tmp"</code> will be used
   * @param content The content to write (may be null).
   *
   * @throws  IllegalArgumentException
   *          If the <code>prefix</code> argument contains fewer than three
   *          characters
   * @throws  IOException  If a file could not be created
   */
  public static File createTempFile(String prefix, String suffix, String content) throws IOException {
    File file = File.createTempFile(prefix, suffix);
    file.deleteOnExit();
    
    if (content != null && !content.isEmpty()) {
      Writer writer = new OutputStreamWriter(new FileOutputStream(file));
      try {
        writer.write(content);
      
      } catch (Exception e) {
        file.delete();
        
      } finally {
        try {
          writer.close();
        } catch (IOException e) {
          // ignore here
        }
      }
    }
    return file;
  }
    
  /**
   * Checks if the parent directory of a file exists and creates it if necessary.
   * @param file The file whose directory should be checked.
   * @throws IOException If the directory does not exist and it's creation fails.
   */
  public static void ensureParentDirectoryExists(File file) throws IOException {
    File directory = file.getParentFile();
    if ((directory != null) && !directory.exists()) {
      if (!directory.mkdirs()) {
        throw new IOException("Could not create directory " + directory + " for output file!");
      }
    }
  }
  
  /**
   * Writes content to a file.
   * @param file The file.
   * @param content The content which shall be written.
   * @param append Whether to append or to overwrite.
   * @throws IOException
   */
  public static void writeFile(File file, Object content, boolean append) throws IOException {
    Preconditions.checkNotNull(file);
    Preconditions.checkNotNull(content);
    
    ensureParentDirectoryExists(file);

    Writer writer = new OutputStreamWriter(new FileOutputStream(file, append));
    try {
      writer.write(content.toString());
          
    } finally {
      try {
        writer.close();
      } catch (IOException e) {
        // ignore here
      }
    }
  }
  
  /**
   * Reads the complete content of a file into a string.
   * @param file The file.
   * @return The content of the file.
   * @throws IOException
   */
  public static String readFile(File file) throws IOException {
    Preconditions.checkNotNull(file);
    checkReadableFile(file);
    
    StringBuilder data = new StringBuilder((int)file.length());
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
    
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        data.append(line);
        data.append('\n');
      }
      return data.toString();
      
    } finally {
      try {
        reader.close();
      } catch (IOException e) {
        // ignore here
      }
    }
  }

  /**
   * Verifies if a file exists, is a normal file and is readable. If this is not
   * the case, a FileNotFoundException with a nice message is thrown.
   * 
   * @param path The path (may be null).
   * @param name The name.
   * @throws FileNotFoundException If one of the conditions is not true.
   */
  public static void checkReadableFile(String path, String name) throws FileNotFoundException {
    Preconditions.checkNotNull(name);
    checkReadableFile(new File(path, name));
  }
    
  /**
   * Verifies if a file exists, is a normal file and is readable. If this is not
   * the case, a FileNotFoundException with a nice message is thrown.
   * 
   * @param file The file to check.
   * @throws FileNotFoundException If one of the conditions is not true.
   */
  public static void checkReadableFile(File file) throws FileNotFoundException {
    Preconditions.checkNotNull(file);
    
    if (!file.exists()) {
      throw new FileNotFoundException("File " + file.getAbsolutePath() + " does not exist!");
    }

    if (!file.isFile()) {
      throw new FileNotFoundException("File " + file.getAbsolutePath() + " is not a normal file!");
    }

    if (!file.canRead()) {
      throw new FileNotFoundException("File " + file.getAbsolutePath() + " is not readable!");
    }
  }
}