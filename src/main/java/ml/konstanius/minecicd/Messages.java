package ml.konstanius.minecicd;

import org.bukkit.command.CommandSender;

public abstract class Messages {
    public static void sendManagementMessage(CommandSender sender) {
        // includes:
        // - restart server (yellow, propose)
        // - run reload (red, propose)
        // - list plugins to reload (green, run)

        String restart = "<yellow><u><bold><click:suggest_command:/restart>[Restart Server]</click></bold></u></yellow> ";
        String reload = "<red><u><bold><click:suggest_command:/reload confirm>[Reload Server]</click></bold></u></red> ";
        String list = "<green><u><bold><click:suggest_command:/pl>[List Plugins]</click></bold></u></green>";

        sender.sendRichMessage(restart + reload + list);
    }
}
