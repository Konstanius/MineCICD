package ml.konstanius.minecicd;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public abstract class Messages {
    public static HashMap<String, String> messages = new HashMap<>();

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

    public static String getCleanMessage(String message, boolean prefixed) {
        String msg = getMessage(message, prefixed);
        msg = msg.replaceAll("(&[0-9a-fk-or])", "");
        return msg;
    }

    public static String getCleanMessage(String message,boolean prefixed,  Map<String, String> variables) {
        String msg = getMessage(message, prefixed, variables);
        msg = msg.replaceAll("(&[0-9a-fk-or])", "");
        return msg;
    }

    public static BaseComponent[] getRichMessage(String message, boolean prefixed) {
        String fullMsg = getMessage(message, prefixed);
        return messageToComponent(fullMsg);
    }

    public static BaseComponent[] getRichMessage(String message, boolean prefixed, Map<String, String> variables) {
        String fullMsg = getMessage(message, prefixed, variables);
        return messageToComponent(fullMsg);
    }

    public static BaseComponent[] messageToComponent(String message) {
        ComponentBuilder builder = new ComponentBuilder("");

        StringBuilder current = new StringBuilder();
        ChatColor currentColor = ChatColor.WHITE;
        ArrayList<ChatColor> formatStack = new ArrayList<>();
        boolean escape = false;
        for (int i = 0; i < message.length(); i++) {
            // if it is an &
            if (message.charAt(i) == '&' && escape) {
                current.append("&");
                escape = false;
                continue;
            }

            // if it is a backslash escape character
            if (message.charAt(i) == '\\') {
                if (escape) {
                    current.append("\\");
                    escape = false;
                } else {
                    escape = true;
                }
                continue;
            }

            // if it is a color code
            if (message.charAt(i) == '&') {
                if (i < message.length() - 1) {
                    ChatColor color = colorReplacers.get("&" + message.charAt(i + 1));
                    if (color != null) {
                        if (current.length() > 0) {
                            String currentString = current.toString();
                            TextComponent textComponent = new TextComponent(currentString);
                            textComponent.setColor(currentColor);
                            for (ChatColor format : formatStack) {
                                textComponent.setObfuscated(format == ChatColor.MAGIC);
                                textComponent.setBold(format == ChatColor.BOLD);
                                textComponent.setStrikethrough(format == ChatColor.STRIKETHROUGH);
                                textComponent.setUnderlined(format == ChatColor.UNDERLINE);
                                textComponent.setItalic(format == ChatColor.ITALIC);
                            }
                            builder.append(textComponent);
                            current = new StringBuilder();
                        }
                        currentColor = color;
                        i++;
                        continue;
                    }

                    ChatColor format = formatReplacers.get("&" + message.charAt(i + 1));
                    if (format != null) {
                        if (current.length() > 0) {
                            String currentString = current.toString();
                            TextComponent textComponent = new TextComponent(currentString);
                            textComponent.setColor(currentColor);
                            for (ChatColor f : formatStack) {
                                textComponent.setObfuscated(f == ChatColor.MAGIC);
                                textComponent.setBold(f == ChatColor.BOLD);
                                textComponent.setStrikethrough(f == ChatColor.STRIKETHROUGH);
                                textComponent.setUnderlined(f == ChatColor.UNDERLINE);
                                textComponent.setItalic(f == ChatColor.ITALIC);
                            }
                            builder.append(textComponent);
                            current = new StringBuilder();
                        }
                        formatStack.add(format);
                        i++;
                        continue;
                    }

                    if (message.charAt(i + 1) == 'r') {
                        if (current.length() > 0) {
                            String currentString = current.toString();
                            TextComponent textComponent = new TextComponent(currentString);
                            textComponent.setColor(currentColor);
                            for (ChatColor f : formatStack) {
                                textComponent.setObfuscated(f == ChatColor.MAGIC);
                                textComponent.setBold(f == ChatColor.BOLD);
                                textComponent.setStrikethrough(f == ChatColor.STRIKETHROUGH);
                                textComponent.setUnderlined(f == ChatColor.UNDERLINE);
                                textComponent.setItalic(f == ChatColor.ITALIC);
                            }
                            builder.append(textComponent);
                            current = new StringBuilder();
                        }
                        currentColor = ChatColor.WHITE;
                        formatStack.clear();
                        i++;

                        TextComponent textComponent = new TextComponent("");
                        textComponent.setColor(currentColor);
                        textComponent.setObfuscated(false);
                        textComponent.setBold(false);
                        textComponent.setStrikethrough(false);
                        textComponent.setUnderlined(false);
                        textComponent.setItalic(false);
                        builder.append(textComponent);
                        continue;
                    }
                }

                current.append(message.charAt(i));
                continue;
            }
            current.append(message.charAt(i));
            escape = false;
        }

        if (current.length() > 0) {
            String currentString = current.toString();
            TextComponent textComponent = new TextComponent(currentString);
            textComponent.setColor(currentColor);
            for (ChatColor format : formatStack) {
                textComponent.setObfuscated(format == ChatColor.MAGIC);
                textComponent.setBold(format == ChatColor.BOLD);
                textComponent.setStrikethrough(format == ChatColor.STRIKETHROUGH);
                textComponent.setUnderlined(format == ChatColor.UNDERLINE);
                textComponent.setItalic(format == ChatColor.ITALIC);
            }
            builder.append(textComponent);
        }

        return builder.create();
    }

    public static void loadMessages() {
        try {
            messages.clear();
            YamlConfiguration pluginMessagesResource = new YamlConfiguration();
            pluginMessagesResource.load(new InputStreamReader(Objects.requireNonNull(MineCICD.plugin.getResource("messages.yml")), StandardCharsets.UTF_8));

            File pluginMessagesFile = new File(MineCICD.plugin.getDataFolder().getAbsolutePath(), "messages.yml");
            if (!pluginMessagesFile.exists()) {
                MineCICD.plugin.saveResource("messages.yml", false);
            }

            FileConfiguration messagesConfig = new YamlConfiguration();
            messagesConfig.load(pluginMessagesFile);

            for (String key : messagesConfig.getKeys(false)) {
                Object value = messagesConfig.get(key);
                if (value instanceof String) {
                    messages.put(key, (String) value);
                } else if (value instanceof ArrayList) {
                    ArrayList<String> list = (ArrayList<String>) value;
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        builder.append(list.get(i));
                        if (i < list.size() - 1) {
                            builder.append("\n");
                        }
                    }
                    messages.put(key, builder.toString());
                } else {
                    MineCICD.log("Invalid message format at key: " + key, Level.WARNING);
                }
            }

            // check if any keys are missing
            for (String key : pluginMessagesResource.getKeys(false)) {
                if (!messages.containsKey(key)) {
                    MineCICD.log("Missing message key: " + key, Level.WARNING);
                    messages.put(key, pluginMessagesResource.getString(key));
                }
            }
        } catch (IOException | InvalidConfigurationException e) {
            MineCICD.log("Failed to load messages", Level.SEVERE);
            MineCICD.logError(e);
        }
    }

    // replace &0 - &9, &a - &f, &k, &l, &m, &n, &o, &r with ChatComponent
    public static HashMap<String, ChatColor> colorReplacers = new HashMap<String, ChatColor>() {{
        put("&0", ChatColor.BLACK);
        put("&1", ChatColor.DARK_BLUE);
        put("&2", ChatColor.DARK_GREEN);
        put("&3", ChatColor.DARK_AQUA);
        put("&4", ChatColor.DARK_RED);
        put("&5", ChatColor.DARK_PURPLE);
        put("&6", ChatColor.GOLD);
        put("&7", ChatColor.GRAY);
        put("&8", ChatColor.DARK_GRAY);
        put("&9", ChatColor.BLUE);
        put("&a", ChatColor.GREEN);
        put("&b", ChatColor.AQUA);
        put("&c", ChatColor.RED);
        put("&d", ChatColor.LIGHT_PURPLE);
        put("&e", ChatColor.YELLOW);
        put("&f", ChatColor.WHITE);
    }};

    public static HashMap<String, ChatColor> formatReplacers = new HashMap<String, ChatColor>() {{
        put("&k", ChatColor.MAGIC);
        put("&l", ChatColor.BOLD);
        put("&m", ChatColor.STRIKETHROUGH);
        put("&n", ChatColor.UNDERLINE);
        put("&o", ChatColor.ITALIC);
    }};
}
