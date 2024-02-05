package ml.konstanius.minecicd;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static ml.konstanius.minecicd.Messages.getMessage;
import static ml.konstanius.minecicd.MineCICD.*;

public abstract class FilesManager {
    static void generatePreviousFiles() throws IOException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            File path = new File(plugin.getDataFolder().getAbsolutePath() + "/repo");
            if (!path.exists()) {
                throw new FileNotFoundException(getMessage("error-repo-missing", false));
            }

            File previousFilesFile = new File(plugin.getDataFolder().getAbsolutePath() + "/previousFiles.txt");
            HashSet<String> previousFilesSet = new HashSet<>();

            Files.walkFileTree(path.toPath(), new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // skip .git folder
                    if (file.toString().contains(".git")) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        String relativePath = file.toString().replace(path.toString(), "");
                        if (relativePath.startsWith("/")) {
                            relativePath = relativePath.substring(1);
                        }

                        previousFilesSet.add(relativePath);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            FileUtils.writeLines(previousFilesFile, previousFilesSet);
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    static ArrayList<String> getPreviousFiles() throws IOException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            File previousFilesFile = new File(plugin.getDataFolder().getAbsolutePath() + "/previousFiles.txt");
            if (!previousFilesFile.exists()) {
                generatePreviousFiles();
            }

            return (ArrayList<String>) FileUtils.readLines(previousFilesFile, "UTF-8");
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    public static void mergeToLocal() throws IOException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            File path = new File(plugin.getDataFolder().getAbsolutePath() + "/repo");
            if (!path.exists()) {
                throw new FileNotFoundException(getMessage("error-repo-missing", false));
            }

            gitSanitizer();

            String rootPath = plugin.getServer().getWorldContainer().getAbsolutePath().replace("/.", "");

            if (!rootPath.endsWith("/")) {
                rootPath += "/";
            }

            ArrayList<String> changedFiles = new ArrayList<>();

            ArrayList<String> newFiles = new ArrayList<>();

            String finalRootPath = rootPath;
            ArrayList<String> finalPreviousFiles = getPreviousFiles();
            Files.walkFileTree(path.toPath(), new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // skip .git folder
                    if (file.toString().contains(".git")) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        String relativePath = file.toString().replace(path.toString(), "");
                        if (relativePath.startsWith("/")) {
                            relativePath = relativePath.substring(1);
                        }
                        File targetFile = new File(finalRootPath + relativePath);

                        // remove from previousFiles
                        finalPreviousFiles.remove(relativePath);

                        // compare files
                        if (targetFile.exists()) {
                            if (FileUtils.contentEquals(file.toFile(), targetFile)) {
                                return FileVisitResult.CONTINUE;
                            } else {
                                changedFiles.add(relativePath);
                            }
                        } else {
                            newFiles.add(relativePath);
                        }

                        FileUtils.copyFile(file.toFile(), targetFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            // remove deleted files
            for (String previousFile : finalPreviousFiles) {
                File targetFile = new File(finalRootPath + previousFile);
                if (targetFile.exists()) {
                    FileUtils.deleteQuietly(targetFile);
                }

                logger.info("Deleted file: " + previousFile);
            }

            for (String changedFile : changedFiles) {
                logger.info("Changed file: " + changedFile);
            }

            for (String newFile : newFiles) {
                logger.info("New file: " + newFile);
            }


            // update previousFiles.txt
            generatePreviousFiles();
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    public static void mergeToGit() throws IOException, GitAPIException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            File path = new File(plugin.getDataFolder().getAbsolutePath() + "/repo");
            if (!path.exists()) {
                throw new FileNotFoundException(getMessage("error-repo-missing", false));
            }

            boolean changes = GitManager.pullRepo();
            if (changes) {
                mergeToLocal();
            }

            String rootPath = plugin.getServer().getWorldContainer().getAbsolutePath().replace("/.", "");

            if (!rootPath.endsWith("/")) {
                rootPath += "/";
            }

            ArrayList<String> repoFiles = getPreviousFiles();

            String repoPath = path.getAbsolutePath();
            if (!repoPath.endsWith("/")) {
                repoPath += "/";
            }

            ArrayList<String> changedFiles = new ArrayList<>();
            ArrayList<String> deletedFiles = new ArrayList<>();

            // iterate over the repoFiles, copy or delete from server to repo
            for (String repoFileString : repoFiles) {
                File serverFile = new File(rootPath + repoFileString);
                File repoFile = new File(repoPath + repoFileString);

                if (serverFile.exists()) {
                    // compare files
                    try {
                        if (FileUtils.contentEquals(serverFile, repoFile)) {
                            continue;
                        }
                    } catch (Exception ignored) {
                    }

                    // copy from server to repo
                    FileUtils.copyFile(serverFile, repoFile);

                    changedFiles.add(repoFileString);
                } else {
                    // skip .git folder
                    if (repoFileString.contains("/.git/")) {
                        continue;
                    }

                    // delete from repo
                    FileUtils.deleteQuietly(repoFile);

                    deletedFiles.add(repoFileString);
                }
            }

            // use RmCommand and add all deleted files
            if (!deletedFiles.isEmpty()) {
                try (Git git = Git.open(new File(plugin.getDataFolder().getAbsolutePath() + "/repo"))) {
                    RmCommand rm = git.rm();
                    for (String deletedFile : deletedFiles) {
                        rm.addFilepattern(deletedFile);
                    }
                    rm.call();
                }

                GitManager.generateTabCompleter();
            }

            generatePreviousFiles();
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    public static int addPath(String path, String message, String playerName) throws IOException, GitAPIException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            File repo = new File(plugin.getDataFolder().getAbsolutePath() + "/repo");
            if (!repo.exists()) {
                throw new FileNotFoundException(getMessage("error-repo-missing", false));
            }

            // check that the file / directory is not blacklisted
            boolean enableBlacklistFiletypes = Config.getBoolean("enable-blacklist-filetypes");
            ArrayList<String> blockedTypes = Config.getStringList("blacklist-filetypes");

            boolean enableBlacklistPaths = Config.getBoolean("enable-blacklist-paths");
            ArrayList<String> blockedPaths = Config.getStringList("blacklist-paths");

            boolean enableWhitelistFiletypes = Config.getBoolean("enable-whitelist-filetypes");
            ArrayList<String> allowedTypes = Config.getStringList("whitelist-filetypes");

            boolean enableWhitelistPaths = Config.getBoolean("enable-whitelist-paths");
            ArrayList<String> allowedPaths = Config.getStringList("whitelist-paths");

            boolean isDirectory = path.endsWith("/");

            if (enableBlacklistFiletypes && !isDirectory) {
                for (String blockedType : blockedTypes) {
                    if (path.endsWith(blockedType)) {
                        throw new IOException(getMessage("error-type-blacklisted", false));
                    }
                }
            }

            if (enableBlacklistPaths) {
                for (String blockedPath : blockedPaths) {
                    if (path.startsWith(blockedPath)) {
                        throw new IOException(getMessage("error-path-blacklisted", false));
                    }
                }
            }

            if (enableWhitelistFiletypes && !isDirectory) {
                boolean allowed = false;
                for (String allowedType : allowedTypes) {
                    if (path.endsWith(allowedType) || path.endsWith("/")) {
                        allowed = true;
                        break;
                    }
                }

                if (!allowed) {
                    // add to the config
                    try {
                        Config.addToList("whitelist-filetypes", path.substring(path.lastIndexOf(".")));
                    } catch (Exception ignored) {
                    }
                }
            }

            if (enableWhitelistPaths) {
                boolean allowed = false;
                for (String allowedPath : allowedPaths) {
                    if (path.startsWith(allowedPath)) {
                        allowed = true;
                        break;
                    }
                }

                if (!allowed) {
                    // add to the config
                    Config.addToList("whitelist-paths", "/" + path);
                }
            }

            List<File> files = new ArrayList<>();
            if (!isDirectory) {
                files.add(new File(plugin.getServer().getWorldContainer().getAbsolutePath().replace("/.", "") + "/" + path));
            } else {
                // recursively add the files that respect the filters of types and paths
                File directory = new File(plugin.getServer().getWorldContainer().getAbsolutePath().replace("/.", "") + "/" + path);

                // walk the directory tree
                Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        File f = file.toFile();
                        if (f.isFile()) {

                            // check that the file respects the filters
                            boolean blacklisted = false;
                            boolean allowed = false;
                            if (enableBlacklistFiletypes) {
                                for (String blockedType : blockedTypes) {
                                    if (f.getName().endsWith(blockedType)) {
                                        blacklisted = true;
                                        break;
                                    }
                                }
                            }

                            if (enableBlacklistPaths) {
                                for (String blockedPath : blockedPaths) {
                                    if (f.getAbsolutePath().startsWith(blockedPath)) {
                                        blacklisted = true;
                                        break;
                                    }
                                }
                            }

                            if (enableWhitelistFiletypes) {
                                for (String allowedType : allowedTypes) {
                                    if (f.getName().endsWith(allowedType)) {
                                        allowed = true;
                                        break;
                                    }
                                }
                            }

                            if (enableWhitelistPaths) {
                                for (String allowedPath : allowedPaths) {
                                    if (f.getAbsolutePath().startsWith(allowedPath)) {
                                        allowed = true;
                                        break;
                                    }
                                }
                            }

                            if (allowed && !blacklisted) {
                                files.add(f);
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            String root = plugin.getServer().getWorldContainer().getAbsolutePath().replace("/.", "");
            if (root.endsWith("/")) {
                root = root.substring(0, root.length() - 1);
            }

            // copy the files to the repo
            for (File file : files) {
                String relativePath = file.getAbsolutePath().replace(root, "");
                File repoFile = new File(repo.getAbsolutePath() + "/" + relativePath);
                if (!repoFile.getParentFile().exists()) {
                    repoFile.getParentFile().mkdirs();
                }
                FileUtils.copyFile(file, repoFile);
            }

            // sanitize the repo
            gitSanitizer();

            // add to git
            File addedFile = new File(repo.getAbsolutePath() + "/" + path);
            GitManager.add(addedFile, message, playerName);

            return files.size();
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    public static int removePath(String path, String message, String playerName) throws IOException, GitAPIException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            File repo = new File(plugin.getDataFolder().getAbsolutePath() + "/repo");
            if (!repo.exists()) {
                throw new FileNotFoundException(getMessage("error-repo-missing", false));
            }
            File file = new File(repo.getAbsolutePath() + "/" + path);

            mergeToGit();

            gitSanitizer();

            // count the number of files to be deleted
            int count = countFiles(new File(repo.getAbsolutePath() + "/" + path));

            // get the path of all files and directories to be deleted
            ArrayList<File> files = new ArrayList<>();

            if (!file.exists()) {
                throw new FileNotFoundException(getMessage("error-file-missing", false));
            }

            if (!file.isDirectory()) {
                files.add(file);
            } else {
                // walk through the directory and add all files to the list
                Files.walkFileTree(new File(repo.getAbsolutePath() + "/" + path).toPath(), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        files.add(file.toFile());
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            // add to git
            GitManager.remove(files, message, playerName);

            if (new File(repo.getAbsolutePath() + "/" + path).isDirectory()) {
                // delete the directory
                FileUtils.deleteDirectory(new File(repo.getAbsolutePath() + "/" + path));
            } else {
                // delete the file
                FileUtils.deleteQuietly(new File(repo.getAbsolutePath() + "/" + path));
            }

            generatePreviousFiles();

            GitManager.generateTabCompleter();

            return count;
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    public static int countFiles(File path) {
        // skip .git folder
        if (path.getAbsolutePath().contains("/.git/")) {
            return 0;
        }

        // skip if contains "/plugins/MineCICD/"
        if (path.getAbsolutePath().contains("/plugins/MineCICD/") && !path.getAbsolutePath().contains("/plugins/MineCICD/repo/")) {
            return 0;
        }

        // if file, return 1
        if (!path.isDirectory()) {
            return 1;
        }

        int count = 0;

        File[] files = path.listFiles();
        if (files == null) {
            return 0;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                count += countFiles(file);
            } else {
                count++;
            }
        }

        return count;
    }

    /**
     * Sanitize the repo folder and remove files that are not allowed
     *
     * @throws FileNotFoundException if repo folder does not exist
     * @throws IOException           if an I/O error occurs
     */
    public static void gitSanitizer() throws IOException {
        boolean ownsBusy = !busy;
        busy = true;
        try {
            boolean enableWhitelistFiletypes = Config.getBoolean("enable-whitelist-filetypes");
            ArrayList<String> allowedTypes = Config.getStringList("whitelist-filetypes");

            boolean enableWhitelistPaths = Config.getBoolean("enable-whitelist-paths");
            ArrayList<String> allowedPaths = Config.getStringList("whitelist-paths");

            boolean enableBlacklistFiletypes = Config.getBoolean("enable-blacklist-filetypes");
            ArrayList<String> blockedTypes = Config.getStringList("blacklist-filetypes");

            boolean enableBlacklistPaths = Config.getBoolean("enable-blacklist-paths");
            ArrayList<String> blockedPaths = Config.getStringList("blacklist-paths");

            File path = new File(plugin.getDataFolder().getAbsolutePath() + "/repo");
            if (!path.exists()) {
                throw new FileNotFoundException(getMessage("error-repo-missing", false));
            }

            String rootOfRepo = path.getAbsolutePath();

            // visit recursively all files in repo folder
            // steps:
            // if blacklisted path is true, delete file recursively
            // if whitelisted path is true, but does not contain the file, delete file recursively
            // if blacklisted filetype is true, delete file
            // if whitelisted filetype is true, but does not contain the file, delete file

            try {
                Files.walkFileTree(path.toPath(), new FileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        // check if path is blacklisted
                        try {
                            String absolutePath = dir.toAbsolutePath().toString();

                            absolutePath = absolutePath.replaceFirst(rootOfRepo, "");
                            if (!absolutePath.startsWith("/")) {
                                absolutePath = "/" + absolutePath;
                            }
                            if (!absolutePath.endsWith("/")) {
                                absolutePath = absolutePath + "/";
                            }

                            // skip .git folder
                            if (absolutePath.equals("/.git/")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }

                            // At this point, absolutePath is the path relative to the repo folder

                            if (enableBlacklistPaths) {
                                for (String blockedPath : blockedPaths) {
                                    if (absolutePath.startsWith(blockedPath)) {
                                        // delete file recursively
                                        FileUtils.deleteDirectory(dir.toFile());
                                        return FileVisitResult.SKIP_SUBTREE;
                                    }
                                }
                            }

                            if (enableWhitelistPaths && absolutePath.length() > 1) {
                                boolean isAllowed = false;
                                for (String allowedPath : allowedPaths) {
                                    if (absolutePath.startsWith(allowedPath)) {
                                        isAllowed = true;
                                        break;
                                    }
                                }
                                if (!isAllowed) {
                                    // delete file recursively
                                    FileUtils.deleteDirectory(dir.toFile());
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                            }

                            // Also remove /plugins/MineCICD folder
                            if (absolutePath.startsWith("/plugins/MineCICD")) {
                                FileUtils.deleteDirectory(dir.toFile());
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                        } catch (NullPointerException ignored) {

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        // if it isnt a file continue
                        if (!Files.isRegularFile(file)) {
                            return FileVisitResult.CONTINUE;
                        }

                        // check if filetype is blacklisted
                        try {

                            String absolutePath = file.toAbsolutePath().toString();

                            absolutePath = absolutePath.replace(rootOfRepo, "");
                            if (!absolutePath.startsWith("/")) {
                                absolutePath = "/" + absolutePath;
                            }

                            // skip .git folder
                            if (absolutePath.equals("/.git/")) {
                                return FileVisitResult.CONTINUE;
                            }

                            // At this point, absolutePath is the path relative to the repo folder

                            // Check path
                            if (enableBlacklistPaths) {
                                for (String blockedPath : blockedPaths) {
                                    if (absolutePath.startsWith(blockedPath)) {
                                        // delete file
                                        Files.delete(file);
                                        return FileVisitResult.CONTINUE;
                                    }
                                }
                            }

                            if (enableWhitelistPaths) {
                                boolean isAllowed = false;
                                for (String allowedPath : allowedPaths) {
                                    if (absolutePath.startsWith(allowedPath)) {
                                        isAllowed = true;
                                        break;
                                    }
                                }
                                if (!isAllowed) {
                                    // delete file
                                    Files.delete(file);
                                    return FileVisitResult.CONTINUE;
                                }
                            }

                            if (enableBlacklistFiletypes) {
                                for (String blockedType : blockedTypes) {
                                    if (absolutePath.endsWith(blockedType)) {
                                        // delete file
                                        Files.delete(file);
                                        return FileVisitResult.CONTINUE;
                                    }
                                }
                            }

                            if (enableWhitelistFiletypes) {
                                boolean isAllowed = false;
                                for (String allowedType : allowedTypes) {
                                    if (absolutePath.endsWith(allowedType)) {
                                        isAllowed = true;
                                        break;
                                    }
                                }
                                if (!isAllowed) {
                                    // delete file
                                    Files.delete(file);
                                    return FileVisitResult.CONTINUE;
                                }
                            }
                        } catch (NullPointerException ignored) {

                        }

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return null;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                        return null;
                    }
                });
            } catch (NullPointerException ignored) {

            }
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }

    public static void generateLocalFilesCache() {
        File rootFile = plugin.getServer().getWorldContainer();
        generateFilesCache(rootFile, localFiles);
    }

    public static void generateRepoFilesCache() {
        File rootFile = new File(plugin.getDataFolder().getAbsolutePath() + "/repo");
        generateFilesCache(rootFile, repoFiles);
    }

    public static void generateFilesCache(File rootFile, HashSet<String> cache) {
        List<File> files = getFilesInDirectory(rootFile);
        cache.clear();
        String finalRootPath = rootFile.getAbsolutePath();
        for (File file : files) {
            try {
                String relativePath = file.getAbsolutePath().replace(finalRootPath, "");

                // skip .git folder
                if (relativePath.startsWith("/.git")) {
                    continue;
                }

                // skip /plugins/MineCICD folder
                if (relativePath.startsWith("/plugins/MineCICD")) {
                    continue;
                }

                cache.add(file.getAbsolutePath().replace(finalRootPath, "").substring(1));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void generateScriptsCache() {
        // get plugin data path
        File rootFile = new File(plugin.getDataFolder().getAbsolutePath() + "/scripts");
        File[] fileList = rootFile.listFiles();
        if (fileList == null) {
            return;
        }

        MineCICD.scripts.clear();
        String finalRootPath = rootFile.getAbsolutePath();

        // add all files, remove leading finalRootPath, remove trailing .txt
        for (File file : fileList) {
            try {
                String relativePath = file.toPath().toString().replace(finalRootPath, "");
                if (relativePath.endsWith(".txt")) {
                    MineCICD.scripts.add(relativePath.substring(1, relativePath.length() - 4));
                }
            } catch (NullPointerException ignored) {
            }
        }
    }

    public static List<File> getFilesInDirectory(File rootFile) {
        if (!rootFile.exists()) {
            return new ArrayList<>();
        } else if (!rootFile.isDirectory()) {
            return new ArrayList<>();
        }

        File[] fileList = rootFile.listFiles();
        if (fileList == null) {
            return new ArrayList<>();
        }

        List<File> files = new ArrayList<>();
        for (File file : fileList) {
            if (file.isDirectory()) {
                files.addAll(getFilesInDirectory(file));
                files.add(file);
            } else {
                files.add(file);
            }
        }

        return files;
    }
}
