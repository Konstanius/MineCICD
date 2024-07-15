package ml.konstanius.minecicd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;

public class BaseCommandTabCompleter implements TabCompleter {
    @Override
    public ArrayList<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        ArrayList<String> unfiltered = getUnfiltered(sender, args);
        if (unfiltered == null) {
            return null;
        }

        String filter = args[args.length - 1];
        String argInputReplaced = filter.replace("\\", "/");
        ArrayList<String> filtered = new java.util.ArrayList<>();

        for (String argToFilter : unfiltered) {
            String argToFilterReplaced = argToFilter.replace("\\", "/");
            if (argToFilterReplaced.startsWith(argInputReplaced)) {
                filtered.add(argToFilter);
            }
        }
        return filtered;
    }

    public @Nullable ArrayList<String> getUnfiltered(CommandSender sender, String[] args) {
        int argLength = args.length;
        if (argLength == 1) {
            ArrayList<String> list = new java.util.ArrayList<>();
            list.add("add");
            list.add("remove");
            list.add("pull");
            list.add("push");
            list.add("reset");
            list.add("revert");
            list.add("rollback");
            list.add("log");
            list.add("reload");
            list.add("diff");
            list.add("status");
            list.add("help");
            list.add("script");
            list.add("resolve");
            list.removeIf(s -> !sender.hasPermission("minecicd." + s));
            return list;
        }

        String subCommand = args[0];
        if (!sender.hasPermission("minecicd." + subCommand)) {
            return new java.util.ArrayList<>();
        }

        switch (subCommand) {
            case "add": {
                if (args.length != 2) {
                    return new java.util.ArrayList<>();
                }

                String filter = args[1];
                String[] children = filter.split("[/\\\\]");

                File root = new File(new File(".").getAbsolutePath());
                File current = root;
                for (int i = 0; i < children.length - 1; i++) {
                    String s = children[i];
                    current = new File(current, s);
                    if (!current.exists()) {
                        return new ArrayList<>();
                    }
                }

                // if it ends with "/" or "\", set current to the last child
                if ((filter.endsWith("/") || filter.endsWith("\\")) && children.length > 0) {
                    current = new File(current, children[children.length - 1]);
                }

                File[] list = current.listFiles();
                if (list == null) {
                    return new ArrayList<>();
                }

                ArrayList<String> returnable = new ArrayList<>();
                for (File file : list) {
                    if (file.getName().equals(".git")) continue;

                    String relativePath = root.toPath().toAbsolutePath().relativize(file.toPath().toAbsolutePath()).toString();

                    if (file.isDirectory()) {
                        relativePath += File.separator;
                    }

                    returnable.add(relativePath);
                }
                return returnable;
            }
            case "remove": {
                if (args.length != 2) {
                    return new java.util.ArrayList<>();
                }

                String filter = args[1];
                String[] children = filter.split("[/\\\\]");

                File root = new File(".");
                File current = root;
                for (int i = 0; i < children.length - 1; i++) {
                    String s = children[i];
                    current = new File(current, s);
                    if (!current.exists()) {
                        return new ArrayList<>();
                    }
                }

                // if it ends with "/" or "\", set current to the last child
                if (filter.endsWith("/") || filter.endsWith("\\")) {
                    current = new File(current, children[children.length - 1]);
                }

                File[] list = current.listFiles();
                if (list == null) {
                    return new ArrayList<>();
                }

                ArrayList<String> returnable = new ArrayList<>();
                for (File file : list) {
                    if (file.getName().equals(".git")) continue;

                    String relativePath = root.toPath().toAbsolutePath().relativize(file.toPath().toAbsolutePath()).toString();

                    if (file.isDirectory()) {
                        relativePath += File.separator;
                    }

                    returnable.add(relativePath);
                }
                return returnable;
            }
            case "diff": {
                ArrayList<String> list = new ArrayList<>();
                list.add("local");
                list.add("remote");
                return list;
            }
            case "resolve": {
                ArrayList<String> list = new ArrayList<>();
                list.add("merge-abort");
                list.add("repo-reset");
                list.add("reset-local-changes");
                return list;
            }
            case "script": {
                if (!Config.getBoolean("webhooks.allow-scripts")) {
                    return new ArrayList<>();
                }

                File scriptsFolder = new File(MineCICD.plugin.getDataFolder().getAbsolutePath() + "/scripts");
                if (!scriptsFolder.exists()) {
                    return new ArrayList<>();
                }

                File[] list = scriptsFolder.listFiles();
                if (list == null) {
                    return new ArrayList<>();
                }

                ArrayList<String> returnable = new ArrayList<>();
                for (File file : list) {
                    if (file.getName().endsWith(".sh")) {
                        returnable.add(file.getName());
                    }
                }
                return returnable;
            }
            case "log": {
                try (Git git = Git.open(new File("."))) {
                    ArrayList<String> list = new ArrayList<>();

                    Iterable<RevCommit> commits = git.log().call();
                    for (RevCommit commit : commits) {
                        list.add(commit.getName());
                    }

                    int pages = (int) Math.ceil((double) list.size() / 10);
                    for (int i = 1; i <= pages; i++) {
                        list.add(String.valueOf(i));
                    }

                    return list;
                } catch (Exception e) {
                    return new ArrayList<>();
                }
            }
            default:
                return new ArrayList<>();
        }
    }
}
