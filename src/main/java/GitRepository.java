import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class GitRepository {
  
  public static String getHeadCommit() throws IOException {
    File headFile = new File(".git/HEAD");
    if (!headFile.exists()) {
      return null;
    }
    
    String headContent = Files.readString(headFile.toPath()).trim();
    if (headContent.startsWith("ref: ")) {
      String refPath = headContent.substring(5);
      File refFile = new File(".git/" + refPath);
      if (refFile.exists()) {
        return Files.readString(refFile.toPath()).trim();
      }
    } else {
      return headContent;
    }
    
    return null;
  }

  public static void updateHead(String commitHash) throws IOException {
    File headFile = new File(".git/HEAD");
    String headContent = Files.readString(headFile.toPath()).trim();
    
    String branchName = "main";
    if (headContent.startsWith("ref: ")) {
      String refPath = headContent.substring(5);
      if (refPath.startsWith("refs/heads/")) {
        branchName = refPath.substring(11);
      }
    }
    
    File branchFile = new File(".git/refs/heads/" + branchName);
    branchFile.getParentFile().mkdirs();
    Files.write(branchFile.toPath(), (commitHash + "\n").getBytes());
  }

  public static String getCurrentBranch() throws IOException {
    File headFile = new File(".git/HEAD");
    if (!headFile.exists()) {
      return "main";
    }
    String headContent = Files.readString(headFile.toPath()).trim();
    if (headContent.startsWith("ref: ")) {
      String refPath = headContent.substring(5);
      if (refPath.startsWith("refs/heads/")) {
        return refPath.substring(11);
      }
    }
    return "main";
  }
  
  public static String resolveRef(String ref) throws IOException {
    // Check if it's a branch name
    File branchFile = new File(".git/refs/heads/" + ref);
    if (branchFile.exists()) {
      return Files.readString(branchFile.toPath()).trim();
    }
    
    // Check if it's a tag name
    File tagFile = new File(".git/refs/tags/" + ref);
    if (tagFile.exists()) {
      return Files.readString(tagFile.toPath()).trim();
    }
    
    // Check if it's already a commit hash (40 chars hex)
    if (ref.length() == 40 && ref.matches("[0-9a-f]{40}")) {
      // Verify it exists
      String dirHash = ref.substring(0, 2);
      String fileHash = ref.substring(2);
      File objectFile = new File(".git/objects/" + dirHash + "/" + fileHash);
      if (objectFile.exists()) {
        return ref;
      }
    }
    
    return null;
  }
  
  public static void updateHeadToBranch(String branchName) throws IOException {
    File headFile = new File(".git/HEAD");
    Files.write(headFile.toPath(), ("ref: refs/heads/" + branchName + "\n").getBytes());
  }
}

