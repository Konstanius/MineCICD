package ml.konstanius.minecicd;

import org.apache.commons.io.FileUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Functionality documentation:
 * <p>
 * - Uses Git native filters, clean and smudge<br>
 * - .gitattributes file is used to specify the filter associated with each file<br>
 * - .git/config file is used to specify the individual "sed ..." commands for each filter<br>
 * - secrets cannot contain the single quote character (" ' ")<br>
 * - each file can only have one filter, but each sed command may replace multiple placeholders<br>
 * - the name of the filter in the .git/config and .gitattributes files will be the same as the relative file path that it is applied to<br>
 * <p>
 * Each secret has the following:
 * - A unique identifier, which is the same as its {{identifier}} placeholder will be<br>
 * - A file that it is associated with<br>
 * - A secret that it will replace in the file
 */
public class GitSecret {
    public static HashMap<String, ArrayList<GitSecret>> readFromSecretsStore() throws IOException, InvalidConfigurationException {
        File secretsFile = new File(".", "secrets.yml");
        HashMap<String, ArrayList<GitSecret>> secrets = new HashMap<>();
        if (!secretsFile.exists()) {
            Files.write(secretsFile.toPath(), (
                    "1:\n" +
                            "  file: \"plugins/example-plugin-1/config.yml\"\n" +
                            "  database_password: \"password\"\n" +
                            "  database_username: \"username\"\n" +
                            "2:\n" +
                            "  file: \"plugins/example-plugin-2/config.yml\"\n" +
                            "  license_key: \"license_key\""
            ).getBytes());
        }

        FileConfiguration secretsConfig = new YamlConfiguration();
        secretsConfig.load(secretsFile);

        for (String index : secretsConfig.getKeys(false)) {
            ArrayList<GitSecret> secretsList = new ArrayList<>();

            HashSet<String> secretsForThisFile = new HashSet<>();

            ConfigurationSection section = secretsConfig.getConfigurationSection(index);
            if (section == null) {
                throw new InvalidConfigurationException("Secrets must be in a configuration section");
            }

            String filePath = section.getString("file");
            if (filePath == null) {
                throw new InvalidConfigurationException("Secrets must have a file");
            }
            if (filePath.contains("'")) {
                throw new InvalidConfigurationException("Secrets file paths cannot contain the single quote character");
            }
            if (filePath.contains(" ")) {
                throw new InvalidConfigurationException("Secrets file paths cannot contain spaces");
            }

            Set<String> keys = section.getKeys(false);

            for (String secretIdentifier : keys) {
                if (secretIdentifier.equals("file")) {
                    continue;
                }
                String secret = section.getString(secretIdentifier);

                if (secret == null) {
                    throw new InvalidConfigurationException("Secrets must have a value");
                }
                if (secretIdentifier.contains("'") || secret.contains("'")) {
                    throw new InvalidConfigurationException("Secrets and their identifier cannot contain the single quote character");
                }
                if (secretsForThisFile.contains(secretIdentifier)) {
                    throw new InvalidConfigurationException("Secrets must have unique identifiers");
                }
                secretsForThisFile.add(secretIdentifier);

                secretsList.add(new GitSecret(secretIdentifier, filePath, secret));
            }
            secrets.put(filePath, secretsList);
        }

        return secrets;
    }

    public static void configureGitSecretFiltering(HashMap<String, ArrayList<GitSecret>> secrets) throws IOException {
        File gitConfigFile = new File(new File(".", ".git"), "config");
        if (!GitUtils.activeRepoExists()) {
            return;
        }

        File gitAttributesFile = new File(".", ".gitattributes");
        if (gitAttributesFile.exists()) {
            FileUtils.deleteQuietly(gitAttributesFile);
        }

        StringBuilder gitAttributesContent = new StringBuilder();
        for (String filePath : secrets.keySet()) {
            gitAttributesContent.append(filePath).append(" filter=").append(filePath).append("\n");
        }
        Files.write(gitAttributesFile.toPath(), gitAttributesContent.toString().getBytes());

        ArrayList<String> gitConfigLines = new ArrayList<>(Files.readAllLines(gitConfigFile.toPath()));
        boolean inFilterSection = false;
        for (int i = 0; i < gitConfigLines.size(); i++) {
            String line = gitConfigLines.get(i);
            if (line.contains("[filter")) {
                inFilterSection = true;
            } else if (inFilterSection && line.contains("[")) {
                inFilterSection = false;
            }
            if (inFilterSection) {
                gitConfigLines.remove(i);
                i--;
            }
        }

        for (String filePath : secrets.keySet()) {
            gitConfigLines.add("[filter \"" + filePath + "\"]");

            StringBuilder cleanCommand = new StringBuilder("\tclean = sed ");
            StringBuilder smudgeCommand = new StringBuilder("\tsmudge = sed ");

            for (GitSecret secret : secrets.get(filePath)) {
                cleanCommand.append("-e 's/").append(secret.secret).append("/{{").append(secret.identifier).append("}}/g' ");
                smudgeCommand.append("-e 's/{{").append(secret.identifier).append("}}/").append(secret.secret).append("/g' ");
            }

            gitConfigLines.add(cleanCommand.toString());
            gitConfigLines.add(smudgeCommand.toString());
        }

        gitConfigLines.add("");

        Files.write(gitConfigFile.toPath(), gitConfigLines);
    }

    public GitSecret(String identifier, String file, String secret) {
        this.identifier = identifier;
        this.file = file;
        this.secret = secret;
    }

    public String identifier;
    public String file;
    public String secret;
}
