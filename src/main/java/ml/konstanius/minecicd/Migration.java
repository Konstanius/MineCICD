package ml.konstanius.minecicd;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Migration {
    public static void migrate() throws IOException, InvalidConfigurationException {
        int fileConfigVersion = MineCICD.config.getInt("version");

        YamlConfiguration pluginConfig = new YamlConfiguration();
        pluginConfig.load(new InputStreamReader(Objects.requireNonNull(MineCICD.plugin.getResource("config.yml")), StandardCharsets.UTF_8));
        int pluginConfigVersion = pluginConfig.getInt("version");

        if (fileConfigVersion < pluginConfigVersion) {
            if (fileConfigVersion < 20200) {
                // TODO migrate to 2.2.0
            }
        }
    }
}
