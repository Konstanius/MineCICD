package ml.konstanius.minecicd;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import static ml.konstanius.minecicd.MineCICD.muteList;

public abstract class Messages {
    public static HashMap<String, String> messages = new HashMap<>();

    public static void sendManagementMessage(CommandSender sender) {
        sender.sendRichMessage(Messages.getMessage("management-message", false));
    }

    public static void presentWebhookCommit(String author, String message, String mainAction, String commit, ArrayList<String> commands, ArrayList<String> scripts) {
        String header = Messages.getMessage("webhook-header", false);
        String footer = Messages.getMessage("webhook-footer", false);

        String authorMessage = Messages.getMessage(
                "webhook-author",
                false,
                new HashMap<>() {{put("author", author);}}
        );
        String commitMessage = Messages.getMessage(
                "webhook-commit",
                false,
                new HashMap<>() {{put("commit", commit);}}
        );
        String messageMessage = Messages.getMessage(
                "webhook-message",
                false,
                new HashMap<>() {{put("message", message);}}
        );
        String mainActionMessage = Messages.getMessage(
                "webhook-main-action",
                false,
                new HashMap<>() {{put("action", mainAction);}}
        );

        ArrayList<String> commandMessages = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            int finalI = i;
            String commandMessage = Messages.getMessage(
                    "webhook-command",
                    false,
                    new HashMap<>() {{put("command", command); put("index", String.valueOf(finalI + 1));}}
            );

            commandMessages.add(commandMessage);
        }

        ArrayList<String> scriptMessages = new ArrayList<>();
        for (int i = 0; i < scripts.size(); i++) {
            String script = scripts.get(i);
            int finalI = i;
            String scriptMessage = Messages.getMessage(
                    "webhook-script",
                    false,
                    new HashMap<>() {{put("script", script); put("index", String.valueOf(finalI + 1));}}
            );

            scriptMessages.add(scriptMessage);
        }


        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("minecicd.notify") && !muteList.contains(p.getUniqueId().toString())) {
                try {
                    p.sendRichMessage(header);
                    p.sendRichMessage(authorMessage);
                    p.sendRichMessage(commitMessage);
                    p.sendRichMessage(mainActionMessage);
                    p.sendRichMessage(messageMessage);
                    for (String commandMessage : commandMessages) {
                        p.sendRichMessage(commandMessage);
                    }
                    for (String scriptMessage : scriptMessages) {
                        p.sendRichMessage(scriptMessage);
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
