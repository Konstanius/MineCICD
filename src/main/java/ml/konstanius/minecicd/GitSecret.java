package ml.konstanius.minecicd;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

/**
 * Functionality documentation:
 * <p>
 * - Uses Git native filters, clean and smudge<br>
 * - .gitattributes file is used to specify the filter associated with each file<br>
 * - .git/config file is used to specify the individual "minecicd-replace.exe ..." commands for each filter<br>
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

    public static void configureGitSecretFiltering(HashMap<String, ArrayList<GitSecret>> secrets) throws IOException, InterruptedException {
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

        // Check if sed is installed
        boolean sedInstalled = false;
        if (!SystemUtils.IS_OS_WINDOWS) {
            Process process = Runtime.getRuntime().exec("which sed");
            process.waitFor();
            sedInstalled = process.exitValue() == 0;
        }

        for (String filePath : secrets.keySet()) {
            if (secrets.get(filePath).isEmpty()) {
                continue;
            }

            gitConfigLines.add("[filter \"" + filePath + "\"]");

            StringBuilder cleanCommand = new StringBuilder("\tclean = ");
            StringBuilder smudgeCommand = new StringBuilder("\tsmudge = ");
            if (SystemUtils.IS_OS_WINDOWS) {
                cleanCommand.append(".\\\\minecicd_tools\\\\windows-replace.exe");
                smudgeCommand.append(".\\\\minecicd_tools\\\\windows-replace.exe");
            } else {
                if (sedInstalled) {
                    cleanCommand.append(" sed");
                    smudgeCommand.append(" sed");
                } else {
                    cleanCommand.append(" ./minecicd_tools/linux-replace.exe");
                    smudgeCommand.append(" ./minecicd_tools/linux-replace.exe");
                }
            }

            if (!sedInstalled) {
                for (GitSecret secret : secrets.get(filePath)) {
                    String base64Secret = Base64.getEncoder().encodeToString(secret.secret.getBytes());
                    String base64Identifier = Base64.getEncoder().encodeToString(("{{" + secret.identifier + "}}").getBytes());

                    cleanCommand.append(" ").append(base64Secret).append(" ").append(base64Identifier);
                    smudgeCommand.append(" ").append(base64Identifier).append(" ").append(base64Secret);
                }
            } else {
                for (GitSecret secret : secrets.get(filePath)) {
                    cleanCommand.append(" -e 's/").append(secret.secret).append("/{{").append(secret.identifier).append("}}/g'");
                    smudgeCommand.append(" -e 's/{{").append(secret.identifier).append("}}/").append(secret.secret).append("/g'");
                }
            }

            gitConfigLines.add(cleanCommand.toString());
            gitConfigLines.add(smudgeCommand.toString());
        }

        gitConfigLines.add("");

        Files.write(gitConfigFile.toPath(), gitConfigLines);

        if (!SystemUtils.IS_OS_WINDOWS && sedInstalled) {
            return;
        }

        // load the appropriate replace executable into the minecicd_tools directory
        File minecicdToolsDir = new File(".", "minecicd_tools");
        if (!minecicdToolsDir.exists()) {
            minecicdToolsDir.mkdir();
        }

        File replaceExecutable = new File(minecicdToolsDir, SystemUtils.IS_OS_WINDOWS ? "windows-replace.exe" : "linux-replace.exe");
        if (replaceExecutable.exists()) {
            return;
        }

        InputStream is = MineCICD.plugin.getResource((SystemUtils.IS_OS_WINDOWS ? "windows-replace.exe" : "linux-replace.exe"));
        if (is == null) {
            throw new IOException("Could not load the MineCICD replace executable");
        }

        Files.copy(is, replaceExecutable.toPath());
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
