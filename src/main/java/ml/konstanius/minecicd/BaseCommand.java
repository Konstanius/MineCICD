package ml.konstanius.minecicd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

import static ml.konstanius.minecicd.MineCICD.busy;

public class BaseCommand implements CommandExecutor {

    public BaseCommand() {
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        String subCommand;
        if (args.length == 0) {
            subCommand = "help";
        } else {
            subCommand = args[0];
        }

        if (!sender.hasPermission("minecicd." + subCommand)) {
            sender.sendRichMessage("You do not have permission to use this command");
            return true;
        }

        if (busy) {
            sender.sendRichMessage("The plugin is busy with another command");
            return true;
        }

        switch (subCommand) {
            case "pull" -> {
                try {
                    if (args.length != 1) {
                        sender.sendRichMessage("Invalid arguments. Usage: /MineCICD pull");
                        return true;
                    }

                    boolean changes = GitManager.pullRepo();
                    if (changes) {
                        FilesManager.mergeToLocal();
                        sender.sendRichMessage("All changes have been pulled successfully");
                    } else {
                        sender.sendRichMessage("No changes to pull");
                    }
                } catch (IOException | GitAPIException e) {
                    sender.sendRichMessage("Error pulling repo: " + e.getMessage());
                    e.printStackTrace();
                }
                return true;
            }
            case "push" -> {
                try {
                    if (args.length < 2) {
                        sender.sendRichMessage("Invalid arguments. Usage: /MineCICD push <commit message>");
                        return true;
                    }

                    FilesManager.mergeToGit();
                    String commitMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    GitManager.pushRepo(commitMessage, sender.getName());
                    sender.sendRichMessage("Pushed repo successfully");
                } catch (IOException | GitAPIException e) {
                    sender.sendRichMessage("Error pushing repo: " + e.getMessage());
                    e.printStackTrace();
                }
                return true;
            }
            case "reload" -> {
                if (args.length != 1) {
                    sender.sendRichMessage("Invalid arguments. Usage: /MineCICD reload");
                    return true;
                }

                Config.reload();

                try {
                    GitManager.checkoutBranch();
                } catch (IOException | GitAPIException e) {
                    sender.sendRichMessage("Error checking out branch: " + e.getMessage() + "\nYou might still be able to continue using the plugin");
                    return true;
                }
                sender.sendRichMessage("Reloaded config successfully");

                return true;
            }
            case "add" -> {
                if (args.length < 3) {
                    sender.sendRichMessage("Invalid arguments. Usage: /MineCICD add <file / 'directory/'> <message> (trailing slash is required for directories)");
                    return true;
                }

                String file = args[1];
                String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                try {
                    int amount = FilesManager.addPath(file, message, sender.getName());
                    sender.sendRichMessage("Added " + amount + " file(s) successfully");
                } catch (IOException | GitAPIException e) {
                    sender.sendRichMessage("Error adding file: " + e.getMessage());
                    e.printStackTrace();
                }
                return true;
            }
            case "clone" -> {
                if (args.length != 1) {
                    sender.sendRichMessage("Invalid arguments. Usage: /MineCICD clone");
                    return true;
                }

                try {
                    GitManager.cloneRepo();
                    FilesManager.mergeToLocal();
                    sender.sendRichMessage("Cloned repo successfully");
                } catch (IOException | GitAPIException e) {
                    sender.sendRichMessage("Error cloning repo: " + e.getMessage());
                    e.printStackTrace();
                }
                return true;
            }
            default -> {
                sender.sendRichMessage("Invalid subcommand. Valid commands:");
                sender.sendRichMessage("/MineCICD pull - Pulls the repo from the remote");
                sender.sendRichMessage("/MineCICD push <commit message> - Pushes the repo to the remote");
                sender.sendRichMessage("/MineCICD add <file> <message> - Adds a file to the repo");
                sender.sendRichMessage("/MineCICD clone - Clones the repo from the remote");
                sender.sendRichMessage("/MineCICD reload - Reloads the config");
                return true;
            }
        }
    }
}
