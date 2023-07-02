package ml.konstanius.minecicd;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

import static ml.konstanius.minecicd.MineCICD.muteList;

public abstract class Messages {
    public static HashMap<String, String> messages = new HashMap<>();

    public static void sendManagementMessage(CommandSender sender) {
        // includes:
        // - restart server (yellow, propose)
        // - run reload (red, propose)
        // - list plugins to reload (green, run)

        String restart = "<yellow><u><bold><click:suggest_command:/restart>[Restart Server]</click></bold></u></yellow> ";
        String reload = "<red><u><bold><click:suggest_command:/reload confirm>[Reload Server]</click></bold></u></red> ";
        String list = "<green><u><bold><click:suggest_command:/pl>[List Plugins]</click></bold></u></green>";

        sender.sendRichMessage(restart + reload + list);
    }

    public static void presentWebhookCommit(String pusher, String message, String mainAction, String commit, ArrayList<String> commands) {
        String header = "<gold><bold>========== MineCICD Webhook Push ==========</bold></gold>";
        String footer = "<gold><bold>============================================</bold></gold>";

        String pusherMessage = "<gray><italic>Pusher: </italic></gray><white>" + pusher + "</white>";
        String messageMessage = "<gray><italic>Message: </italic></gray><white>" + message + "</white>";
        String commitMessage = "<gray><italic>Commit: </italic></gray><white>" + commit + "</white>";
        String actionMessage = "<gray><italic>Action: </italic></gray><white>" + mainAction + "</white>";

        ArrayList<String> commandMessages = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            commandMessages.add("<gray><italic>Command (" + (i + 1) + "): </italic></gray><white>" + command + "</white>");
        }


        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("minecicd.notify") && !muteList.contains(p.getUniqueId().toString())) {
                try {
                    p.sendRichMessage(header);
                    p.sendRichMessage(pusherMessage);
                    p.sendRichMessage(messageMessage);
                    p.sendRichMessage(commitMessage);
                    p.sendRichMessage(actionMessage);
                    for (String commandMessage : commandMessages) {
                        p.sendRichMessage(commandMessage);
                    }
                    p.sendRichMessage(footer);

                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                    Bukkit.getScheduler().runTaskLater(MineCICD.plugin, () -> p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f), 5L);
                    Bukkit.getScheduler().runTaskLater(MineCICD.plugin, () -> p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f), 10L);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static String getMessage(String message, boolean prefixed) {
        String msg = messages.getOrDefault(message, message);
        if (prefixed) {
            String prefix = messages.getOrDefault("prefix", "");
            msg = prefix + msg;

            msg = msg.replace("{prefix}", prefix);
        }

        return msg;
    }

    public static String getMessage(String message, boolean prefixed, Map<String, String> variables) {
        String msg = getMessage(message, prefixed);

        for (String key : variables.keySet()) {
            msg = msg.replace("{" + key + "}", variables.get(key));
        }

        return msg;
    }

    public static void loadMessages() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.load(new InputStreamReader(Objects.requireNonNull(MineCICD.plugin.getResource("messages.yml")), StandardCharsets.UTF_8));

            File messagesFile = new File(MineCICD.plugin.getDataFolder().getAbsolutePath() + "/messages.yml");
            if (!messagesFile.exists()) {
                MineCICD.plugin.saveResource("messages.yml", false);
            }

            FileConfiguration messagesConfig = new YamlConfiguration();
            messagesConfig.load(messagesFile);

            for (String key : messagesConfig.getKeys(false)) {
                String value = messagesConfig.getString(key);
                messages.put(key, translateCodes(value));
            }

            // check if any keys are missing
            for (String key : config.getKeys(false)) {
                if (!messages.containsKey(key)) {
                    MineCICD.log("Missing message key: " + key, Level.WARNING);
                    // add it to the end of the file
                    messages.put(key, translateCodes(config.getString(key)));
                }
            }
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    // replace &0 - &9, &a - &f, &k, &l, &m, &n, &o, &r with miniMessage <type>
    public static HashMap<String, String> replacers = new HashMap<>() {{
        put("&0", "<black>");
        put("&1", "<dark_blue>");
        put("&2", "<dark_green>");
        put("&3", "<dark_aqua>");
        put("&4", "<dark_red>");
        put("&5", "<dark_purple>");
        put("&6", "<gold>");
        put("&7", "<gray>");
        put("&8", "<dark_gray>");
        put("&9", "<blue>");
        put("&a", "<green>");
        put("&b", "<aqua>");
        put("&c", "<red>");
        put("&d", "<light_purple>");
        put("&e", "<yellow>");
        put("&f", "<white>");
        put("&k", "<obfuscated>");
        put("&l", "<bold>");
        put("&m", "<strikethrough>");
        put("&n", "<underlined>");
        put("&o", "<italic>");
        put("&r", "<reset>");
    }};

    private static String translateCodes(String value) {
        for (String key : replacers.keySet()) {
            value = value.replace(key, replacers.get(key));
        }

        return value;
    }
}
