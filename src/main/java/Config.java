import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class Config {
  
  public static void createDefaultConfig() throws IOException {
    File configFile = new File(".git/config");
    configFile.getParentFile().mkdirs();
    
    StringBuilder config = new StringBuilder();
    config.append("[core]\n");
    config.append("\trepositoryformatversion = 0\n");
    config.append("\tfilemode = true\n");
    config.append("\tbare = false\n");
    
    Files.write(configFile.toPath(), config.toString().getBytes());
  }
  
  public static String getConfigValue(String section, String key) {
    try {
      File configFile = new File(".git/config");
      if (!configFile.exists()) {
        return null;
      }
      
      List<String> lines = Files.readAllLines(configFile.toPath());
      String currentSection = null;
      
      for (String line : lines) {
        line = line.trim();
        if (line.startsWith("[") && line.endsWith("]")) {
          currentSection = line.substring(1, line.length() - 1);
        } else if (currentSection != null && currentSection.equals(section) && line.contains("=")) {
          String[] parts = line.split("=", 2);
          if (parts.length == 2 && parts[0].trim().equals(key)) {
            return parts[1].trim();
          }
        }
      }
    } catch (Exception e) {
      // Return null on error
    }
    return null;
  }
  
  public static void setConfigValue(String section, String key, String value) throws IOException {
    File configFile = new File(".git/config");
    configFile.getParentFile().mkdirs();
    
    List<String> lines = new ArrayList<>();
    if (configFile.exists()) {
      lines = new ArrayList<>(Files.readAllLines(configFile.toPath()));
    }
    
    // Find section and update or add value
    String currentSection = null;
    boolean found = false;
    int insertIndex = -1;
    
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.startsWith("[") && line.endsWith("]")) {
        currentSection = line.substring(1, line.length() - 1);
        if (currentSection.equals(section)) {
          insertIndex = i + 1;
        }
      } else if (currentSection != null && currentSection.equals(section) && line.contains("=")) {
        String[] parts = line.split("=", 2);
        if (parts.length >= 1 && parts[0].trim().equals(key)) {
          lines.set(i, "\t" + key + " = " + value);
          found = true;
          break;
        }
      }
    }
    
    if (!found) {
      if (insertIndex == -1) {
        // Section doesn't exist, add it
        lines.add("[" + section + "]");
        lines.add("\t" + key + " = " + value);
      } else {
        // Section exists, add key-value
        lines.add(insertIndex, "\t" + key + " = " + value);
      }
    }
    
    Files.write(configFile.toPath(), lines);
  }
  
  public static String getUserName() {
    String name = getConfigValue("user", "name");
    if (name == null) {
      name = System.getProperty("user.name", "Unknown");
    }
    return name;
  }
  
  public static String getUserEmail() {
    String email = getConfigValue("user", "email");
    if (email == null) {
      email = System.getProperty("user.email", "unknown@example.com");
    }
    return email;
  }
}

