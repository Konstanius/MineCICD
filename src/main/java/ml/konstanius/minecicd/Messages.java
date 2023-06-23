package ml.konstanius.minecicd;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

import static ml.konstanius.minecicd.MineCICD.muteList;

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

    public static void presentWebhookCommit(String pusher, String message, String mainAction, String commit, ArrayList<String> commands) {
        String header = "<gold><bold>========== MineCICD Webhook Push ==========</bold></gold>";
        String footer = "<gold><bold>============================================</bold></gold>";

        String pusherMessage = "<gray><italic>Pusher: </italic></gray><white>" + pusher + "</white>";
        String messageMessage = "<gray><italic>Message: </italic></gray><white>" + message + "</white>";
        String commitMessage = "<gray><italic>Commit: </italic></gray><white>" + commit + "</white>";
        String actionMessage = "<gray><italic>Action: </italic></gray><white>" + mainAction + "</white>";

        ArrayList<String> commandMessages = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            commandMessages.add("<gray><italic>Command (" + (i + 1) + "): </italic></gray><white>" + command + "</white>");
        }


        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("minecicd.notify") && !muteList.contains(p.getUniqueId().toString())) {
                try {
                    p.sendRichMessage(header);
                    p.sendRichMessage(pusherMessage);
                    p.sendRichMessage(messageMessage);
                    p.sendRichMessage(commitMessage);
                    p.sendRichMessage(actionMessage);
                    for (String commandMessage : commandMessages) {
                        p.sendRichMessage(commandMessage);
                    }
                    p.sendRichMessage(footer);

                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                    Bukkit.getScheduler().runTaskLater(MineCICD.plugin, () -> p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f), 5L);
                    Bukkit.getScheduler().runTaskLater(MineCICD.plugin, () -> p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f), 10L);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
