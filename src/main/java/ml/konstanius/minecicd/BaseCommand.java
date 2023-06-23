package ml.konstanius.minecicd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

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
                    sender.sendRichMessage("Error adding file(s): " + e.getMessage());
                    e.printStackTrace();
                }
                return true;
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendRichMessage("Invalid arguments. Usage: /MineCICD remove <file / 'directory/'> <message> (trailing slash is required for directories)");
                    return true;
                }

                String file = args[1];
                String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                try {
                    int amount = FilesManager.removePath(file, message, sender.getName());
                    sender.sendRichMessage("Removed " + amount + " file(s) successfully");
                } catch (IOException | GitAPIException e) {
                    sender.sendRichMessage("Error removing file(s): " + e.getMessage());
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
            case "status" -> {
                if (args.length != 1) {
                    sender.sendRichMessage("Invalid arguments. Usage: /MineCICD status");
                    return true;
                }

                String[] lines = GitManager.getStatus();
                for (String line : lines) {
                    sender.sendRichMessage(line);
                }
                return true;
            }
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendRichMessage("Invalid arguments. Usage: /MineCICD reset <commit hash / link>");
                    return true;
                }

                try {
                    GitManager.reset(args[1]);
                    sender.sendRichMessage("Reset successfully");
                } catch (IOException | GitAPIException e) {
                    sender.sendRichMessage("Error resetting: " + e.getMessage());
                    e.printStackTrace();
                }
                return true;
            }
            case "revert" -> {
                if (args.length < 3) {
                    sender.sendRichMessage("Invalid arguments. Usage: /MineCICD revert <commit hash / link>");
                    return true;
                }

                try {
                    GitManager.revert(args[1]);
                    sender.sendRichMessage("Reverted successfully");
                } catch (IOException | GitAPIException e) {
                    sender.sendRichMessage("Error reverting: " + e.getMessage());
                    e.printStackTrace();
                }
                return true;
            }
            case "log" -> {
                if (args.length != 2) {
                    sender.sendRichMessage("Invalid arguments. Usage: /MineCICD log <page>");
                    return true;
                }

                String[] lines = GitManager.getLog();

                int page = Integer.parseInt(args[1]);
                int maxPage = (int) Math.ceil(lines.length / 10.0);
                if (page > maxPage) {
                    sender.sendRichMessage("Invalid page number. Max page number is " + maxPage);
                    return true;
                }

                sender.sendRichMessage("===== MineCICD log (" + page + "/" + maxPage + ") =====");
                for (int i = (page - 1) * 10; i < page * 10; i++) {
                    if (i >= lines.length) {
                        break;
                    }
                    sender.sendRichMessage(lines[i]);
                }
                if (maxPage == 1) {
                    sender.sendRichMessage("===== End of log =====");
                } else {
                    String left = page == 1 ? "<- (Beginning)" : " <blue><u><click:run_command:TODO><- ("+ (page - 1) +") </click></u></blue>";
                    String right = page == maxPage ? "(End) ->" : " <blue><u><click:run_command:TODO>("+ (page + 1) +") -></click></u></blue> > ";
                    sender.sendRichMessage("===== " + left + " | " + right + " =====");
                }
                return true;
            }
            default -> {
                sender.sendRichMessage("Invalid subcommand. Valid commands:");
                sender.sendRichMessage("/MineCICD pull - Pulls the repo from the remote");
                sender.sendRichMessage("/MineCICD push <commit message> - Pushes the repo to the remote");
                sender.sendRichMessage("/MineCICD add <file> <message> - Adds a file to the repo");
                sender.sendRichMessage("/MineCICD clone - Clones the repo from the remote");
                sender.sendRichMessage("/MineCICD status - Gets the status of the repo");
                sender.sendRichMessage("/MineCICD reset <commit hash / link> - Resets the current branch to a specific commit");
                sender.sendRichMessage("/MineCICD revert <commit hash / link> - Reverts a specific commit");
                sender.sendRichMessage("/MineCICD reload - Reloads the config");
                return true;
            }
        }
    }
}
