package ml.konstanius.minecicd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static ml.konstanius.minecicd.MineCICD.localFiles;

public class BaseCommandTabCompleter implements TabCompleter {
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!Config.getBoolean("tab-completion")) {
            return null;
        }

        List<String> unfiltered = getUnfiltered(sender, command, label, args);
        if (unfiltered == null) {
            return null;
        }

        String lastArg = args[args.length - 1];
        List<String> filtered = new java.util.ArrayList<>(List.of());
        for (String arg : unfiltered) {
            if (arg.startsWith(lastArg)) {
                filtered.add(arg);
            }
        }
        return filtered;
    }

    public @Nullable List<String> getUnfiltered(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        int argLength = args.length;
        if (argLength == 1) {
            // Subcommands
            List<String> list = new java.util.ArrayList<>(List.of("pull", "push", "add", "remove", "clone", "status", "reset", "revert", "log", "reload", "mute", "help", "script", "rollback"));
            list.removeIf(s -> !sender.hasPermission("minecicd." + s));
            return list;
        } else {
            String subCommand = args[0];
            if (!sender.hasPermission("minecicd." + subCommand)) {
                return List.of();
            }
            switch (subCommand) {
                case "add" -> {
                    if (argLength == 2) {
                        String filter = args[1];
                        List<String> returnable = new ArrayList<>();
                        for (String file : localFiles) {
                            if (file.startsWith(filter)) {
                                // check if any slashes are after the filter + 1 index
                                if (file.indexOf('/', filter.length() + 1) == -1) {
                                    returnable.add(file);
                                }
                            }
                        }
                        return returnable;
                    }
                    return List.of();
                }
                case "remove" -> {
                    if (argLength == 2) {
                        String filter = args[1];
                        List<String> returnable = new ArrayList<>();
                        for (String file : MineCICD.repoFiles) {
                            if (file.startsWith(filter)) {
                                // check if any slashes are after the filter + 1 index
                                if (file.indexOf('/', filter.length() + 1) == -1) {
                                    returnable.add(file);
                                }
                            }
                        }
                        return returnable;
                    }
                    return List.of();
                }
                case "revert", "reset" -> {
                    // Commit list
                    if (argLength == 2) {
                        return List.of(MineCICD.commitLog);
                    }
                    return List.of();
                }
                case "log" -> {
                    // Returns a number between 1 and amount / 10
                    int amount = MineCICD.logSize;
                    int pages = (int) Math.ceil((double) amount / 10);
                    // add all from 1 to amount / 10
                    List<String> list = new java.util.ArrayList<>(List.of());
                    for (int i = 1; i <= pages; i++) {
                        list.add(String.valueOf(i));
                    }
                    return list;
                }
                case "script" -> {
                    // returns the name of scripts defined in plugin data folder / scripts / <name>.txt
                    if (argLength == 2) {
                        return new ArrayList<>(MineCICD.scripts);
                    }
                    return List.of();
                }
                case "mute" -> {
                    // Returns true or false
                    return List.of("true", "false");
                }
            }
        }

        return List.of();
    }
}
