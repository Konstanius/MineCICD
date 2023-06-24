package ml.konstanius.minecicd;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MineCICD extends JavaPlugin {
    public static FileConfiguration config;
    public static Logger logger = Bukkit.getLogger();
    public static HttpServer webServer;
    public static boolean busy = false;
    public static Plugin plugin;
    public static HashSet<String> muteList = new HashSet<>();
    public static int logSize = 0; // Caches the amount of commits
    public static String[] commitLog = new String[0]; // Caches the commit revision hashes
    public static HashSet<String> repoFiles = new HashSet<>(); // Caches the files and paths in the repo
    public static HashSet<String> localFiles = new HashSet<>(); // Caches the files and paths in the local directory
    public static BossBar bossBar = Bukkit.createBossBar("MineCICD", BarColor.BLUE, BarStyle.SOLID);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        plugin = this;

        try {
            int port = Config.getInt("webhook-port");
            if (port != 0) {
                URL whatismyip = new URL("https://checkip.amazonaws.com");
                BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
                String ip = in.readLine();

                String webhookPath = Config.getString("webhook-path");
                if (webhookPath.startsWith("/")) {
                    webhookPath = webhookPath.substring(1);
                }

                webServer = HttpServer.create(new InetSocketAddress(port), 0);
                webServer.createContext("/" + webhookPath, new WebhookHandler());
                webServer.setExecutor(null);
                webServer.start();

                log("MineCICD started listening on: http://" + ip + ":" + port + "/" + webhookPath, Level.INFO);
            } else {
                log("Webhook port is not set. Please set it in config.yml.", Level.WARNING);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log("Failed to start webhook server. Please check your config.yml.", Level.SEVERE);
        }

        // TODO register events in the future

        // Register commands
        Objects.requireNonNull(this.getCommand("minecicd")).setExecutor(new BaseCommand());

        // Register tab completers
        Objects.requireNonNull(this.getCommand("minecicd")).setTabCompleter(new BaseCommandTabCompleter());



        // async load the files cache
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                GitManager.checkoutBranch();

                boolean changes = GitManager.pullRepo();
                if (changes) {
                    FilesManager.mergeToLocal();
                } else {
                    GitManager.generateTabCompleter();
                }
            } catch (IOException | GitAPIException ignored) {
            }
        });

        // continuous async timer to display bossbar when busy and hide when not
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, task -> {
            if (Config.getBoolean("bossbar")) {
                bossBar.setVisible(true);
            } else {
                bossBar.setVisible(false);
            }

            if (busy) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("minecicd.notify") && !muteList.contains(p.getUniqueId().toString())) {
                        bossBar.addPlayer(p);
                    } else {
                        bossBar.removePlayer(p);
                    }
                }
            } else {
                Bukkit.getOnlinePlayers().forEach(player -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        bossBar.removePlayer(p);
                    }
                });
            }
        }, 0, 5);
    }

    @Override
    public void onDisable() {
        webServer.stop(0);
        log("MineCICD stopped listening.", Level.INFO);
    }

    public static void log(String l, Level level) {
        logger.log(level, l);
    }
}
