import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class ObjectStore {
  
  public static String sha1Hash(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] hash = md.digest(data);
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] readObject(String hash) throws IOException {
    String dirHash = hash.substring(0, 2);
    String fileHash = hash.substring(2);
    File objectFile = new File(".git/objects/" + dirHash + "/" + fileHash);
    
    if (!objectFile.exists()) {
      throw new IOException("Object not found: " + hash);
    }
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (InflaterInputStream iis = new InflaterInputStream(new FileInputStream(objectFile))) {
      byte[] buffer = new byte[8192];
      int len;
      while ((len = iis.read(buffer)) != -1) {
        baos.write(buffer, 0, len);
      }
    }
    return baos.toByteArray();
  }

  public static void writeObject(String hash, byte[] data) throws IOException {
    String dirHash = hash.substring(0, 2);
    String fileHash = hash.substring(2);
    File dir = new File(".git/objects/" + dirHash);
    dir.mkdirs();
    File objectFile = new File(dir, fileHash);
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
      dos.write(data);
    }
    
    Files.write(objectFile.toPath(), baos.toByteArray());
  }

  public static String storeObject(String type, byte[] content) throws IOException {
    String header = type + " " + content.length + "\0";
    byte[] fullData = new byte[header.length() + content.length];
    System.arraycopy(header.getBytes(), 0, fullData, 0, header.length());
    System.arraycopy(content, 0, fullData, header.length(), content.length);
    
    String hash = sha1Hash(fullData);
    writeObject(hash, fullData);
    return hash;
  }

  public static ObjectInfo parseObject(String hash) throws IOException {
    byte[] data = readObject(hash);
    int nullIndex = -1;
    for (int i = 0; i < data.length; i++) {
      if (data[i] == 0) {
        nullIndex = i;
        break;
      }
    }
    if (nullIndex == -1) {
      throw new IOException("Invalid object format");
    }
    
    String header = new String(data, 0, nullIndex);
    String[] parts = header.split(" ");
    String type = parts[0];
    byte[] content = java.util.Arrays.copyOfRange(data, nullIndex + 1, data.length);
    
    return new ObjectInfo(type, content);
  }
  
  public static class ObjectInfo {
    public String type;
    public byte[] content;
    
    public ObjectInfo(String type, byte[] content) {
      this.type = type;
      this.content = content;
    }
  }
}

