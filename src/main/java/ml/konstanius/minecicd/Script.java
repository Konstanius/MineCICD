package ml.konstanius.minecicd;

import org.bukkit.Bukkit;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static ml.konstanius.minecicd.MineCICD.busy;
import static ml.konstanius.minecicd.MineCICD.plugin;

public abstract class Script {
    public static void run(String script) throws Exception {
        boolean ownsBusy = !busy;
        busy = true;

        try {
            String path = plugin.getDataFolder().getAbsolutePath() + "/scripts/" + script + ".txt";

            // This is an example Script for running actions using WebHook Pushes
            // Comments are defined with a # at the beginning of the line
            // Running commands ingame does not require the / at the beginning of the line
            // Running commands in the system shell is done by adding "[] " at the beginning of the line
            List<String> lines = Files.readAllLines(Paths.get(path));

            // run the commands with all permissions
            var ref = new Object() {
                int result = -1;
                String output = "";
            };
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (line.startsWith("#")) {
                            continue;
                        }

                        if (line.startsWith("[] ")) {
                            try {
                                ProcessBuilder b = new ProcessBuilder(line.substring(3).split(" "));
                                b.inheritIO();
                                Process p = b.start();
                                ref.result = p.waitFor();
                            } catch (Exception e) {
                                ref.output = "Script failed on line " + (i + 1) + "\nCommand: " + line + "\n" + e.getMessage();
                                ref.result = 1;
                                break;
                            }
                        } else {
                            ref.result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line) ? 0 : 1;
                        }

                        if (ref.result != 0) {
                            ref.output = "Script failed on line " + (i + 1) + "\nCommand: " + line + "\nExit code " + ref.result;
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            while (ref.result == -1) {
                Thread.sleep(100);
            }

            if (ref.result != 0) {
                throw new Exception(ref.output);
            }
        } finally {
            if (ownsBusy) {
                busy = false;
            }
        }
    }
}
