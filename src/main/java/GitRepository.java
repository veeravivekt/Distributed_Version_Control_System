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
}

