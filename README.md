# Distributed Version Control System
> My entire documentation for creating a ```git clone``` not just the command :)

> ```git``` at its core, is a system of interconnected text files that reference each other through filenames.

## The Basics - Understanding Git: 
Consider Git as a tool that manages a filesystem by capturing snapshots of its state at different points in time.

 It is a content-addressable filesystem, acting as a key-value store(or more like value-value store, as it computes keys from data itself). Insert content into a Git repository, and Git returns a unique key to retrieve it later.

Common locations where code stays in git:
1. Local Working Directory (Code Playground)
2. Staging Area (Temporary holding spot for files before commiting)
3. Local Repository (Place where we store commited changes locally)
4. Remote Repository (Server for sharing and backing up code)

![alt text](readmeDir/image.png)


>Git uses Three-tree Architecture. 

So whats that?

### 1. Two-tree Architecture.

This is what a lot of other VCS use such as Subversion. They have a working tree and repository

![alt text](readmeDir/image2.png)

### 2. Three-tree Architecture
Git has the working tree and the repository, and additionally in between is another tree which is the staging area.

![alt text](readmeDir/image3.png)

### Git File States

In Git, files can be in one of three states:

1. **Modified / Untracked**  
   - *Modified*: You've changed the file but haven't saved those changes to your Git history yet.
   - *Untracked*: These files are new and aren't being tracked by Git. They weren’t part of the last commit and haven’t been added to staging.

2. **Staged**  
   - You've marked the modified file to be included in the next commit. It's ready to be saved to the Git history.

3. **Committed**  
   - The file's changes are safely stored in the Git repository.


## Part 1: .git Directory - git init

What does .git directory even contain?

This:

```
$ tree .git

.git
├── config
├── HEAD
├── hooks
│   └── prepare-commit-msg.msample
├── objects
│   ├── info
│   └── pack
└── refs
    ├── heads
    └── tags

```
> Note: if you want tree structure like above for your folder : ``` brew install tree```

### Git Folder and File Structure

When you initialize a Git repository, several files and folders are created. Here's a breakdown of what they are and what they do:

1. **config**  
   - This text file contains the configuration settings for your repository, such as the author information, file modes, and other basic settings.

2. **HEAD**  
   - The `HEAD` file tracks the current branch of the repository. It usually points to `refs/heads/main`, `refs/heads/master`, or another default branch, depending on your setup. After your first commit, the branch file (e.g., `master`) will appear under `refs/heads`.

3. **hooks**  
   - This folder contains scripts that Git can run automatically before or after certain actions (like committing or pushing).

4. **objects**  
   - This folder stores the actual Git objects, which represent your repository's data, such as files, commits, and changes.

5. **refs**  
   - `refs` holds references or pointers to branches and tags.  
   - `refs/heads` contains pointers to branches.  
   - `refs/tags` contains pointers to tags.

Each of these plays a critical role in how Git manages your repository!

> So how do you even initialize a git repo
```git
git init
```

Now for the implementation:
#### Initializing a Git Repository:
A Step-by-Step Breakdown This code snippet demonstrates the process of initializing a new Git repository. Let's break it down into simple steps: 

**1. Creating the Root Directory:** The code starts by creating a new directory named ".git" in the current location. This directory will serve as the root of our Git repository. 
```java
java final File root = new File(".git");
```

**2. Setting Up Essential Subdirectories:** Inside the ".git" folder, two crucial subdirectories are created:
- objects: This directory will store all the Git objects.
- refs: This directory will contain references to commits.
```java
new File(root, "objects").mkdirs(); 
new File(root, "refs").mkdirs();
```

**3. Creating the HEAD File:** A new file named "HEAD" is created in the ".git" directory. This file is essential for Git as it points to the current branch.
```java
final File head = new File(root, "HEAD");
```

**4.Writing to the HEAD File:** The code writes the following content to the HEAD file:
```text
ref: refs/heads/main
```

This line tells Git that the current branch is "main".
```java
head.createNewFile(); 
Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
```

**5. Confirmation Message:** If all steps are successful, the code prints a confirmation message:
```java
System.out.println("Initialized git directory");
```

**6. Error Handling:** The code is wrapped in a try-catch block to handle any potential IO exceptions. If an error occurs during the process, it throws a RuntimeException.
```java
try { 
	// ... (previous code) 
} catch (IOException e) { 
	throw new RuntimeException(e); 
}
```

This code essentially mimics the basic structure creation that occurs when you run `git init` in a directory. It sets up the fundamental components needed for Git to start tracking your project.
## Git Objects
So we will be dealing with 3 Git objects: 
**1. Blobs:** Used to store file data (only content of file without name or permissions) \
**2. Trees:** Store directory structures(including names and permissions) \
**3. Commits:** Keeps the data like commit message, author, commiter, parent commits, etc.. \

Every Git object is identified by a unique 40-character SHA-1 hash, called the "object hash."
for example: `9fb8b43296432c0f2212264f2206cff35b0c63`

The SHA-1 hash in Git is a 40-character identifier that ensures: \
**Uniqueness:** Each commit and content piece is uniquely tracked. \
**Integrity:** Detects data corruption or tampering. 

`git hash-object` converts an existing file into a git object
## Part 2: Read a blob object git cat-file
Blobs are binary large objects. \

`git cat-file` prints an existing git object to the standard output.

you don’t modify a file in git, you create a new file in a different location.

In git repository, the paths are actually determined by contents. 

How to read and decode a Git object (specifically a blob) from the Git object database. Let's break it down step by step: 
**1. Parsing the Object Hash:** The code starts by splitting the provided hash into two parts
```java
java String hash = args; 
String dirHash = hash.substring(0, 2); 
String fileHash = hash.substring(2);
```
- **dirHash**: The first two characters of the hash, used as the directory name.
- **fileHash**: The remaining characters, used as the file name.

**2. Locating the Blob File:** The code constructs the path to the blob file in the Git object database:
```java
File blobFile = new File("./.git/objects/" + dirHash + "/" + fileHash);
```

This path follows Git's object storage structure: `.git/objects/xx/yyyyyyyyy`.

**3. Reading and Inflating the Blob Content:** Git stores objects in a compressed format. The code reads and decompresses the blob:

```java
String blob = new BufferedReader(new InputStreamReader(new InflaterInputStream(new FileInputStream(blobFile)))).readLine();
```

This line does several things:
- Opens the blob file
- Creates an `InflaterInputStream` to decompress the content
- Wraps it in a `BufferedReader` to read the decompressed data

**4. Extracting the Actual Content:** Git prepends some metadata to the object content. The code extracts the actual content:

```java
String content = blob.substring(blob.indexOf("\0") + 1)
```

This line finds the null byte `(\0)` that separates the metadata from the content and extracts everything after it.

**5. Outputting the Content:** Finally, the code prints the extracted content:
```java
System.out.print(content);
```

This code essentially replicates part of what happens when you use `git cat-file -p <hash>` command, focusing on reading and displaying the content of a blob object.
