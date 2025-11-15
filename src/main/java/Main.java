import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class Main {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Usage: git <command> [options]");
      return;
    }
    
    final String command = args[0];
    
    try {
      switch (command) {
        case "init" -> init();
        case "cat-file" -> catFile(args);
        case "hash-object" -> hashObject(args);
        case "ls-tree" -> lsTree(args);
        case "write-tree" -> writeTree();
        case "commit-tree" -> commitTree(args);
        case "commit" -> commit(args);
        case "log" -> log(args);
        case "add" -> add(args);
        case "checkout" -> checkout(args);
        case "status" -> status();
        case "branch" -> branch(args);
        case "tag" -> tag(args);
        default -> System.out.println("Unknown command: " + command);
      }
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  // ========== INIT ==========
  private static void init() throws IOException {
    final File root = new File(".git");
    new File(root, "objects").mkdirs();
    new File(root, "refs/heads").mkdirs();
    new File(root, "refs/tags").mkdirs();
    final File head = new File(root, "HEAD");
    
    head.createNewFile();
    Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
    System.out.println("Initialized git directory");
  }

  // ========== UTILITY METHODS ==========
  
  private static String storeObject(String type, byte[] content) throws IOException {
    return ObjectStore.storeObject(type, content);
  }

  // ========== CAT-FILE ==========
  private static void catFile(String[] args) throws IOException {
    if (args.length < 3) {
      System.out.println("Usage: git cat-file -p <hash>");
      return;
    }
    
    String flag = args[1];
    String hash = args[2];
    
    if (!flag.equals("-p")) {
      System.out.println("Only -p flag is supported");
      return;
    }
    
    ObjectStore.ObjectInfo obj = ObjectStore.parseObject(hash);
    
    switch (obj.type) {
      case "blob" -> System.out.print(new String(obj.content));
      case "tree" -> printTree(obj.content);
      case "commit" -> System.out.print(new String(obj.content));
      default -> System.out.println("Unknown object type: " + obj.type);
    }
  }

  private static void printTree(byte[] treeData) {
    int pos = 0;
    while (pos < treeData.length) {
      // Read mode
      int modeEnd = pos;
      while (modeEnd < treeData.length && treeData[modeEnd] != ' ') {
        modeEnd++;
      }
      String mode = new String(treeData, pos, modeEnd - pos);
      pos = modeEnd + 1;
      
      // Read name
      int nameEnd = pos;
      while (nameEnd < treeData.length && treeData[nameEnd] != 0) {
        nameEnd++;
      }
      String name = new String(treeData, pos, nameEnd - pos);
      pos = nameEnd + 1;
      
      // Read hash (20 bytes)
      StringBuilder hash = new StringBuilder();
      for (int i = 0; i < 20; i++) {
        hash.append(String.format("%02x", treeData[pos + i]));
      }
      pos += 20;
      
      // Determine type
      String type = mode.equals("40000") ? "tree" : "blob";
      
      System.out.println(mode + " " + type + " " + hash + "\t" + name);
    }
  }

  // ========== HASH-OBJECT ==========
  private static void hashObject(String[] args) throws IOException {
    if (args.length < 2) {
      System.out.println("Usage: git hash-object <file>");
      return;
    }
    
    String filePath = args[1];
    File file = new File(filePath);
    if (!file.exists()) {
      throw new IOException("File not found: " + filePath);
    }
    
    byte[] content = Files.readAllBytes(file.toPath());
    String hash = storeObject("blob", content);
    System.out.println(hash);
  }

  // ========== LS-TREE ==========
  private static void lsTree(String[] args) throws IOException {
    if (args.length < 2) {
      System.out.println("Usage: git ls-tree <hash> [--name-only]");
      return;
    }
    
    String hash = args[1];
    boolean nameOnly = args.length > 2 && args[2].equals("--name-only");
    
    ObjectStore.ObjectInfo obj = ObjectStore.parseObject(hash);
    if (!obj.type.equals("tree")) {
      throw new IOException("Not a tree object: " + hash);
    }
    
    if (nameOnly) {
      printTreeNames(obj.content);
    } else {
      printTree(obj.content);
    }
  }

  private static void printTreeNames(byte[] treeData) {
    int pos = 0;
    while (pos < treeData.length) {
      // Skip mode
      while (pos < treeData.length && treeData[pos] != ' ') {
        pos++;
      }
      pos++; // Skip space
      
      // Read name
      int nameEnd = pos;
      while (nameEnd < treeData.length && treeData[nameEnd] != 0) {
        nameEnd++;
      }
      String name = new String(treeData, pos, nameEnd - pos);
      pos = nameEnd + 1;
      
      // Skip hash (20 bytes)
      pos += 20;
      
      System.out.println(name);
    }
  }

  // ========== WRITE-TREE ==========
  private static String writeTree() throws IOException {
    return writeTreeRecursive(new File("."), "");
  }

  private static String writeTreeRecursive(File dir, String prefix) throws IOException {
    List<TreeEntry> entries = new ArrayList<>();
    File[] files = dir.listFiles();
    
    if (files == null) {
      throw new IOException("Cannot read directory: " + dir);
    }
    
    // Filter out .git directory
    List<File> filteredFiles = new ArrayList<>();
    for (File file : files) {
      if (!file.getName().equals(".git")) {
        filteredFiles.add(file);
      }
    }
    
    // Sort entries
    filteredFiles.sort(Comparator.comparing(File::getName));
    
    for (File file : filteredFiles) {
      String name = file.getName();
      String path = prefix.isEmpty() ? name : prefix + "/" + name;
      
      if (file.isDirectory()) {
        String treeHash = writeTreeRecursive(file, path);
        entries.add(new TreeEntry("40000", name, treeHash));
      } else {
        byte[] content = Files.readAllBytes(file.toPath());
        String blobHash = storeObject("blob", content);
        entries.add(new TreeEntry("100644", name, blobHash));
      }
    }
    
    // Build tree object
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    for (TreeEntry entry : entries) {
      baos.write(entry.mode.getBytes());
      baos.write(' ');
      baos.write(entry.name.getBytes());
      baos.write(0);
      // Convert hex hash to bytes
      byte[] hashBytes = hexToBytes(entry.hash);
      baos.write(hashBytes);
    }
    
    return storeObject("tree", baos.toByteArray());
  }

  private static byte[] hexToBytes(String hex) {
    byte[] bytes = new byte[hex.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
    }
    return bytes;
  }

  // ========== COMMIT-TREE ==========
  private static void commitTree(String[] args) throws IOException {
    String treeHash = null;
    String parentHash = null;
    String message = null;
    String author = System.getProperty("user.name", "Unknown") + " <" + 
                    System.getProperty("user.email", "unknown@example.com") + ">";
    long timestamp = System.currentTimeMillis() / 1000;
    String timezone = "+0000";
    
    // Parse arguments
    for (int i = 1; i < args.length; i++) {
      if (args[i].equals("-m") && i + 1 < args.length) {
        message = args[++i];
      } else if (args[i].equals("-p") && i + 1 < args.length) {
        parentHash = args[++i];
      } else if (args[i].equals("-t") && i + 1 < args.length) {
        treeHash = args[++i];
      }
    }
    
    if (treeHash == null && args.length > 1) {
      treeHash = args[1];
    }
    
    if (treeHash == null) {
      throw new IOException("Tree hash required");
    }
    
    if (message == null) {
      throw new IOException("Commit message required (-m)");
    }
    
    StringBuilder commitContent = new StringBuilder();
    commitContent.append("tree ").append(treeHash).append("\n");
    if (parentHash != null) {
      commitContent.append("parent ").append(parentHash).append("\n");
    }
    commitContent.append("author ").append(author).append(" ").append(timestamp).append(" ").append(timezone).append("\n");
    commitContent.append("committer ").append(author).append(" ").append(timestamp).append(" ").append(timezone).append("\n");
    commitContent.append("\n");
    commitContent.append(message).append("\n");
    
    String commitHash = storeObject("commit", commitContent.toString().getBytes());
    System.out.println(commitHash);
  }

  // ========== COMMIT ==========
  private static void commit(String[] args) throws IOException {
    String message = null;
    
    // Parse -m flag
    for (int i = 1; i < args.length; i++) {
      if (args[i].equals("-m") && i + 1 < args.length) {
        message = args[++i];
      }
    }
    
    if (message == null) {
      throw new IOException("Commit message required (-m)");
    }
    
    // Write tree from current directory
    String treeHash = writeTree();
    
    // Get parent commit from HEAD
    String parentHash = GitRepository.getHeadCommit();
    
    // Create commit
    String commitHash = createCommit(treeHash, parentHash, message);
    
    // Update HEAD
    GitRepository.updateHead(commitHash);
    
    System.out.println(commitHash);
  }

  private static String createCommit(String treeHash, String parentHash, String message) throws IOException {
    String author = System.getProperty("user.name", "Unknown") + " <" + 
                    System.getProperty("user.email", "unknown@example.com") + ">";
    long timestamp = System.currentTimeMillis() / 1000;
    String timezone = "+0000";
    
    StringBuilder commitContent = new StringBuilder();
    commitContent.append("tree ").append(treeHash).append("\n");
    if (parentHash != null) {
      commitContent.append("parent ").append(parentHash).append("\n");
    }
    commitContent.append("author ").append(author).append(" ").append(timestamp).append(" ").append(timezone).append("\n");
    commitContent.append("committer ").append(author).append(" ").append(timestamp).append(" ").append(timezone).append("\n");
    commitContent.append("\n");
    commitContent.append(message).append("\n");
    
    return storeObject("commit", commitContent.toString().getBytes());
  }


  // ========== LOG ==========
  private static void log(String[] args) throws IOException {
    String startHash = null;
    
    if (args.length > 1) {
      startHash = args[1];
    } else {
      startHash = GitRepository.getHeadCommit();
    }
    
    if (startHash == null) {
      System.out.println("No commits found");
      return;
    }
    
    String currentHash = startHash;
    while (currentHash != null) {
      printCommit(currentHash);
      currentHash = getParentCommit(currentHash);
      if (currentHash != null) {
        System.out.println();
      }
    }
  }

  private static void printCommit(String hash) throws IOException {
    ObjectStore.ObjectInfo obj = ObjectStore.parseObject(hash);
    if (!obj.type.equals("commit")) {
      throw new IOException("Not a commit object: " + hash);
    }
    
    String content = new String(obj.content);
    String[] lines = content.split("\n");
    
    System.out.println("commit " + hash);
    
    for (String line : lines) {
      if (line.startsWith("tree ") || line.startsWith("parent ") || 
          line.startsWith("author ") || line.startsWith("committer ")) {
        System.out.println(line);
      } else if (!line.isEmpty()) {
        System.out.println();
        System.out.println("    " + line);
      }
    }
  }

  private static String getParentCommit(String commitHash) throws IOException {
    ObjectStore.ObjectInfo obj = ObjectStore.parseObject(commitHash);
    String content = new String(obj.content);
    String[] lines = content.split("\n");
    
    for (String line : lines) {
      if (line.startsWith("parent ")) {
        return line.substring(7);
      }
    }
    
    return null;
  }

  // ========== ADD ==========
  private static void add(String[] args) throws IOException {
    if (args.length < 2) {
      System.out.println("Usage: git add <file>");
      return;
    }
    
    String filePath = args[1];
    File file = new File(filePath);
    
    if (!file.exists()) {
      throw new IOException("File not found: " + filePath);
    }
    
    if (file.isDirectory()) {
      // Add directory recursively
      addDirectory(file, "");
    } else {
      // Create blob object and update index
      byte[] content = Files.readAllBytes(file.toPath());
      String hash = storeObject("blob", content);
      Index.updateIndex(filePath, hash, "100644");
    }
  }
  
  private static void addDirectory(File dir, String prefix) throws IOException {
    File[] files = dir.listFiles();
    if (files == null) return;
    
    for (File file : files) {
      if (file.getName().equals(".git")) continue;
      
      String filePath = prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName();
      
      if (file.isDirectory()) {
        addDirectory(file, filePath);
      } else {
        byte[] content = Files.readAllBytes(file.toPath());
        String hash = storeObject("blob", content);
        Index.updateIndex(filePath, hash, "100644");
      }
    }
  }

  // ========== CHECKOUT ==========
  private static void checkout(String[] args) throws IOException {
    if (args.length < 2) {
      System.out.println("Usage: git checkout <commit-hash>");
      return;
    }
    
    String hash = args[1];
    ObjectStore.ObjectInfo obj = ObjectStore.parseObject(hash);
    
    if (obj.type.equals("commit")) {
      // Extract tree hash from commit
      String content = new String(obj.content);
      String[] lines = content.split("\n");
      String treeHash = null;
      for (String line : lines) {
        if (line.startsWith("tree ")) {
          treeHash = line.substring(5);
          break;
        }
      }
      if (treeHash != null) {
        checkoutTree(treeHash, new File("."));
        // Update HEAD to point to this commit
        Files.write(new File(".git/HEAD").toPath(), (hash + "\n").getBytes());
      }
    } else if (obj.type.equals("tree")) {
      checkoutTree(hash, new File("."));
    } else {
      throw new IOException("Cannot checkout non-commit/tree object");
    }
  }

  private static void checkoutTree(String treeHash, File destDir) throws IOException {
    ObjectStore.ObjectInfo treeObj = ObjectStore.parseObject(treeHash);
    if (!treeObj.type.equals("tree")) {
      throw new IOException("Not a tree object");
    }
    
    byte[] treeData = treeObj.content;
    int pos = 0;
    
    while (pos < treeData.length) {
      // Read mode
      int modeEnd = pos;
      while (modeEnd < treeData.length && treeData[modeEnd] != ' ') {
        modeEnd++;
      }
      String mode = new String(treeData, pos, modeEnd - pos);
      pos = modeEnd + 1;
      
      // Read name
      int nameEnd = pos;
      while (nameEnd < treeData.length && treeData[nameEnd] != 0) {
        nameEnd++;
      }
      String name = new String(treeData, pos, nameEnd - pos);
      pos = nameEnd + 1;
      
      // Read hash
      StringBuilder hash = new StringBuilder();
      for (int i = 0; i < 20; i++) {
        hash.append(String.format("%02x", treeData[pos + i]));
      }
      pos += 20;
      
      File targetFile = new File(destDir, name);
      
      if (mode.equals("40000")) {
        // Tree (directory)
        targetFile.mkdirs();
        checkoutTree(hash.toString(), targetFile);
      } else {
        // Blob (file)
        ObjectStore.ObjectInfo blobObj = ObjectStore.parseObject(hash.toString());
        targetFile.getParentFile().mkdirs();
        Files.write(targetFile.toPath(), blobObj.content);
      }
    }
  }

  // ========== STATUS ==========
  private static void status() throws IOException {
    String headCommit = GitRepository.getHeadCommit();
    
    if (headCommit == null) {
      System.out.println("On branch main\n\nNo commits yet\n");
      return;
    }
    
    String currentBranch = GitRepository.getCurrentBranch();
    System.out.println("On branch " + currentBranch + "\n");
    
    // Simplified status - in full implementation would compare working tree with index and HEAD
    System.out.println("Changes not staged for commit:");
    System.out.println("  (use \"git add <file>\" to update what will be committed)");
    System.out.println("  (use \"git checkout -- <file>\" to discard changes in working directory)");
    System.out.println();
    System.out.println("no changes added to commit (use \"git add\")");
  }
  

  // ========== BRANCH ==========
  private static void branch(String[] args) throws IOException {
    if (args.length == 1) {
      // List branches
      File headsDir = new File(".git/refs/heads");
      if (headsDir.exists()) {
        File[] branchFiles = headsDir.listFiles();
        if (branchFiles != null) {
          String currentBranch = GitRepository.getCurrentBranch();
          for (File branchFile : branchFiles) {
            String branchName = branchFile.getName();
            String prefix = branchName.equals(currentBranch) ? "* " : "  ";
            System.out.println(prefix + branchName);
          }
        }
      }
    } else if (args.length == 2) {
      // Create branch
      String branchName = args[1];
      String headCommit = GitRepository.getHeadCommit();
      if (headCommit == null) {
        throw new IOException("Cannot create branch: no commits yet");
      }
      File branchFile = new File(".git/refs/heads/" + branchName);
      branchFile.getParentFile().mkdirs();
      Files.write(branchFile.toPath(), (headCommit + "\n").getBytes());
    } else if (args.length == 3 && args[1].equals("-d")) {
      // Delete branch
      String branchName = args[2];
      String currentBranch = GitRepository.getCurrentBranch();
      if (branchName.equals(currentBranch)) {
        throw new IOException("Cannot delete current branch");
      }
      File branchFile = new File(".git/refs/heads/" + branchName);
      if (branchFile.exists()) {
        branchFile.delete();
      } else {
        throw new IOException("Branch not found: " + branchName);
      }
    }
  }


  // ========== TAG ==========
  private static void tag(String[] args) throws IOException {
    if (args.length == 1) {
      // List tags
      File tagsDir = new File(".git/refs/tags");
      if (tagsDir.exists()) {
        File[] tagFiles = tagsDir.listFiles();
        if (tagFiles != null) {
          for (File tagFile : tagFiles) {
            System.out.println(tagFile.getName());
          }
        }
      }
    } else if (args.length == 2) {
      // Create tag
      String tagName = args[1];
      String headCommit = GitRepository.getHeadCommit();
      if (headCommit == null) {
        throw new IOException("Cannot create tag: no commits yet");
      }
      File tagFile = new File(".git/refs/tags/" + tagName);
      tagFile.getParentFile().mkdirs();
      Files.write(tagFile.toPath(), (headCommit + "\n").getBytes());
    }
  }

  // ========== HELPER CLASSES ==========
  private static class TreeEntry {
    String mode;
    String name;
    String hash;
    
    TreeEntry(String mode, String name, String hash) {
      this.mode = mode;
      this.name = name;
      this.hash = hash;
    }
  }
}
