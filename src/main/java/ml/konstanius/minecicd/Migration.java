package ml.konstanius.minecicd;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Level;

public class Migration {
    public static void migrate() throws IOException, InvalidConfigurationException {
        int fileConfigVersion = MineCICD.config.getInt("version");

        YamlConfiguration pluginConfig = new YamlConfiguration();
        pluginConfig.load(new InputStreamReader(Objects.requireNonNull(MineCICD.plugin.getResource("config.yml")), StandardCharsets.UTF_8));
        int pluginConfigVersion = pluginConfig.getInt("version");

        if (fileConfigVersion < pluginConfigVersion) {
            if (fileConfigVersion < 20200) {
                MineCICD.log("MineCICD cannot self migrate from PRE-2.2.0 Versions.", Level.SEVERE);
                MineCICD.log("Please change or regenerate your .gitignore file (See https://github.com/Konstanius/MineCICD/blob/master/src/main/resources/.gitignore)", Level.SEVERE);
                MineCICD.log("Then delete the config.yml and restart the server.", Level.SEVERE);
                MineCICD.log("MineCICD will now disable itself.", Level.SEVERE);
                MineCICD.plugin.getPluginLoader().disablePlugin(MineCICD.plugin);
                return;
            }
        }
    }
}
