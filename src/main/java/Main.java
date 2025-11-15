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
        case "merge" -> merge(args);
        case "diff" -> diff(args);
        case "reset" -> reset(args);
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
    
    // Create config file
    Config.createDefaultConfig();
    
    System.out.println("Initialized git directory");
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
    String hash = ObjectStore.storeObject("blob", content);
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
        String blobHash = ObjectStore.storeObject("blob", content);
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
    
    return ObjectStore.storeObject("tree", baos.toByteArray());
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
    
    String commitHash = createCommit(treeHash, parentHash, message);
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
    
    // Write tree from index (staging area)
    String treeHash = writeTreeFromIndex();
    
    // Get parent commit from HEAD
    String parentHash = GitRepository.getHeadCommit();
    
    // Create commit
    String commitHash = createCommit(treeHash, parentHash, message);
    
    // Update HEAD
    GitRepository.updateHead(commitHash);
    
    System.out.println(commitHash);
  }
  
  // ========== WRITE-TREE-FROM-INDEX ==========
  private static String writeTreeFromIndex() throws IOException {
    Map<String, Index.IndexEntry> indexEntries = Index.readIndex();
    
    if (indexEntries.isEmpty()) {
      throw new IOException("Nothing to commit (index is empty)");
    }
    
    // Organize entries by directory
    Map<String, Map<String, TreeEntry>> dirContents = new HashMap<>();
    dirContents.put("", new TreeMap<>());
    
    // Process all index entries
    for (Index.IndexEntry entry : indexEntries.values()) {
      String[] parts = entry.path.split("/");
      String dirPath = "";
      
      // Ensure all parent directories exist in dirContents
      for (int i = 0; i < parts.length - 1; i++) {
        String nextDir = dirPath.isEmpty() ? parts[i] : dirPath + "/" + parts[i];
        dirContents.putIfAbsent(nextDir, new TreeMap<>());
        dirPath = nextDir;
      }
      
      // Add file to its directory
      String fileName = parts[parts.length - 1];
      dirContents.get(dirPath).put(fileName, new TreeEntry(entry.mode, fileName, entry.hash));
    }
    
    // Build trees bottom-up
    Map<String, String> treeHashes = new HashMap<>();
    List<String> dirs = new ArrayList<>(dirContents.keySet());
    dirs.sort((a, b) -> {
      int depthA = a.isEmpty() ? 0 : a.split("/").length;
      int depthB = b.isEmpty() ? 0 : b.split("/").length;
      return Integer.compare(depthB, depthA); // Deepest first
    });
    
    for (String dirPath : dirs) {
      Map<String, TreeEntry> contents = dirContents.get(dirPath);
      Map<String, TreeEntry> treeEntries = new TreeMap<>();
      
      // Add direct file entries
      for (Map.Entry<String, TreeEntry> e : contents.entrySet()) {
        treeEntries.put(e.getKey(), e.getValue());
      }
      
      // Add subdirectory entries (if any)
      String parentPath = dirPath;
      for (String subDir : dirs) {
        if (!subDir.equals(parentPath) && subDir.startsWith(parentPath.isEmpty() ? "" : parentPath + "/")) {
          String relative = parentPath.isEmpty() ? subDir : subDir.substring(parentPath.length() + 1);
          if (!relative.contains("/")) {
            // Direct child directory
            String treeHash = treeHashes.get(subDir);
            if (treeHash != null) {
              treeEntries.put(relative, new TreeEntry("40000", relative, treeHash));
            }
          }
        }
      }
      
      // Build tree object
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      for (TreeEntry entry : treeEntries.values()) {
        baos.write(entry.mode.getBytes());
        baos.write(' ');
        baos.write(entry.name.getBytes());
        baos.write(0);
        byte[] hashBytes = hexToBytes(entry.hash);
        baos.write(hashBytes);
      }
      
      String treeHash = ObjectStore.storeObject("tree", baos.toByteArray());
      treeHashes.put(dirPath, treeHash);
    }
    
    return treeHashes.get("");
  }

  private static String createCommit(String treeHash, String parentHash, String message) throws IOException {
    String author = Config.getUserName() + " <" + Config.getUserEmail() + ">";
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
    
    return ObjectStore.storeObject("commit", commitContent.toString().getBytes());
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
    
    boolean inMessage = false;
    for (String line : lines) {
      if (line.startsWith("tree ") || line.startsWith("parent ") || 
          line.startsWith("author ") || line.startsWith("committer ")) {
        System.out.println(line);
      } else if (line.isEmpty() && !inMessage) {
        inMessage = true;
      } else if (inMessage || !line.isEmpty()) {
        if (!inMessage) {
          System.out.println();
          inMessage = true;
        }
        System.out.println("    " + line);
      }
    }
  }
  
  private static String getParentCommit(String commitHash) throws IOException {
    ObjectStore.ObjectInfo obj = ObjectStore.parseObject(commitHash);
    String content = new String(obj.content);
    String[] lines = content.split("\n");
    
    // Return first parent (for merge commits, there can be multiple)
    for (String line : lines) {
      if (line.startsWith("parent ")) {
        return line.substring(7);
      }
    }
    
    return null;
  }
  
  private static List<String> getParentCommits(String commitHash) throws IOException {
    List<String> parents = new ArrayList<>();
    ObjectStore.ObjectInfo obj = ObjectStore.parseObject(commitHash);
    String content = new String(obj.content);
    String[] lines = content.split("\n");
    
    for (String line : lines) {
      if (line.startsWith("parent ")) {
        parents.add(line.substring(7));
      }
    }
    
    return parents;
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
      String hash = ObjectStore.storeObject("blob", content);
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
        String hash = ObjectStore.storeObject("blob", content);
        Index.updateIndex(filePath, hash, "100644");
      }
    }
  }

  // ========== CHECKOUT ==========
  private static void checkout(String[] args) throws IOException {
    if (args.length < 2) {
      System.out.println("Usage: git checkout <commit-hash|branch-name>");
      return;
    }
    
    String ref = args[1];
    String commitHash = GitRepository.resolveRef(ref);
    
    if (commitHash == null) {
      throw new IOException("Invalid reference: " + ref);
    }
    
    ObjectStore.ObjectInfo obj = ObjectStore.parseObject(commitHash);
    
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
        
        // Check if ref is a branch name
        File branchFile = new File(".git/refs/heads/" + ref);
        if (branchFile.exists()) {
          // Update HEAD to point to branch
          GitRepository.updateHeadToBranch(ref);
        } else {
          // Detached HEAD - point directly to commit
          Files.write(new File(".git/HEAD").toPath(), (commitHash + "\n").getBytes());
        }
      }
    } else if (obj.type.equals("tree")) {
      checkoutTree(commitHash, new File("."));
      Files.write(new File(".git/HEAD").toPath(), (commitHash + "\n").getBytes());
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
    String currentBranch = GitRepository.getCurrentBranch();
    
    if (headCommit == null) {
      String branchDisplay = currentBranch != null ? currentBranch : "main";
      System.out.println("On branch " + branchDisplay + "\n\nNo commits yet\n");
      return;
    }
    
    if (currentBranch != null) {
      System.out.println("On branch " + currentBranch + "\n");
    } else {
      System.out.println("HEAD detached at " + headCommit.substring(0, 7) + "\n");
    }
    
    // Get HEAD tree
    Map<String, String> headTreeFiles = getTreeFiles(headCommit);
    
    // Get index entries
    Map<String, Index.IndexEntry> indexEntries = Index.readIndex();
    
    // Get working tree files
    Map<String, String> workingTreeFiles = getWorkingTreeFiles();
    
    // Compare and categorize
    List<String> staged = new ArrayList<>();
    List<String> modified = new ArrayList<>();
    List<String> deleted = new ArrayList<>();
    List<String> untracked = new ArrayList<>();
    
    // Check staged changes (index vs HEAD)
    for (Map.Entry<String, Index.IndexEntry> entry : indexEntries.entrySet()) {
      String path = entry.getKey();
      String indexHash = entry.getValue().hash;
      String headHash = headTreeFiles.get(path);
      
      if (headHash == null) {
        staged.add(path); // New file
      } else if (!headHash.equals(indexHash)) {
        staged.add(path); // Modified file
      }
    }
    
    // Check for deleted files in HEAD but not in index
    for (String path : headTreeFiles.keySet()) {
      if (!indexEntries.containsKey(path)) {
        deleted.add(path);
      }
    }
    
    // Check working tree vs index
    for (Map.Entry<String, String> entry : workingTreeFiles.entrySet()) {
      String path = entry.getKey();
      String workingHash = entry.getValue();
      Index.IndexEntry indexEntry = indexEntries.get(path);
      
      if (indexEntry == null) {
        if (!headTreeFiles.containsKey(path)) {
          untracked.add(path);
        }
      } else if (!indexEntry.hash.equals(workingHash)) {
        modified.add(path);
      }
    }
    
    // Check for files in index but not in working tree
    for (String path : indexEntries.keySet()) {
      if (!workingTreeFiles.containsKey(path) && !deleted.contains(path)) {
        deleted.add(path);
      }
    }
    
    // Print status
    if (!staged.isEmpty()) {
      System.out.println("Changes to be committed:");
      System.out.println("  (use \"git restore --staged <file>...\" to unstage)");
      for (String file : staged) {
        System.out.println("\tmodified:   " + file);
      }
      System.out.println();
    }
    
    if (!modified.isEmpty() || !deleted.isEmpty()) {
      System.out.println("Changes not staged for commit:");
      System.out.println("  (use \"git add <file>...\" to update what will be committed)");
      System.out.println("  (use \"git restore <file>...\" to discard changes in working directory)");
      for (String file : modified) {
        System.out.println("\tmodified:   " + file);
      }
      for (String file : deleted) {
        System.out.println("\tdeleted:    " + file);
      }
      System.out.println();
    }
    
    if (!untracked.isEmpty()) {
      System.out.println("Untracked files:");
      System.out.println("  (use \"git add <file>...\" to include in what will be committed)");
      for (String file : untracked) {
        System.out.println("\t" + file);
      }
      System.out.println();
    }
    
    if (staged.isEmpty() && modified.isEmpty() && deleted.isEmpty() && untracked.isEmpty()) {
      System.out.println("nothing to commit, working tree clean");
    }
  }
  
  private static Map<String, String> getTreeFiles(String commitHash) throws IOException {
    Map<String, String> files = new HashMap<>();
    
    if (commitHash == null) {
      return files;
    }
    
    ObjectStore.ObjectInfo commitObj = ObjectStore.parseObject(commitHash);
    if (!commitObj.type.equals("commit")) {
      return files;
    }
    
    String content = new String(commitObj.content);
    String[] lines = content.split("\n");
    String treeHash = null;
    for (String line : lines) {
      if (line.startsWith("tree ")) {
        treeHash = line.substring(5);
        break;
      }
    }
    
    if (treeHash != null) {
      getTreeFilesRecursive(treeHash, "", files);
    }
    
    return files;
  }
  
  private static void getTreeFilesRecursive(String treeHash, String prefix, Map<String, String> files) throws IOException {
    ObjectStore.ObjectInfo treeObj = ObjectStore.parseObject(treeHash);
    if (!treeObj.type.equals("tree")) {
      return;
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
      
      String path = prefix.isEmpty() ? name : prefix + "/" + name;
      
      if (mode.equals("40000")) {
        // Tree (directory)
        getTreeFilesRecursive(hash.toString(), path, files);
      } else {
        // Blob (file)
        files.put(path, hash.toString());
      }
    }
  }
  
  private static Map<String, String> getWorkingTreeFiles() throws IOException {
    Map<String, String> files = new HashMap<>();
    getWorkingTreeFilesRecursive(new File("."), "", files);
    return files;
  }
  
  private static void getWorkingTreeFilesRecursive(File dir, String prefix, Map<String, String> files) throws IOException {
    File[] fileList = dir.listFiles();
    if (fileList == null) return;
    
    for (File file : fileList) {
      if (file.getName().equals(".git")) continue;
      
      String path = prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName();
      
      if (file.isDirectory()) {
        getWorkingTreeFilesRecursive(file, path, files);
      } else {
        byte[] content = Files.readAllBytes(file.toPath());
        String hash = ObjectStore.sha1Hash(("blob " + content.length + "\0").getBytes());
        // Actually need to hash the full object format
        String header = "blob " + content.length + "\0";
        byte[] fullData = new byte[header.length() + content.length];
        System.arraycopy(header.getBytes(), 0, fullData, 0, header.length());
        System.arraycopy(content, 0, fullData, header.length(), content.length);
        hash = ObjectStore.sha1Hash(fullData);
        files.put(path, hash);
      }
    }
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
            String prefix = (currentBranch != null && branchName.equals(currentBranch)) ? "* " : "  ";
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
      if (currentBranch != null && branchName.equals(currentBranch)) {
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

  // ========== MERGE ==========
  private static void merge(String[] args) throws IOException {
    if (args.length < 2) {
      System.out.println("Usage: git merge <branch-name>");
      return;
    }
    
    String branchName = args[1];
    String branchCommit = GitRepository.resolveRef(branchName);
    String currentCommit = GitRepository.getHeadCommit();
    
    if (branchCommit == null) {
      throw new IOException("Branch not found: " + branchName);
    }
    
    if (currentCommit == null) {
      throw new IOException("No commits yet");
    }
    
    if (branchCommit.equals(currentCommit)) {
      System.out.println("Already up to date.");
      return;
    }
    
    // Find merge base (simplified - just use first common ancestor)
    findMergeBase(currentCommit, branchCommit);
    
    // Create merge commit with two parents
    String treeHash = writeTreeFromIndex();
    String commitHash = createMergeCommit(treeHash, currentCommit, branchCommit, "Merge branch '" + branchName + "'");
    
    // Update HEAD
    GitRepository.updateHead(commitHash);
    
    System.out.println("Merge made by recursive strategy.");
    System.out.println(commitHash);
  }
  
  private static String findMergeBase(String commit1, String commit2) throws IOException {
    // Simplified: return the older commit (in real Git, would find common ancestor)
    // For now, just return commit1 as a placeholder
    Set<String> ancestors1 = getAncestors(commit1);
    Set<String> ancestors2 = getAncestors(commit2);
    
    // Find common ancestor
    for (String ancestor : ancestors1) {
      if (ancestors2.contains(ancestor)) {
        return ancestor;
      }
    }
    
    return null; // No common ancestor found
  }
  
  private static Set<String> getAncestors(String commitHash) throws IOException {
    Set<String> ancestors = new HashSet<>();
    String current = commitHash;
    while (current != null) {
      ancestors.add(current);
      current = getParentCommit(current);
    }
    return ancestors;
  }
  
  private static String createMergeCommit(String treeHash, String parent1, String parent2, String message) throws IOException {
    String author = Config.getUserName() + " <" + Config.getUserEmail() + ">";
    long timestamp = System.currentTimeMillis() / 1000;
    String timezone = "+0000";
    
    StringBuilder commitContent = new StringBuilder();
    commitContent.append("tree ").append(treeHash).append("\n");
    commitContent.append("parent ").append(parent1).append("\n");
    commitContent.append("parent ").append(parent2).append("\n");
    commitContent.append("author ").append(author).append(" ").append(timestamp).append(" ").append(timezone).append("\n");
    commitContent.append("committer ").append(author).append(" ").append(timestamp).append(" ").append(timezone).append("\n");
    commitContent.append("\n");
    commitContent.append(message).append("\n");
    
    return ObjectStore.storeObject("commit", commitContent.toString().getBytes());
  }

  // ========== DIFF ==========
  private static void diff(String[] args) throws IOException {
    String commit1 = null;
    String commit2 = null;
    
    if (args.length == 1) {
      // Compare working tree with HEAD
      commit1 = GitRepository.getHeadCommit();
      commit2 = null; // working tree
    } else if (args.length == 2) {
      commit1 = GitRepository.resolveRef(args[1]);
      commit2 = GitRepository.getHeadCommit();
    } else if (args.length == 3) {
      commit1 = GitRepository.resolveRef(args[1]);
      commit2 = GitRepository.resolveRef(args[2]);
    }
    
    if (commit1 == null && commit2 == null) {
      System.out.println("No differences found");
      return;
    }
    
    Map<String, String> files1 = commit1 != null ? getTreeFiles(commit1) : getWorkingTreeFiles();
    Map<String, String> files2 = commit2 != null ? getTreeFiles(commit2) : getWorkingTreeFiles();
    
    // Find differences
    Set<String> allFiles = new HashSet<>(files1.keySet());
    allFiles.addAll(files2.keySet());
    
    boolean hasDiff = false;
    for (String file : allFiles) {
      String hash1 = files1.get(file);
      String hash2 = files2.get(file);
      
      if (hash1 == null) {
        System.out.println("diff --git a/" + file + " b/" + file);
        System.out.println("new file");
        hasDiff = true;
      } else if (hash2 == null) {
        System.out.println("diff --git a/" + file + " b/" + file);
        System.out.println("deleted file");
        hasDiff = true;
      } else if (!hash1.equals(hash2)) {
        System.out.println("diff --git a/" + file + " b/" + file);
        System.out.println("index " + hash1.substring(0, 7) + ".." + hash2.substring(0, 7));
        hasDiff = true;
      }
    }
    
    if (!hasDiff) {
      System.out.println("No differences found");
    }
  }

  // ========== RESET ==========
  private static void reset(String[] args) throws IOException {
    String mode = "--mixed"; // default
    String commitRef = null;
    
    // Parse arguments
    for (int i = 1; i < args.length; i++) {
      if (args[i].equals("--soft") || args[i].equals("--mixed") || args[i].equals("--hard")) {
        mode = args[i];
      } else {
        commitRef = args[i];
      }
    }
    
    if (commitRef == null) {
      throw new IOException("Commit required");
    }
    
    String commitHash = GitRepository.resolveRef(commitRef);
    if (commitHash == null) {
      throw new IOException("Invalid commit: " + commitRef);
    }
    
    // Update HEAD
    String currentBranch = GitRepository.getCurrentBranch();
    if (currentBranch != null) {
      // On a branch - update branch ref
      File branchFile = new File(".git/refs/heads/" + currentBranch);
      Files.write(branchFile.toPath(), (commitHash + "\n").getBytes());
    } else {
      // Detached HEAD - update HEAD directly
      File headFile = new File(".git/HEAD");
      Files.write(headFile.toPath(), (commitHash + "\n").getBytes());
    }
    
    if (mode.equals("--hard")) {
      // Reset working directory and index
      ObjectStore.ObjectInfo commitObj = ObjectStore.parseObject(commitHash);
      String content = new String(commitObj.content);
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
      }
      Index.clearIndex();
    } else if (mode.equals("--mixed")) {
      // Reset index only
      Index.clearIndex();
    }
    // --soft: don't reset index or working directory
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
