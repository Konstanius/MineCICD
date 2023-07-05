package ml.konstanius.minecicd;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.stream.Collectors;

import static ml.konstanius.minecicd.Messages.getMessage;
import static ml.konstanius.minecicd.Messages.sendManagementMessage;
import static ml.konstanius.minecicd.MineCICD.*;

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
            sender.sendRichMessage(getMessage(
                    "no-permission",
                    true,
                    new HashMap<>() {{
                        put("label", label);
                    }}
            ));
            return true;
        }

        if (busy) {
            sender.sendRichMessage(getMessage(
                    "busy",
                    true,
                    new HashMap<>() {{
                        put("label", label);
                    }}
            ));
            return true;
        }

        // close chat for the player
        if (sender instanceof Player) {
            ((Player) sender).closeInventory();
        }

        String title = Config.getString("bossbar-command-title");
        title = title.replace("{action}", subCommand);
        MineCICD.bossBar.setTitle(title);
        BarColor color = BarColor.GREEN;
        for (BarColor barColor : BarColor.values()) {
            if (barColor.name().contains(Config.getString("bossbar-command-color").toUpperCase())) {
                color = barColor;
                break;
            }
        }
        MineCICD.bossBar.setColor(color);
        MineCICD.bossBar.setStyle(BarStyle.SOLID);
        MineCICD.bossBar.setProgress(1.0);

        // start an empty async task
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                switch (subCommand) {
                    case "pull" -> {
                        try {
                            if (args.length != 1) {
                                sender.sendRichMessage(getMessage(
                                        "pull-usage",
                                        true,
                                        new HashMap<>() {{
                                            put("label", label);
                                        }}
                                ));
                                return;
                            }

                            boolean changes = GitManager.pullRepo();
                            if (changes) {
                                FilesManager.mergeToLocal();
                                sender.sendRichMessage(getMessage(
                                        "pull-success",
                                        true
                                ));
                                sendManagementMessage(sender);
                            } else {
                                sender.sendRichMessage(getMessage(
                                        "pull-no-changes",
                                        true,
                                        new HashMap<>() {{
                                            put("label", label);
                                        }}
                                ));
                            }
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "pull-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", e.getMessage());
                                    }}
                            ));
                            e.printStackTrace();
                        }
                    }
                    case "push" -> {
                        try {
                            if (args.length < 2) {
                                sender.sendRichMessage(getMessage(
                                        "push-usage",
                                        true,
                                        new HashMap<>() {{
                                            put("label", label);
                                        }}
                                ));
                                return;
                            }

                            FilesManager.mergeToGit();
                            String commitMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                            int changes = GitManager.pushRepo(commitMessage, sender.getName());
                            sender.sendRichMessage(getMessage(
                                    "push-success",
                                    true,
                                    new HashMap<>() {{
                                        put("changes", String.valueOf(changes));
                                    }}
                            ));
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "push-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", e.getMessage());
                                    }}
                            ));
                            e.printStackTrace();
                        }
                    }
                    case "reload" -> {
                        if (args.length != 1) {
                            sender.sendRichMessage(getMessage(
                                    "reload-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        Config.reload();
                        MineCICD.startWebServer();

                        try {
                            GitManager.checkoutBranch();
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "reload-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", e.getMessage());
                                    }}
                            ));
                            return;
                        }
                        sender.sendRichMessage(getMessage("reload-success", true));
                    }
                    case "add" -> {
                        if (args.length < 3) {
                            sender.sendRichMessage(getMessage(
                                    "add-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        String file = args[1];
                        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                        boolean isDirectory = false;
                        String path = plugin.getServer().getWorldContainer().getAbsolutePath();

                        String fileCopyPath = path + "/" + file;
                        if (fileCopyPath.endsWith("/")) {
                            fileCopyPath = fileCopyPath.substring(0, fileCopyPath.length() - 1);
                        }
                        File fileObject = new File(fileCopyPath);
                        if (!fileObject.exists()) {
                            sender.sendRichMessage(getMessage(
                                    "add-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", "File / Directory does not exist.");
                                    }}
                            ));
                            return;
                        }

                        if (fileObject.isDirectory()) {
                            isDirectory = true;
                        }

                        if (isDirectory && !file.endsWith("/")) {
                            // error out with "file is directory, directories need trailing slash"
                            sender.sendRichMessage(getMessage(
                                    "add-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", "File is a directory. To add directories, add a trailing slash to the file path.");
                                    }}
                            ));
                            return;
                        } else if (!isDirectory && file.endsWith("/")) {
                            // error out with "file is not directory, directories need trailing slash"
                            sender.sendRichMessage(getMessage(
                                    "add-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", "File is not a directory. To add files, remove the trailing slash from the file path.");
                                    }}
                            ));
                            return;
                        }

                        try {
                            int amount = FilesManager.addPath(file, message, sender.getName());
                            sender.sendRichMessage(getMessage(
                                    "add-success",
                                    true,
                                    new HashMap<>() {{
                                        put("amount", String.valueOf(amount));
                                    }}
                            ));
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "add-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", e.getMessage());
                                    }}
                            ));
                            e.printStackTrace();
                        }
                    }
                    case "remove" -> {
                        if (args.length < 3) {
                            sender.sendRichMessage(getMessage(
                                    "remove-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        String file = args[1];
                        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                        boolean isDirectory = false;
                        String path = plugin.getServer().getWorldContainer().getAbsolutePath();

                        String fileCopyPath = path + "/" + file;
                        if (fileCopyPath.endsWith("/")) {
                            fileCopyPath = fileCopyPath.substring(0, fileCopyPath.length() - 1);
                        }
                        File fileObject = new File(fileCopyPath);
                        if (!fileObject.exists()) {
                            sender.sendRichMessage(getMessage(
                                    "add-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", "File / Directory does not exist.");
                                    }}
                            ));
                            return;
                        }

                        if (fileObject.isDirectory()) {
                            isDirectory = true;
                        }

                        if (isDirectory && !file.endsWith("/")) {
                            // error out with "file is directory, directories need trailing slash"
                            sender.sendRichMessage(getMessage(
                                    "add-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", "File is a directory. To add directories, add a trailing slash to the file path.");
                                    }}
                            ));
                            return;
                        } else if (!isDirectory && file.endsWith("/")) {
                            // error out with "file is not directory, directories need trailing slash"
                            sender.sendRichMessage(getMessage(
                                    "add-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", "File is not a directory. To add files, remove the trailing slash from the file path.");
                                    }}
                            ));
                            return;
                        }

                        try {
                            int amount = FilesManager.removePath(file, message, sender.getName());
                            sender.sendRichMessage(getMessage(
                                    "remove-success",
                                    true,
                                    new HashMap<>() {{
                                        put("amount", String.valueOf(amount));
                                    }}
                            ));
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "remove-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", e.getMessage());
                                    }}
                            ));
                            e.printStackTrace();
                        }
                    }
                    case "clone" -> {
                        if (args.length != 1) {
                            sender.sendRichMessage(getMessage(
                                    "clone-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        try {
                            GitManager.cloneRepo();
                            FilesManager.mergeToLocal();
                            sender.sendRichMessage(getMessage("clone-success", true));
                            sendManagementMessage(sender);
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "clone-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", e.getMessage());
                                    }}
                            ));
                            e.printStackTrace();
                        }
                    }
                    case "status" -> {
                        if (args.length != 1) {
                            sender.sendRichMessage(getMessage(
                                    "status-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        String[] lines = GitManager.getStatus();
                        for (String line : lines) {
                            sender.sendRichMessage(line);
                        }
                    }
                    case "reset" -> {
                        if (args.length < 3) {
                            sender.sendRichMessage(getMessage(
                                    "reset-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        try {
                            GitManager.reset(args[1]);
                            sender.sendRichMessage(getMessage("reset-success", true));
                            sendManagementMessage(sender);
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "reset-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", e.getMessage());
                                    }}
                            ));
                            e.printStackTrace();
                        }
                    }
                    case "rollback" -> {
                        if (args.length != 3) {
                            sender.sendRichMessage(getMessage(
                                    "rollback-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        String date = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                        Calendar calendar = Calendar.getInstance();
                        try {
                            calendar.setTime(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(date));
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "rollback-invalid-date",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        // if future
                        if (calendar.after(Calendar.getInstance())) {
                            sender.sendRichMessage(getMessage(
                                    "rollback-future-date",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        try {
                            GitManager.rollback(calendar);
                            sender.sendRichMessage(getMessage("rollback-success", true));
                            sendManagementMessage(sender);
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "rollback-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", e.getMessage());
                                    }}
                            ));
                            e.printStackTrace();
                        }
                    }
                    case "revert" -> {
                        if (args.length < 3) {
                            sender.sendRichMessage(getMessage(
                                    "revert-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        try {
                            GitManager.revert(args[1]);
                            sender.sendRichMessage(getMessage("revert-success", true));
                            sendManagementMessage(sender);
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "revert-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", e.getMessage());
                                    }}
                            ));
                            e.printStackTrace();
                        }
                    }
                    case "log" -> {
                        if (args.length != 2) {
                            sender.sendRichMessage(getMessage(
                                    "log-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        String[] lines = GitManager.getLog();

                        int page = Integer.parseInt(args[1]);
                        int maxPage = (int) Math.ceil(lines.length / 10.0);
                        if (page > maxPage) {
                            sender.sendRichMessage(getMessage(
                                    "log-invalid-page-high",
                                    true,
                                    new HashMap<>() {{
                                        put("maxPage", String.valueOf(maxPage));
                                    }}
                            ));
                            return;
                        } else if (page < 1) {
                            sender.sendRichMessage(getMessage(
                                    "log-invalid-page-low",
                                    true,
                                    new HashMap<>() {{
                                        put("minPage", "1");
                                    }}
                            ));
                            return;
                        }

                        sender.sendRichMessage(getMessage(
                                "log-header",
                                false,
                                new HashMap<>() {{put("page", String.valueOf(page));
                                    put("maxPage", String.valueOf(maxPage));}}
                        ));
                        for (int i = (page - 1) * 10; i < page * 10; i++) {
                            if (i >= lines.length) {
                                break;
                            }
                            sender.sendRichMessage(lines[i]);
                        }
                        if (maxPage == 1) {
                            sender.sendRichMessage(getMessage("log-end", false));
                        } else {
                            String left;
                            if (page == 1) {
                                left = getMessage(
                                        "log-end-first",
                                        false
                                );
                            } else {
                                left = getMessage(
                                        "log-end-previous",
                                        false,
                                        new HashMap<>() {{
                                            put("previousPage", String.valueOf(page - 1));
                                        }}
                                );
                            }

                            String right;
                            if (page == maxPage) {
                                right = getMessage(
                                        "log-end-last",
                                        false
                                );
                            } else {
                                right = getMessage(
                                        "log-end-next",
                                        false,
                                        new HashMap<>() {{
                                            put("nextPage", String.valueOf(page + 1));
                                        }}
                                );
                            }

                            sender.sendRichMessage(
                                    getMessage(
                                            "log-end-paged",
                                            false,
                                            new HashMap<>() {{
                                                put("left", left);
                                                put("right", right);
                                            }}
                                    )
                            );
                        }
                    }
                    case "mute" -> {
                        if (args.length != 2) {
                            sender.sendRichMessage(getMessage(
                                    "mute-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        // if not player
                        if (!(sender instanceof Player)) {
                            sender.sendRichMessage(getMessage("mute-not-player", true));
                            return;
                        }

                        String arg = args[1];
                        boolean mute;
                        if (arg.equalsIgnoreCase("true") || arg.equalsIgnoreCase("yes") || arg.equalsIgnoreCase("on") || arg.equalsIgnoreCase("1")) {
                            mute = true;
                        } else if (arg.equalsIgnoreCase("false") || arg.equalsIgnoreCase("no") || arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("0")) {
                            mute = false;
                        } else {
                            sender.sendRichMessage(getMessage(
                                    "mute-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        String uuid = ((Player) sender).getUniqueId().toString();
                        if (mute) {
                            sender.sendRichMessage(getMessage("mute-enabled", true));
                            muteList.add(uuid);
                        } else {
                            sender.sendRichMessage(getMessage("mute-disabled", true));
                            muteList.remove(uuid);
                        }
                    }
                    case "script" -> {
                        if (args.length != 2) {
                            sender.sendRichMessage(getMessage(
                                    "script-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        String scriptName = args[1];

                        // remove trailing .txt
                        if (scriptName.endsWith(".txt")) {
                            scriptName = scriptName.substring(0, scriptName.length() - 4);
                        }

                        try {
                            Script.run(scriptName);
                            sender.sendRichMessage(getMessage("script-success", true));
                        } catch (Exception e) {
                            sender.sendRichMessage(getMessage(
                                    "script-failed",
                                    true,
                                    new HashMap<>() {{
                                        put("error", e.getMessage());
                                    }}
                            ));
                            e.printStackTrace();
                        }
                    }
                    case "debug" -> {
                        if (args.length != 1) {
                            sender.sendRichMessage(getMessage(
                                    "debug-usage",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                            return;
                        }

                        StringBuilder builder = new StringBuilder();
                        // date and version
                        builder.append("Date: ").append(new Date()).append("\n");
                        builder.append("Version: ").append(plugin.getDescription().getVersion()).append("\n");

                        // config
                        builder.append("Config: ").append("\n");
                        // read from plugin/config.yml
                        try {
                            FileConfiguration config = plugin.getConfig();
                            for (String key : config.getKeys(true)) {
                                builder.append(key).append(": ").append(config.get(key)).append("\n");
                            }
                        } catch (Exception e) {
                            builder.append("Failed to read config.yml: ").append(e.getMessage()).append("\n");
                        }

                        // messages.yml
                        builder.append("Messages: ").append("\n");
                        // read from plugin/messages.yml
                        try {
                            File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
                            builder.append(Files.readString(messagesFile.toPath())).append("\n");
                        } catch (Exception e) {
                            builder.append("Failed to read messages.yml: ").append(e.getMessage()).append("\n");
                        }

                        // file tree
                        builder.append("File tree: ").append("\n");
                        // start at world path

                        String rootPath = plugin.getServer().getWorldContainer().getAbsolutePath().replace("/.", "");

                        if (!rootPath.endsWith("/")) {
                            rootPath += "/";
                        }

                        try {
                            String finalRootPath = rootPath;
                            Files.walk(Paths.get(rootPath)).forEach(path -> builder.append(path.toString().replace(finalRootPath, "")).append("\n"));
                        } catch (Exception e) {
                            builder.append("Failed to read file tree: ").append(e.getMessage()).append("\n");
                        }

                        // previousFiles.txt
                        builder.append("Previous files: ").append("\n");
                        // read from plugin/previousFiles.txt
                        try {
                            File previousFilesFile = new File(plugin.getDataFolder(), "previousFiles.txt");
                            builder.append(Files.readString(previousFilesFile.toPath())).append("\n");
                        } catch (Exception e) {
                            builder.append("Failed to read previousFiles.txt: ").append(e.getMessage()).append("\n");
                        }

                        String url = "https://api.mclo.gs/1/log";
                        String charset = StandardCharsets.UTF_8.name();
                        String content = builder.toString();
                        String query = String.format("content=%s", URLEncoder.encode(content, charset));
                        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                        connection.setRequestMethod("POST");
                        connection.setRequestProperty("Accept-Charset", charset);
                        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + charset);
                        connection.setRequestProperty("User-Agent", "Minecraft/" + plugin.getServer().getVersion());
                        connection.setDoOutput(true);
                        try (OutputStream output = connection.getOutputStream()) {
                            output.write(query.getBytes(charset));
                        }
                        InputStream response = connection.getInputStream();
                        String responseString = new BufferedReader(new InputStreamReader(response, charset)).lines().collect(Collectors.joining("\n"));
                        JSONObject responseJson = new JSONObject(responseString);
                        if (responseJson.getBoolean("success")) {
                            sender.sendRichMessage(getMessage(
                                    "debug-success",
                                    true,
                                    new HashMap<>() {
                                        {
                                            put("url", responseJson.getString("url"));
                                        }
                                    }));
                        } else {
                            sender.sendRichMessage(getMessage(
                                    "debug-failed",
                                    true,
                                    new HashMap<>() {
                                        {
                                            put("error", responseJson.getString("error"));
                                        }
                                    }));
                        }
                    }
                    default -> {
                        if (!subCommand.equals("help")) {
                            sender.sendRichMessage(getMessage(
                                    "invalid-command",
                                    true,
                                    new HashMap<>() {{
                                        put("label", label);
                                    }}
                            ));
                        }
                        
                        sender.sendRichMessage(getMessage(
                                "help-clone",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));
                        
                        sender.sendRichMessage(getMessage(
                                "help-pull",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));
                        
                        sender.sendRichMessage(getMessage(
                                "help-push",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));
                        
                        sender.sendRichMessage(getMessage(
                                "help-add",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));
                        
                        sender.sendRichMessage(getMessage(
                                "help-remove",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));
                        
                        sender.sendRichMessage(getMessage(
                                "help-reset",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));

                        sender.sendRichMessage(getMessage(
                                "help-rollback",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));

                        sender.sendRichMessage(getMessage(
                                "help-revert",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));

                        sender.sendRichMessage(getMessage(
                                "help-script",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));

                        sender.sendRichMessage(getMessage(
                                "help-log",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));

                        sender.sendRichMessage(getMessage(
                                "help-status",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));

                        sender.sendRichMessage(getMessage(
                                "help-mute",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));

                        sender.sendRichMessage(getMessage(
                                "help-debug",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));

                        sender.sendRichMessage(getMessage(
                                "help-reload",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));

                        sender.sendRichMessage(getMessage(
                                "help-help",
                                true,
                                new HashMap<>() {{put("label", label);}}
                        ));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }
}
