package ml.konstanius.minecicd;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MineCICD extends JavaPlugin {
    public static FileConfiguration config;
    public static Logger logger = Bukkit.getLogger();
    public static HttpServer webServer;
    public static boolean busy = false;
    public static Plugin plugin;

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

                log("GitMcSync started listening on: http://" + ip + ":" + port + "/" + webhookPath, Level.INFO);
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

        try {
            GitManager.checkoutBranch();

            boolean changes = GitManager.pullRepo();
            if (changes) {
                FilesManager.mergeToLocal();
            }
        } catch (IOException | GitAPIException ignored) {
        }
    }

    @Override
    public void onDisable() {
        webServer.stop(0);
        log("GitMcSync stopped listening.", Level.INFO);
    }

    public static void log(String l, Level level) {
        logger.log(level, l);
    }
}
