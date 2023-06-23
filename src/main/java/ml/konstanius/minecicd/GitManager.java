package ml.konstanius.minecicd;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import static ml.konstanius.minecicd.MineCICD.*;

public abstract class GitManager {
    /**
     * Clones repo and checks out branch
     *
     * @throws IOException     if repo folder already exists
     * @throws GitAPIException if git clone fails
     */
    public static void cloneRepo() throws IOException, GitAPIException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            String token = Config.getString("token");
            String repo = Config.getString("repository-url");
            String branch = Config.getString("branch");

            boolean failed = false;
            if (token.isBlank()) {
                log("Token is blank", Level.SEVERE);
                failed = true;
            }
            if (repo.isBlank()) {
                log("Repository URL is blank", Level.SEVERE);
                failed = true;
            }
            if (branch.isBlank()) {
                log("Branch is blank", Level.SEVERE);
                failed = true;
            }
            if (failed) return;

            // Clone repo to repo folder and checkout branch
            // Save last commit hash

            File repoFolder = new File(plugin.getDataFolder().getAbsolutePath() + "/repo");
            if (repoFolder.exists()) {
                FileUtils.deleteDirectory(repoFolder);
            }

            try (Git git = Git
                    .cloneRepository()
                    .setURI(repo)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .setDirectory(new File(plugin.getDataFolder().getAbsolutePath() + "/repo"))
                    .setBranch(branch)
                    .call()) {
                try {
                    String newCommit = git.log().call().iterator().next().getName();
                    Config.set("last-commit", newCommit);
                    Config.save();
                } catch (Exception ignored) {
                }

                FilesManager.gitSanitizer();
            }

            FilesManager.generatePreviousFiles();
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    /**
     * Pulls repo and checks out branch
     *
     * @return true if repo was updated
     * @throws IOException     if repo folder does not exist
     * @throws GitAPIException if git pull fails
     */
    public static boolean pullRepo() throws IOException, GitAPIException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            String token = Config.getString("token");
            String lastCommit = Config.getString("last-commit");

            // Pull repo and checkout branch
            // Save last commit hash

            if (lastCommit == null || lastCommit.isBlank()) {
                cloneRepo();
                return true;
            }

            if (token.isBlank()) {
                log("Token is blank", Level.SEVERE);
                return false;
            }

            File repoFolder = new File(plugin.getDataFolder().getAbsolutePath() + "/repo");
            if (!repoFolder.exists()) {
                cloneRepo();
                return true;
            }

            try (Git git = Git.open(new File(plugin.getDataFolder().getAbsolutePath() + "/repo"))) {
                PullResult result = git.pull()
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                        .setRemoteBranchName(Config.getString("branch"))
                        .call();
                String newCommit = git.log().call().iterator().next().getName();
                Config.set("last-commit", newCommit);
                Config.save();

                FilesManager.gitSanitizer();

                if (!result.isSuccessful()) {
                    throw new IOException("Git pull failed");
                }

                return !newCommit.equals(lastCommit);
            }
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    /**
     * Creates a commit and pushes it to the repo
     *
     * @param message commit message
     * @throws IOException     if repo folder does not exist
     * @throws GitAPIException if git push fails
     */
    public static void pushRepo(String message, String player) throws IOException, GitAPIException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            String token = Config.getString("token");

            // Refresh repo
            // Add all files to commit
            // Commit and push
            // Save last commit hash

            pullRepo();

            FilesManager.gitSanitizer();

            try (Git git = Git.open(new File(plugin.getDataFolder().getAbsolutePath() + "/repo"))) {
                git.add().addFilepattern(".").call();
                git.commit().setMessage(message + "\nUser: " + player).call();
                git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, "")).call();
                String newCommit = git.log().call().iterator().next().getName();
                Config.set("last-commit", newCommit);
                Config.save();
            }

            FilesManager.generatePreviousFiles();
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    /**
     * Add a file / directory to the repo
     *
     * @param file file / directory to add
     * @throws IOException     if repo folder does not exist
     * @throws GitAPIException if git add fails
     */
    public static void add(File file, String message, String playerName) throws IOException, GitAPIException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            pullRepo();

            FilesManager.gitSanitizer();

            String token = Config.getString("token");

            String repoPath = plugin.getDataFolder().getAbsolutePath() + "/repo";

            try (Git git = Git.open(new File(repoPath))) {
                git.add().addFilepattern(".").call();
                git.commit().setMessage(message + "\nUser: " + playerName).call();
                git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, "")).call();
                String newCommit = git.log().call().iterator().next().getName();
                Config.set("last-commit", newCommit);
                Config.save();
            }
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    /**
     * Remove a file / directory from the repo
     * @param files files / directories to remove
     * @throws IOException if repo folder does not exist
     * @throws GitAPIException if git rm fails
     */
    public static void remove(List<File> files, String message, String playerName) throws IOException, GitAPIException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            pullRepo();

            FilesManager.gitSanitizer();

            String token = Config.getString("token");

            String repoPath = plugin.getDataFolder().getAbsolutePath() + "/repo";

            try (Git git = Git.open(new File(repoPath))) {
                RmCommand rm = git.rm();
                for (File file : files) {
                    String filePath = file.getAbsolutePath().replace(repoPath, "");
                    if (filePath.startsWith("/")) {
                        filePath = filePath.substring(1);
                    }
                    rm.addFilepattern(filePath);
                }
                rm.call();
                git.commit().setMessage(message + "\nUser: " + playerName).call();
                git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, "")).call();
                String newCommit = git.log().call().iterator().next().getName();
                Config.set("last-commit", newCommit);
                Config.save();
            }
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    public static void checkoutBranch() throws IOException, GitAPIException {
        boolean ownsBusy = !busy;
        busy = true;
        try (Git git = Git.open(new File(plugin.getDataFolder().getAbsolutePath() + "/repo"))){
            try {
                git.checkout().setName(Config.getString("branch")).call();
            } catch (Exception ignored) {
                git.checkout().setCreateBranch(true).setName(Config.getString("branch")).call();
            }
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    public static String[] getStatus() {
        String branch = Config.getString("branch");
        String lastCommit = Config.getString("last-commit");
        String repositoryUrl = Config.getString("repository-url");

        return new String[]{
                "MineCICD is operating on branch " + branch,
                "Repository is " + repositoryUrl,
                "Last commit: " + (lastCommit.isEmpty() ? "None" : lastCommit),
        };
    }
}
