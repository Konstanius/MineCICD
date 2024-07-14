package ml.konstanius.minecicd;

import org.apache.commons.io.FileUtils;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ContentMergeStrategy;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

import static ml.konstanius.minecicd.Messages.getCleanMessage;
import static ml.konstanius.minecicd.MineCICD.busyLock;

public abstract class GitUtils {
    public static CredentialsProvider getCredentials() {
        String user = Config.getString("git.user");
        if (user.isEmpty()) {
            throw new IllegalStateException("Git user is not set");
        }

        String pass = Config.getString("git.pass");
        if (pass.isEmpty()) {
            throw new IllegalStateException("Git password is not set");
        }
        return new UsernamePasswordCredentialsProvider(user, pass);
    }

    public static void loadGitIgnore() {
        File gitIgnoreFile = new File(new File("."), ".gitignore");
        if (gitIgnoreFile.exists()) return;

        InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(MineCICD.plugin.getResource(".gitignore")), StandardCharsets.UTF_8);

        Scanner scanner = new Scanner(reader);
        try {
            Files.write(gitIgnoreFile.toPath(), scanner.useDelimiter("\\A").next().getBytes());
        } catch (IOException e) {
            MineCICD.log("Failed to write .gitignore", Level.SEVERE);
            MineCICD.logError(e);
        }
    }

    public static void allowInGitIgnore(String path, boolean isDirectory) throws IOException {
        String gitString = path.replace("\\", "/");
        if (isDirectory && !gitString.endsWith("/")) {
            gitString += "/";
        }

        File gitIgnoreFile = new File(new File("."), ".gitignore");
        if (!gitIgnoreFile.exists()) {
            throw new IllegalStateException(".gitignore does not exist");
        }

        List<String> lines = Files.readAllLines(gitIgnoreFile.toPath());
        // find markers
        int endIndex = 0;
        int startIndex = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals("# MineCICD GITIGNORE PART BEGIN MARKER")) {
                startIndex = i;
            }
            if (lines.get(i).equals("# MineCICD GITIGNORE PART END MARKER")) {
                endIndex = i;
                break;
            }
        }

        // allow this exact path
        String inclusionRule = "!/" + gitString;
        lines.add(startIndex + 1, inclusionRule);

        // remove whatever includes within the path
        for (int i = startIndex + 2; i < endIndex + 1; i++) {
            if (lines.get(i).startsWith(inclusionRule)) {
                lines.remove(i);
                i--;
                endIndex--;
            }
        }

        // remove whatever excludes within the path
        for (int i = startIndex; i < endIndex + 1; i++) {
            if (lines.get(i).startsWith("/" + gitString)) {
                lines.remove(i);
                i--;
                endIndex--;
            }
        }

        // remove duplicates
        Set<String> set = new HashSet<>();
        ArrayList<String> newLines = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty() || set.add(line)) {
                newLines.add(line);
            }
        }

        Files.write(gitIgnoreFile.toPath(), newLines);
    }

    public static void removeFromGitIgnore(String path, boolean isDirectory) throws IOException {
        String gitString = path.replace("\\", "/");
        if (isDirectory && !gitString.endsWith("/")) {
            gitString += "/";
        }

        File gitIgnoreFile = new File(new File("."), ".gitignore");
        if (!gitIgnoreFile.exists()) {
            throw new IllegalStateException(".gitignore does not exist");
        }

        List<String> lines = Files.readAllLines(gitIgnoreFile.toPath());

        // find markers
        int endIndex = 0;
        int startIndex = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals("# MineCICD GITIGNORE PART BEGIN MARKER")) {
                startIndex = i;
            }
            if (lines.get(i).equals("# MineCICD GITIGNORE PART END MARKER")) {
                endIndex = i;
                break;
            }
        }

        // remove this exact path
        lines.add(endIndex, "/" + gitString);

        // remove whatever else includes within this path
        for (int i = startIndex; i < endIndex; i++) {
            if (lines.get(i).startsWith("!/" + gitString)) {
                lines.remove(i);
                i--;
                endIndex--;
            }
        }

        // remove duplicates
        Set<String> set = new HashSet<>();
        ArrayList<String> newLines = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty() || set.add(line)) {
                newLines.add(line);
            }
        }

        Files.write(gitIgnoreFile.toPath(), newLines);
    }

    public static boolean activeRepoExists() {
        File repoFolder = new File(".");
        return repoFolder.exists() && new File(repoFolder, ".git").exists();
    }

    public static String getCurrentRevision() {
        if (!activeRepoExists()) {
            return "";
        }

        try (Git git = Git.open(new File("."))) {
            return git.log().setMaxCount(1).call().iterator().next().getName();
        } catch (NoHeadException ignored) {
            return "";
        } catch (Exception e) {
            MineCICD.log("Failed to get current revision", Level.SEVERE);
            MineCICD.logError(e);
            return "";
        }
    }

    public static String getLatestRemoteRevision() {
        if (!activeRepoExists()) {
            return "";
        }

        try (Git git = Git.open(new File("."))) {
            git.fetch().setCredentialsProvider(getCredentials()).call();
            return git.log().setMaxCount(1).add(git.getRepository().resolve("origin/" + Config.getString("git.branch"))).call().iterator().next().getName();
        } catch (Exception e) {
            MineCICD.log("Failed to get latest remote revision", Level.SEVERE);
            MineCICD.logError(e);
            return "";
        }
    }

    public static Set<String> getLocalChanges() {
        if (!activeRepoExists()) {
            return new HashSet<>();
        }

        try (Git git = Git.open(new File("."))) {
            git.add().addFilepattern(".").call();
            return git.status().call().getUncommittedChanges();
        } catch (Exception e) {
            MineCICD.log("Failed to check for changes", Level.SEVERE);
            MineCICD.logError(e);
            throw new IllegalStateException("Failed to check for changes");
        }
    }

    public static List<DiffEntry> getRemoteChanges(Git git) throws GitAPIException, IOException {
        boolean ownsBusy = !busyLock;
        if (ownsBusy) busyLock = true;
        try {
            git.fetch().setCredentialsProvider(getCredentials()).call();

            ObjectId oldRevId = git.getRepository().resolve("HEAD");
            ObjectId newRevId = git.getRepository().resolve("origin/" + Config.getString("git.branch"));
            return getChangesBetween(git, oldRevId, newRevId);
        } finally {
            if (ownsBusy) busyLock = false;
        }
    }

    public static List<DiffEntry> getChangesBetween(Git git, ObjectId oldRevId, ObjectId newRevId) throws GitAPIException, IOException {
        AbstractTreeIterator oldTreeParser = prepareTreeParser(git.getRepository(), oldRevId);
        AbstractTreeIterator newTreeParser = prepareTreeParser(git.getRepository(), newRevId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiffFormatter diffFormatter = new DiffFormatter(out);
        diffFormatter.setRepository(git.getRepository());

        diffFormatter.close();
        return diffFormatter.scan(oldTreeParser, newTreeParser);
    }

    public static boolean pull() throws GitAPIException, URISyntaxException, IOException {
        boolean ownsBusy = !busyLock;
        if (ownsBusy) busyLock = true;

        String bar = MineCICD.addBar(getCleanMessage("bossbar-pulling", true), BarColor.BLUE, BarStyle.SOLID);

        try {
            String repo = Config.getString("git.repo");
            if (repo.isEmpty()) {
                throw new IllegalStateException("Git repository is not set");
            }

            String branch = Config.getString("git.branch");
            if (branch.isEmpty()) {
                throw new IllegalStateException("Git branch is not set");
            }

            boolean changes;
            String oldCommit = getCurrentRevision();
            if (!activeRepoExists()) {
                try (Git git = Git.init().setDirectory(new File(".")).call()) {
                    git.remoteAdd().setName("origin").setUri(new URIish(repo)).call();
                    git.fetch().setCredentialsProvider(getCredentials()).call();

                    boolean newRepo = true;
                    if (git.branchList().call().stream().anyMatch(ref -> ref.getName().equals("refs/remotes/origin/" + branch))) {
                        git.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/" + branch).call();
                        git.checkout().setName(branch).call();
                        newRepo = false;
                    }

                    git.add().addFilepattern(".gitignore").call();
                    if (!getLocalChanges().isEmpty() || newRepo) {
                        git.commit().setAuthor("MineCICD", "MineCICD").setMessage("MineCICD initial setup commit").call();
                        git.push().setCredentialsProvider(getCredentials()).call();
                        git.checkout().setName(branch).call();

                        if (Config.getBoolean("experimental-jar-loading")) {
                            File pluginsFolder = new File(new File("."), "plugins");
                            if (pluginsFolder.exists()) {
                                File[] files = pluginsFolder.listFiles();
                                if (files != null) {
                                    for (File file : files) {
                                        if (file.getName().endsWith(".jar") && !file.getName().contains("MineCICD") && !file.getName().contains("PlugMan")) {
                                            // run "plugman unload <plugin>"
                                            String command = "plugman unload " + file.getName();
                                            try {
                                                MineCICD.plugin.getServer().getScheduler().callSyncMethod(MineCICD.plugin, () -> {
                                                    try {
                                                        MineCICD.plugin.getServer().dispatchCommand(MineCICD.plugin.getServer().getConsoleSender(), command);
                                                    } catch (Exception e) {
                                                        MineCICD.log("Failed to unload plugin " + file.getName(), Level.SEVERE);
                                                        MineCICD.logError(e);
                                                    }
                                                    return null;
                                                }).get();
                                            } catch (Exception e) {
                                                MineCICD.log("Failed to unload plugin " + file.getName(), Level.SEVERE);
                                                MineCICD.logError(e);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        git.pull().setStrategy(MergeStrategy.THEIRS).setCredentialsProvider(getCredentials()).setContentMergeStrategy(ContentMergeStrategy.THEIRS).call();

                        if (Config.getBoolean("experimental-jar-loading")) {
                            File pluginsFolder = new File(new File("."), "plugins");
                            if (pluginsFolder.exists()) {
                                File[] files = pluginsFolder.listFiles();
                                if (files != null) {
                                    for (File file : files) {
                                        if (file.getName().endsWith(".jar") && !file.getName().contains("MineCICD") && !file.getName().contains("PlugMan")) {
                                            // run "plugman unload <plugin>"
                                            String command = "plugman load " + file.getName();
                                            try {
                                                MineCICD.plugin.getServer().getScheduler().callSyncMethod(MineCICD.plugin, () -> {
                                                    try {
                                                        MineCICD.plugin.getServer().dispatchCommand(MineCICD.plugin.getServer().getConsoleSender(), command);
                                                    } catch (Exception e) {
                                                        MineCICD.log("Failed to load plugin " + file.getName(), Level.SEVERE);
                                                        MineCICD.logError(e);
                                                    }
                                                    return null;
                                                }).get();
                                            } catch (Exception e) {
                                                MineCICD.log("Failed to load plugin " + file.getName(), Level.SEVERE);
                                                MineCICD.logError(e);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    String newCommit = git.log().setMaxCount(1).call().iterator().next().getName();
                    changes = !newCommit.equals(oldCommit);
                }
            } else {
                try (Git git = Git.open(new File("."))) {
                    // fetch which files are going to be changed by pulling (where remote is ahead of local)
                    String current = getCurrentRevision();
                    String latestRemote = getLatestRemoteRevision();
                    ArrayList<String> toDisable = new ArrayList<>();
                    ArrayList<String> toEnable = new ArrayList<>();
                    if (Config.getBoolean("experimental-jar-loading")) {
                        if (!current.equals(latestRemote)) {
                            List<DiffEntry> diffs = getRemoteChanges(git);

                            for (DiffEntry diff : diffs) {
                                String path = diff.getNewPath();
                                if (!path.startsWith("plugins/")) continue;
                                if (!path.endsWith(".jar")) continue;

                                File file = new File(path);

                                if (diff.getChangeType() == DiffEntry.ChangeType.ADD || diff.getChangeType() == DiffEntry.ChangeType.MODIFY || diff.getChangeType() == DiffEntry.ChangeType.RENAME) {
                                    toEnable.add(file.getName());
                                }

                                if (diff.getChangeType() == DiffEntry.ChangeType.DELETE || diff.getChangeType() == DiffEntry.ChangeType.MODIFY || diff.getChangeType() == DiffEntry.ChangeType.RENAME) {
                                    toDisable.add(file.getName());
                                }
                            }

                            if (!toDisable.isEmpty()) {
                                // Disable these plugins
                                for (String plugin : toDisable) {
                                    // run "plugman unload <plugin>"
                                    String command = "plugman unload " + plugin;
                                    try {
                                        MineCICD.plugin.getServer().getScheduler().callSyncMethod(MineCICD.plugin, () -> {
                                            try {
                                                MineCICD.plugin.getServer().dispatchCommand(MineCICD.plugin.getServer().getConsoleSender(), command);
                                            } catch (Exception e) {
                                                MineCICD.log("Failed to unload plugin " + plugin, Level.SEVERE);
                                                MineCICD.logError(e);
                                            }
                                            return null;
                                        }).get();
                                    } catch (Exception e) {
                                        MineCICD.log("Failed to unload plugin " + plugin, Level.SEVERE);
                                        MineCICD.logError(e);
                                    }
                                }
                            }
                        }
                    }

                    git.pull().setStrategy(MergeStrategy.THEIRS).setCredentialsProvider(getCredentials()).setContentMergeStrategy(ContentMergeStrategy.THEIRS).call();
                    String newCommit = git.log().setMaxCount(1).call().iterator().next().getName();
                    changes = !newCommit.equals(oldCommit);

                    if (Config.getBoolean("experimental-jar-loading")) {
                        if (!toEnable.isEmpty()) {
                            // Enable these plugins
                            for (String plugin : toEnable) {
                                // run "plugman load <plugin>"
                                String command = "plugman load " + plugin;
                                try {
                                    MineCICD.plugin.getServer().getScheduler().callSyncMethod(MineCICD.plugin, () -> {
                                        try {
                                            MineCICD.plugin.getServer().dispatchCommand(MineCICD.plugin.getServer().getConsoleSender(), command);
                                        } catch (Exception e) {
                                            MineCICD.log("Failed to load plugin " + plugin, Level.SEVERE);
                                            MineCICD.logError(e);
                                        }
                                        return null;
                                    }).get();
                                } catch (Exception e) {
                                    MineCICD.log("Failed to load plugin " + plugin, Level.SEVERE);
                                    MineCICD.logError(e);
                                }
                            }
                        }
                    }
                }
            }

            if (changes) {
                MineCICD.changeBar(bar, getCleanMessage("bossbar-pulled-changes", true), BarColor.GREEN, BarStyle.SOLID);
            } else {
                MineCICD.changeBar(bar, getCleanMessage("bossbar-pulled-no-changes", true), BarColor.GREEN, BarStyle.SOLID);
            }
            MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
            return changes;
        } catch (Exception e) {
            if (!(e instanceof IllegalStateException)) {
                MineCICD.log("Failed to pull changes", Level.SEVERE);
                MineCICD.logError(e);
                MineCICD.changeBar(bar, getCleanMessage("bossbar-pull-failed", true), BarColor.RED, BarStyle.SEGMENTED_12);
                MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
            } else {
                MineCICD.removeBar(bar, 0);
            }
            throw e;
        } finally {
            if (ownsBusy) busyLock = false;
        }
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId objectId) throws IOException {
        // Prepare the tree parser
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(repository)) {
            org.eclipse.jgit.revwalk.RevCommit commit = walk.parseCommit(objectId);
            org.eclipse.jgit.revwalk.RevTree tree = walk.parseTree(commit.getTree().getId());
            try (org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeParser.reset(treeWalk.getObjectReader(), tree);
            }
        }
        return treeParser;
    }

    public static void push(String message, String author) throws Exception {
        if (!activeRepoExists()) {
            throw new IllegalStateException("Repository has to be pulled (cloned) before changes can be pushed.");
        }

        boolean ownsBusy = !busyLock;
        if (ownsBusy) busyLock = true;

        String bar = MineCICD.addBar(getCleanMessage("bossbar-pushing", true), BarColor.BLUE, BarStyle.SOLID);

        try {
            // TODO check if all remote commits have been pulled first

            try (Git git = Git.open(new File("."))) {
                git.add().addFilepattern(".").call();

                boolean changes = !getLocalChanges().isEmpty();
                if (!changes) {
                    MineCICD.changeBar(bar, getCleanMessage("bossbar-push-no-changes", true), BarColor.GREEN, BarStyle.SOLID);
                    MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
                    throw new IllegalStateException("No changes to push");
                }

                RevCommit commit = git.commit().setAll(true).setAuthor(author, author).setMessage(message).call();
                git.push().add(commit.getName()).setCredentialsProvider(getCredentials()).call();
            }

            MineCICD.changeBar(bar, getCleanMessage("bossbar-pushed", true), BarColor.GREEN, BarStyle.SOLID);
            MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
        } catch (Exception e) {
            if (!(e instanceof IllegalStateException)) {
                MineCICD.log("Failed to push changes", Level.SEVERE);
                MineCICD.logError(e);
                MineCICD.changeBar(bar, getCleanMessage("bossbar-push-failed", true), BarColor.RED, BarStyle.SEGMENTED_12);
                MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
            }
            throw e;
        } finally {
            if (ownsBusy) busyLock = false;
        }
    }

    public static List<String> getIncludedFiles() throws IOException, GitAPIException {
        List<String> paths = new ArrayList<>();
        try (Git git = Git.open(new File("."))) {
            git.add().addFilepattern(".").call();
            RevWalk walk = new RevWalk(git.getRepository());
            RevCommit commit = walk.parseCommit(git.getRepository().resolve("HEAD"));
            RevTree tree = commit.getTree();

            TreeWalk treeWalk = new TreeWalk(git.getRepository());
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (File.separator.equals("\\")) {
                    path = path.replace("/", "\\");
                } else {
                    path = path.replace("\\", "/");
                }
                paths.add(path);
            }
        }
        return paths;
    }

    public static int add(File file, String author) throws GitAPIException, IOException {
        boolean ownsBusy = !busyLock;
        if (ownsBusy) busyLock = true;

        String bar = MineCICD.addBar(getCleanMessage("bossbar-adding", true), BarColor.BLUE, BarStyle.SOLID);

        try {
            if (!activeRepoExists()) {
                throw new IllegalStateException("Repository has to be pulled (cloned) before files can be added.");
            }

            File root = new File(".");

            int before = getIncludedFiles().size();

            String relativePath = root.toPath().toAbsolutePath().relativize(file.toPath().toAbsolutePath()).toString();
            allowInGitIgnore(relativePath, file.isDirectory());

            try (Git git = Git.open(new File("."))) {
                git.add().addFilepattern(".").call();
                RevCommit commit = git.commit().setAuthor(author, author).setAll(true).setMessage("MineCICD added \"" + relativePath + "\"").call();
                git.push().add(commit.getName()).setCredentialsProvider(getCredentials()).call();
            }

            int after = getIncludedFiles().size();
            int added = after - before;

            MineCICD.changeBar(bar, getCleanMessage("bossbar-added", true, new HashMap<String, String>() {{
                put("amount", String.valueOf(added));
            }}), BarColor.GREEN, BarStyle.SOLID);
            MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
            return added;
        } catch (Exception e) {
            if (!(e instanceof IllegalStateException)) {
                MineCICD.log("Failed to add file(s)", Level.SEVERE);
                MineCICD.logError(e);
                MineCICD.changeBar(bar, getCleanMessage("bossbar-adding-failed", true), BarColor.RED, BarStyle.SEGMENTED_12);
                MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
            } else {
                MineCICD.removeBar(bar, 0);
            }
            throw e;
        } finally {
            if (ownsBusy) busyLock = false;
        }
    }

    public static int remove(File file, String author) throws GitAPIException, IOException {
        boolean ownsBusy = !busyLock;
        if (ownsBusy) busyLock = true;

        String bar = MineCICD.addBar(getCleanMessage("bossbar-removing", true), BarColor.BLUE, BarStyle.SOLID);

        try {
            if (!activeRepoExists()) {
                throw new IllegalStateException("Repository has to be pulled (cloned) before files can be removed.");
            }

            File root = new File(".");

            int amountBefore = getIncludedFiles().size();

            String relativePath = root.toPath().toAbsolutePath().relativize(file.toPath().toAbsolutePath()).toString();
            removeFromGitIgnore(relativePath, file.isDirectory());

            int amountAfter;
            try (Git git = Git.open(new File("."))) {
                RmCommand rm = git.rm().setCached(true).addFilepattern(relativePath);
                rm.call();
                RevCommit commit = git.commit().setAuthor(author, author).setAll(true).setMessage("MineCICD removed \"" + relativePath + "\"").call();
                git.push().add(commit.getName()).setCredentialsProvider(getCredentials()).call();
                amountAfter = getIncludedFiles().size();
            }

            int amountRemoved = amountBefore - amountAfter;

            MineCICD.changeBar(bar, getCleanMessage("bossbar-removed", true, new HashMap<String, String>() {{
                put("amount", String.valueOf(amountRemoved));
            }}), BarColor.GREEN, BarStyle.SOLID);
            MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
            return amountRemoved;
        } catch (Exception e) {
            if (!(e instanceof IllegalStateException)) {
                MineCICD.log("Failed to remove file(s)", Level.SEVERE);
                MineCICD.logError(e);
                MineCICD.changeBar(bar, getCleanMessage("bossbar-removing-failed", true), BarColor.RED, BarStyle.SEGMENTED_12);
                MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
            } else {
                MineCICD.removeBar(bar, 0);
            }
            throw e;
        } finally {
            if (ownsBusy) busyLock = false;
        }
    }

    public static void reset(String commit) throws GitAPIException, IOException {
        if (!activeRepoExists()) {
            throw new IllegalStateException("Repository has to be pulled (cloned) before it can be reset.");
        }

        boolean ownsBusy = !busyLock;
        if (ownsBusy) busyLock = true;

        String bar = MineCICD.addBar(getCleanMessage("bossbar-resetting", true), BarColor.BLUE, BarStyle.SOLID);

        try (Git git = Git.open(new File("."))) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(commit).call();
            MineCICD.changeBar(bar, getCleanMessage("bossbar-reset", true), BarColor.GREEN, BarStyle.SOLID);
            MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
        } catch (Exception e) {
            if (!(e instanceof IllegalStateException)) {
                MineCICD.log("Failed to reset repository", Level.SEVERE);
                MineCICD.logError(e);
                MineCICD.changeBar(bar, getCleanMessage("bossbar-reset-failed", true), BarColor.RED, BarStyle.SEGMENTED_12);
                MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
            } else {
                MineCICD.removeBar(bar, 0);
            }
            throw e;
        } finally {
            if (ownsBusy) busyLock = false;
        }
    }

    public static void revert(String commit) throws GitAPIException, IOException {
        if (!activeRepoExists()) {
            throw new IllegalStateException("Repository has to be pulled (cloned) before it can be reverted.");
        }

        boolean ownsBusy = !busyLock;
        if (ownsBusy) busyLock = true;

        String bar = MineCICD.addBar(getCleanMessage("bossbar-reverting", true), BarColor.BLUE, BarStyle.SOLID);

        try (Git git = Git.open(new File("."))) {
            ObjectId commitId = git.getRepository().resolve(commit);
            RevCommit revCommit = git.revert().include(commitId).call();
            git.push().add(revCommit.getName()).setCredentialsProvider(getCredentials()).call();
            MineCICD.changeBar(bar, getCleanMessage("bossbar-reverted", true), BarColor.GREEN, BarStyle.SOLID);
            MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
        } catch (Exception e) {
            if (!(e instanceof IllegalStateException)) {
                MineCICD.log("Failed to revert repository", Level.SEVERE);
                MineCICD.logError(e);
                MineCICD.changeBar(bar, getCleanMessage("bossbar-revert-failed", true), BarColor.RED, BarStyle.SEGMENTED_12);
                MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
            } else {
                MineCICD.removeBar(bar, 0);
            }
            throw e;
        } finally {
            if (ownsBusy) busyLock = false;
        }
    }

    public static void rollback(Calendar calendar) throws GitAPIException, IOException {
        if (!activeRepoExists()) {
            throw new IllegalStateException("Repository has to be pulled (cloned) before it can be rolled back.");
        }

        String lastCommit;
        long rollbackTime = calendar.getTimeInMillis();
        try (Git git = Git.open(new File("."))) {
            RevCommit commit = git.log().setMaxCount(1).call().iterator().next();
            PersonIdent author = commit.getAuthorIdent();
            Date commitTime = author.getWhen();
            long time = commitTime.getTime();
            if (time <= rollbackTime) {
                lastCommit = commit.getName();
            } else {
                lastCommit = null;
                while (true) {
                    if (commit.getParentCount() == 0) {
                        break;
                    }

                    commit = commit.getParent(0);
                    author = commit.getAuthorIdent();
                    commitTime = author.getWhen();
                    time = commitTime.getTime();
                    if (time <= rollbackTime) {
                        lastCommit = commit.getName();
                        break;
                    }
                }
            }
        }

        if (lastCommit == null) {
            throw new IllegalStateException("No commits found before the specified time");
        }

        reset(lastCommit);
    }

    public static void mergeAbort() throws IOException, GitAPIException {
        try (Git git = Git.open(new File("."))) {
            Repository repository = git.getRepository();
            repository.writeMergeCommitMsg(null);
            repository.writeMergeHeads(null);
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
        }
    }

    public static void repoReset() {
        FileUtils.deleteQuietly(new File(new File("."), ".git"));
        FileUtils.deleteQuietly(new File(new File("."), ".gitignore"));
    }

    public static RevCommit getCommit(String commit) throws IOException {
        try (Git git = Git.open(new File("."))) {
            ObjectId commitId = git.getRepository().resolve(commit);
            if (commitId == null) {
                throw new IllegalArgumentException("Commit not found");
            }

            RevWalk walk = new RevWalk(git.getRepository());
            return walk.parseCommit(commitId);
        }
    }
}
