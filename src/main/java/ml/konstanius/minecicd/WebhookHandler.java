package ml.konstanius.minecicd;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.logging.Level;

import static ml.konstanius.minecicd.MineCICD.busy;
import static ml.konstanius.minecicd.MineCICD.log;

public class WebhookHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange t) throws IOException {
        StringBuilder sb = new StringBuilder();
        InputStream ios = t.getRequestBody();
        int i;
        int max = 10000;
        while ((i = ios.read()) != -1 && max-- > 0) {
            sb.append((char) i);
        }

        if (max <= 0) {
            t.sendResponseHeaders(400, 0);
            return;
        }

        String response = "Response";
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();

        JSONObject json = new JSONObject(sb.toString());

        // handle the webhook event
        String branch = Config.getString("branch");
        if (!json.getString("ref").equals("refs/heads/" + branch)) {
            return;
        }

        String pusher = json.getJSONObject("pusher").getString("name");
        String message = json.getJSONObject("head_commit").getString("message");

        Bukkit.getScheduler().runTaskTimerAsynchronously(MineCICD.plugin, task -> {
            if (busy) {
                return;
            }

            MineCICD.bossBar.setTitle("MineCICD: Processing WebHook..:");

            try {
                log("Received webhook from " + pusher + " with message: " + message, Level.INFO);

                boolean updated = GitManager.pullRepo();
                if (!updated) {
                    return;
                }

                FilesManager.mergeToLocal();

                boolean allowIndividualReload = Config.getBoolean("allow-individual-reload");
                boolean allowGlobalReload = Config.getBoolean("allow-global-reload");
                boolean allowRestart = Config.getBoolean("allow-restart");
                boolean allowScripts = Config.getBoolean("allow-scripts");

                String[] lines = message.split("\n");
                ArrayList<String> individualReload = new ArrayList<>();
                ArrayList<String> commands = new ArrayList<>();
                ArrayList<String> scripts = new ArrayList<>();
                boolean globalReload = false;
                boolean restart = false;
                for (String line : lines) {
                    if (line.startsWith("CICD")) {
                        String command = line.substring(4).trim();
                        if (command.startsWith("reload") && allowIndividualReload) {
                            String plugin = line.substring(10).trim();
                            individualReload.add(plugin);
                        } else if (command.startsWith("global-reload")) {
                            globalReload = allowGlobalReload;
                        } else if (command.startsWith("restart")) {
                            restart = allowRestart;
                        } else if (command.startsWith("run")) {
                            String cmd = line.substring(7).trim();
                            commands.add(cmd);
                        } else if (command.startsWith("script")) {
                            String script = line.substring(10).trim();
                            scripts.add(script);
                        }
                    }
                }

                String mainAction;
                if (restart) {
                    mainAction = "Server Restart";
                } else if (globalReload) {
                    mainAction = "Global Reload";
                } else if (!individualReload.isEmpty()) {
                    mainAction = "Plugin Reload";
                } else {
                    mainAction = "No Action";
                }
                String commit = json.getJSONObject("head_commit").getString("id");
                Messages.presentWebhookCommit(pusher, message, mainAction, commit, commands);

                // run the commands with all permissions
                for (String cmd : commands) {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sudo " + cmd);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // run the scripts
                if (allowScripts) {
                    for (String script : scripts) {
                        try {
                            Script.run(script);
                        } catch (Exception e) {
                            e.printStackTrace();

                            // TODO notify about failure of scripts
                        }
                    }
                }

                if (restart) {
                    Bukkit.shutdown();
                } else if (globalReload) {
                    Bukkit.reload();
                } else if (!individualReload.isEmpty()) {
                    for (String plugin : individualReload) {
                        Plugin pl = Bukkit.getPluginManager().getPlugin(plugin);
                        if (pl == null) {
                            continue;
                        }
                        try {
                            Bukkit.getPluginManager().disablePlugin(pl);
                            Bukkit.getPluginManager().enablePlugin(pl);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            task.cancel();
        }, 0, 5);
    }
}
