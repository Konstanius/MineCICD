package ml.konstanius.minecicd;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Level;

import static ml.konstanius.minecicd.Messages.getMessage;
import static ml.konstanius.minecicd.MineCICD.plugin;

public abstract class Script {
    public static void loadDefaultScript() {
        File gitIgnoreFile = new File(new File("."), "example_script.sh");
        if (gitIgnoreFile.exists()) return;

        InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(MineCICD.plugin.getResource("example_script.sh")), StandardCharsets.UTF_8);

        Scanner scanner = new Scanner(reader);
        try {
            Files.write(gitIgnoreFile.toPath(), scanner.useDelimiter("\\A").next().getBytes());
        } catch (IOException e) {
            MineCICD.log("Failed to write example_script.txt", Level.SEVERE);
            MineCICD.logError(e);
        }
    }

    public static void run(String script) throws Exception {
        boolean ownsBusy = !MineCICD.busyLock;
        MineCICD.busyLock = true;

        String bar = MineCICD.addBar(Messages.getCleanMessage("bossbar-script", true), BarColor.BLUE, BarStyle.SOLID);
        try {
            File scriptsFolder = new File(plugin.getDataFolder(), "scripts");
            File scriptFile = new File(scriptsFolder, script + ".sh");

            List<String> lines = Files.readAllLines(scriptFile.toPath().toAbsolutePath());

            final int[] result = {-1};
            final String[] output = {""};
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (line.startsWith("#")) {
                            continue;
                        }

                        if (line.startsWith("! ")) {
                            try {
                                ProcessBuilder b = new ProcessBuilder(line.substring(3).split(" "));
                                b.inheritIO();
                                Process p = b.start();
                                result[0] = p.waitFor();
                            } catch (Exception e) {
                                int finalI = i;
                                output[0] = getMessage(
                                        "script-error-console",
                                        true,
                                        new HashMap<String, String>() {{
                                            put("script", script);
                                            put("line", String.valueOf(finalI + 1));
                                            put("command", line);
                                            put("error", e.getMessage());
                                        }}
                                );
                                result[0] = 1;
                                break;
                            }
                        } else {
                            int finalI1 = i;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                try {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
                                    result[0] = 0;
                                } catch (Exception e) {
                                    int finalI = finalI1;
                                    output[0] = getMessage(
                                            "script-error-console",
                                            true,
                                            new HashMap<String, String>() {{
                                                put("script", script);
                                                put("line", String.valueOf(finalI + 1));
                                                put("command", line);
                                                put("error", e.getMessage());
                                            }}
                                    );
                                    result[0] = 1;
                                }
                            }).getOwner();
                            if (result[0] == 1) {
                                break;
                            }
                        }

                        if (result[0] != 0) {
                            int finalI = i;
                            output[0] = getMessage(
                                    "script-error-console",
                                    true,
                                    new HashMap<String, String>() {{
                                        put("script", script);
                                        put("line", String.valueOf(finalI + 1));
                                        put("command", line);
                                        put("error", "Exited with exit code " + result[0]);
                                    }}
                            );
                            break;
                        }
                    }
                } catch (Exception e) {
                    MineCICD.logError(e);
                }
            });

            while (result[0] == -1) {
                Thread.sleep(100);
            }

            if (result[0] != 0) {
                throw new Exception(output[0]);
            }

            MineCICD.changeBar(bar, Messages.getCleanMessage("bossbar-script-success", true), BarColor.GREEN, BarStyle.SOLID);
            MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
        } catch (Exception e) {
            MineCICD.changeBar(bar, Messages.getCleanMessage("bossbar-script-failed", true), BarColor.RED, BarStyle.SEGMENTED_12);
            MineCICD.removeBar(bar, Config.getInt("bossbar.duration"));
            throw e;
        } finally {
            if (ownsBusy) {
                MineCICD.busyLock = false;
            }
        }
    }
}
