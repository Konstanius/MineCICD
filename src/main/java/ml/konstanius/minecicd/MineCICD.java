package ml.konstanius.minecicd;

import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.eclipse.jgit.api.Git;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MineCICD extends JavaPlugin {
    public static FileConfiguration config;
    public static Logger logger = Logger.getLogger("MineCICD");
    public static Plugin plugin;
    public static HttpServer webServer;
    public static HashMap<String, BossBar> busyBars = new HashMap<>();
    public static boolean busyLock = false;

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        config = getConfig();

        if (config.get("repository-url") != null) {
            File configFile = new File(getDataFolder(), "config.yml");
            configFile.renameTo(new File(getDataFolder(), "config_old.yml"));

            File messagesFile = new File(getDataFolder(), "messages.yml");
            messagesFile.renameTo(new File(getDataFolder(), "messages_old.yml"));

            // delete all files in the data folder except for the old config and messages files
            File[] files = getDataFolder().listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.getName().equals("config_old.yml") && !file.getName().equals("messages_old.yml")) {
                        FileUtils.deleteQuietly(file);
                    }
                }
            }

            reload();
        } else {
            GitUtils.loadGitIgnore();
            Messages.loadMessages();
            Script.loadDefaultScript();

            try {
                GitSecret.configureGitSecretFiltering(GitSecret.readFromSecretsStore());
            } catch (IOException | InvalidConfigurationException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            setupWebHook();
        }

        Objects.requireNonNull(this.getCommand("minecicd")).setExecutor(new BaseCommand());
        Objects.requireNonNull(this.getCommand("minecicd")).setTabCompleter(new BaseCommandTabCompleter());

        try (Git ignored = Git.open(new File("."))) {
        } catch (Exception ignored) {
        }
    }

    public static void setupWebHook() {
        int port = config.getInt("webhooks.port");
        String path = config.getString("webhooks.path");
        if (port != 0) {
            try {
                if (webServer != null) {
                    webServer.stop(0);
                    webServer = null;
                }

                String serverIp;
                try {
                    URL whatismyip = new URL("https://checkip.amazonaws.com");
                    BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
                    serverIp = in.readLine();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                webServer = HttpServer.create(new InetSocketAddress(port), 0);
                webServer.createContext("/" + path, new WebhookHandler());
                webServer.setExecutor(null);
                webServer.start();

                log("MineCICD is now listening on: \"http://" + serverIp + ":" + port + "/" + path + "\"", Level.INFO);
            } catch (IOException e) {
                logError(e);
            }
        } else {
            if (webServer != null) {
                webServer.stop(0);
                webServer = null;
            }
        }
    }

    public static void reload() {
        plugin.saveDefaultConfig();
        Config.reload();
        Messages.loadMessages();
        GitUtils.loadGitIgnore();
        Script.loadDefaultScript();

        try {
            GitSecret.configureGitSecretFiltering(GitSecret.readFromSecretsStore());
        } catch (IOException | InvalidConfigurationException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        setupWebHook();
    }

    @Override
    public void onDisable() {
        for (String type : busyBars.keySet()) {
            removeBar(type, 0);
        }

        if (webServer != null) {
            webServer.stop(0);
            log("MineCICD stopped listening.", Level.INFO);
        }
    }

    public static void log(String l, Level level) {
        logger.log(level, l);
    }

    public static void logError(Exception e) {
        logger.log(Level.SEVERE, e.getMessage(), e);
        StringBuilder stackTrace = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            stackTrace.append(element.toString()).append("\n");
        }
        logger.log(Level.SEVERE, stackTrace.toString());
    }

    public static String addBar(String title, BarColor color, BarStyle style) {
        if (!Config.getBoolean("bossbar.enabled")) return "";
        String random = String.valueOf(System.currentTimeMillis());

        MineCICD.busyBars.put(random, Bukkit.createBossBar(title, color, style));

        ArrayList<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.removeIf(player -> !player.hasPermission("minecicd.notify"));

        for (Player player : players) {
            MineCICD.busyBars.get(random).addPlayer(player);
        }

        return random;
    }

    public static void changeBar(String type, String title, BarColor color, BarStyle style) {
        if (!Config.getBoolean("bossbar.enabled")) return;
        if (!MineCICD.busyBars.containsKey(type)) return;

        MineCICD.busyBars.get(type).setTitle(title);
        MineCICD.busyBars.get(type).setColor(color);
        MineCICD.busyBars.get(type).setStyle(style);
    }

    public static void removeBar(String type, int delay) {
        if (!Config.getBoolean("bossbar.enabled")) return;
        if (!MineCICD.busyBars.containsKey(type)) return;

        if (delay > 0) {
            BossBar bar = MineCICD.busyBars.get(type);
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(MineCICD.plugin, () -> {
                double currentProgress = bar.getProgress();
                currentProgress -= (1.0 / (double) delay);
                if (currentProgress < 0) {
                    currentProgress = 0;
                }
                bar.setProgress(currentProgress);
            }, 1, 1);
            Bukkit.getScheduler().runTaskLater(MineCICD.plugin, () -> {
                MineCICD.busyBars.get(type).removeAll();
                MineCICD.busyBars.remove(type);
                task.cancel();
            }, delay);
        } else {
            MineCICD.busyBars.get(type).removeAll();
            MineCICD.busyBars.remove(type);
        }
    }
}
