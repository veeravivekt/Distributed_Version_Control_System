import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class Index {
  
  public static void updateIndex(String filePath, String hash, String mode) throws IOException {
    Map<String, IndexEntry> indexEntries = readIndex();
    indexEntries.put(filePath, new IndexEntry(mode, hash, filePath));
    writeIndex(indexEntries);
  }
  
  public static Map<String, IndexEntry> readIndex() throws IOException {
    Map<String, IndexEntry> entries = new LinkedHashMap<>();
    File indexFile = new File(".git/index");
    
    if (!indexFile.exists()) {
      return entries;
    }
    
    try {
      List<String> lines = Files.readAllLines(indexFile.toPath());
      for (String line : lines) {
        if (line.trim().isEmpty()) continue;
        String[] parts = line.split(" ", 3);
        if (parts.length == 3) {
          entries.put(parts[2], new IndexEntry(parts[0], parts[1], parts[2]));
        }
      }
    } catch (Exception e) {
      return entries;
    }
    
    return entries;
  }
  
  public static void writeIndex(Map<String, IndexEntry> entries) throws IOException {
    File indexFile = new File(".git/index");
    indexFile.getParentFile().mkdirs();
    
    List<String> lines = new ArrayList<>();
    for (IndexEntry entry : entries.values()) {
      lines.add(entry.mode + " " + entry.hash + " " + entry.path);
    }
    Files.write(indexFile.toPath(), lines);
  }
  
  public static class IndexEntry {
    public String mode;
    public String hash;
    public String path;
    
    public IndexEntry(String mode, String hash, String path) {
      this.mode = mode;
      this.hash = hash;
      this.path = path;
    }
  }
}

