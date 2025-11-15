import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Index {
  private static final String INDEX_SIGNATURE = "DIRC";
  private static final int INDEX_VERSION = 2;
  
  public static void updateIndex(String filePath, String hash, String mode) throws IOException {
    Map<String, IndexEntry> indexEntries = readIndex();
    indexEntries.put(filePath, new IndexEntry(mode, hash, filePath));
    writeIndex(indexEntries);
  }
  
  public static void removeIndexEntry(String filePath) throws IOException {
    Map<String, IndexEntry> indexEntries = readIndex();
    indexEntries.remove(filePath);
    writeIndex(indexEntries);
  }
  
  public static void clearIndex() throws IOException {
    writeIndex(new LinkedHashMap<>());
  }
  
  public static Map<String, IndexEntry> readIndex() throws IOException {
    Map<String, IndexEntry> entries = new LinkedHashMap<>();
    File indexFile = new File(".git/index");
    
    if (!indexFile.exists()) {
      return entries;
    }
    
    try (DataInputStream dis = new DataInputStream(new FileInputStream(indexFile))) {
      // Read header
      byte[] signature = new byte[4];
      dis.readFully(signature);
      String sig = new String(signature);
      if (!sig.equals(INDEX_SIGNATURE)) {
        // Try reading as text format for backward compatibility
        return readIndexTextFormat(indexFile);
      }
      
      Integer.reverseBytes(dis.readInt()); // version
      int entryCount = Integer.reverseBytes(dis.readInt());
      
      // Read entries
      for (int i = 0; i < entryCount; i++) {
        IndexEntry entry = readIndexEntry(dis);
        entries.put(entry.path, entry);
      }
      
      // Skip extensions (if any) - simplified, just skip to checksum
      // In full implementation, would parse extensions
      
      // Read and verify checksum (20 bytes)
      byte[] checksum = new byte[20];
      dis.readFully(checksum);
      
    } catch (Exception e) {
      // If binary format fails, try text format for backward compatibility
      return readIndexTextFormat(indexFile);
    }
    
    return entries;
  }
  
  private static IndexEntry readIndexEntry(DataInputStream dis) throws IOException {
    // Read ctime (8 bytes) - seconds (4) + nanoseconds (4)
    dis.readLong();
    // Read mtime (8 bytes)
    dis.readLong();
    // Read dev (4 bytes)
    dis.readInt();
    // Read ino (4 bytes)
    dis.readInt();
    // Read mode (4 bytes)
    int mode = Integer.reverseBytes(dis.readInt());
    // Read uid (4 bytes)
    dis.readInt();
    // Read gid (4 bytes)
    dis.readInt();
    // Read file size (4 bytes)
    Integer.reverseBytes(dis.readInt());
    // Read SHA-1 hash (20 bytes)
    byte[] hashBytes = new byte[20];
    dis.readFully(hashBytes);
    String hash = bytesToHex(hashBytes);
    // Read flags (2 bytes)
    int flags = Short.reverseBytes(dis.readShort());
    // Path length is in lower 12 bits of flags
    int pathLength = flags & 0xFFF;
    // Read path (null-terminated, padded to 8-byte boundary)
    byte[] pathBytes = new byte[pathLength];
    dis.readFully(pathBytes);
    String path = new String(pathBytes, 0, pathLength - 1); // Exclude null terminator
    
    // Skip padding to 8-byte boundary
    int padding = (62 + pathLength) % 8;
    if (padding != 0) {
      dis.skipBytes(8 - padding);
    }
    
    return new IndexEntry(String.valueOf(mode), hash, path);
  }
  
  public static void writeIndex(Map<String, IndexEntry> entries) throws IOException {
    File indexFile = new File(".git/index");
    indexFile.getParentFile().mkdirs();
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    
    // Write header
    dos.write(INDEX_SIGNATURE.getBytes());
    dos.writeInt(Integer.reverseBytes(INDEX_VERSION));
    dos.writeInt(Integer.reverseBytes(entries.size()));
    
    // Write entries
    for (IndexEntry entry : entries.values()) {
      writeIndexEntry(dos, entry);
    }
    
    // Calculate and write checksum
    byte[] indexData = baos.toByteArray();
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] checksum = md.digest(indexData);
      dos.write(checksum);
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-1 algorithm not available", e);
    }
    
    dos.close();
    Files.write(indexFile.toPath(), baos.toByteArray());
  }
  
  private static void writeIndexEntry(DataOutputStream dos, IndexEntry entry) throws IOException {
    // Write ctime (8 bytes) - seconds and nanoseconds combined as single long
    dos.writeLong(0);
    // Write mtime (8 bytes) - seconds and nanoseconds combined as single long
    dos.writeLong(0);
    // Write dev (4 bytes)
    dos.writeInt(0);
    // Write ino (4 bytes)
    dos.writeInt(0);
    // Write mode (4 bytes)
    int mode = Integer.parseInt(entry.mode);
    dos.writeInt(Integer.reverseBytes(mode));
    // Write uid (4 bytes)
    dos.writeInt(0);
    // Write gid (4 bytes)
    dos.writeInt(0);
    // Write file size (4 bytes) - we don't store this, use 0
    dos.writeInt(0);
    // Write SHA-1 hash (20 bytes)
    byte[] hashBytes = hexToBytes(entry.hash);
    dos.write(hashBytes);
    // Write flags (2 bytes) - path length in lower 12 bits
    int pathLength = entry.path.length() + 1; // +1 for null terminator
    short flags = (short) pathLength;
    dos.writeShort(Short.reverseBytes(flags));
    // Write path (null-terminated)
    dos.write(entry.path.getBytes());
    dos.writeByte(0); // null terminator
    
    // Pad to 8-byte boundary
    int entrySize = 62 + pathLength;
    int padding = entrySize % 8;
    if (padding != 0) {
      for (int i = 0; i < (8 - padding); i++) {
        dos.writeByte(0);
      }
    }
  }
  
  // Backward compatibility: read text format
  private static Map<String, IndexEntry> readIndexTextFormat(File indexFile) throws IOException {
    Map<String, IndexEntry> entries = new LinkedHashMap<>();
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
      // Return empty map if can't read
    }
    return entries;
  }
  
  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
  
  private static byte[] hexToBytes(String hex) {
    byte[] bytes = new byte[hex.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
    }
    return bytes;
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
