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

import java.io.*;
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
    public static HashSet<String> scripts = new HashSet<>(); // Caches the scripts in the scripts directory
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

            // check if pluginFolder/scripts exists
            File scriptsFolder = new File(plugin.getDataFolder().getAbsolutePath() + "/scripts");
            if (!scriptsFolder.exists()) {
                try {
                    scriptsFolder.mkdir();

                    // copy example_script.txt from resources to pluginFolder/scripts
                    InputStream in = plugin.getResource("example_script.txt");
                    OutputStream out = null;
                    try {
                        out = new FileOutputStream(plugin.getDataFolder().getAbsolutePath() + "/scripts/example_script.txt");
                    } catch (FileNotFoundException e) {
                        File file = new File(plugin.getDataFolder().getAbsolutePath() + "/scripts/example_script.txt");
                        file.createNewFile();
                        out = new FileOutputStream(file);
                    }

                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }

                    in.close();
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Messages.loadMessages();

        bossBar.setVisible(false);
        // continuous async timer to display BossBar when busy and hide when not
        if (Config.getBoolean("bossbar")) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, task -> {
                bossBar.setVisible(true);
                if (busy) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.hasPermission("minecicd.notify") && !muteList.contains(p.getUniqueId().toString())) {
                            if (!bossBar.getPlayers().contains(p)) {
                                bossBar.addPlayer(p);
                            }
                        } else {
                            bossBar.removePlayer(p);
                        }
                    }
                } else {
                    bossBar.removeAll();
                }
            }, 20, 5);
        }
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.stop(0);
            log("MineCICD stopped listening.", Level.INFO);
        }
    }

    public static void log(String l, Level level) {
        logger.log(level, l);
    }
}
