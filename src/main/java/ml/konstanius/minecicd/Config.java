package ml.konstanius.minecicd;

public abstract class Config {
    public static String getString(String path) {
        return MineCICD.config.getString(path);
    }

    public static int getInt(String path) {
        return MineCICD.config.getInt(path);
    }

    public static boolean getBoolean(String path) {
        return MineCICD.config.getBoolean(path);
    }

    public static void set(String path, Object value) {
        MineCICD.config.set(path, value);
        save();
    }

    public static void save() {
        MineCICD.plugin.saveConfig();
        reload();
    }

    public static void reload() {
        MineCICD.plugin.reloadConfig();
        MineCICD.config = MineCICD.plugin.getConfig();
    }
}
