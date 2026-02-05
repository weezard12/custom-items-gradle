package nl.knokko.customitems.plugin.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

class CommandCustomItemsUrlPack {

    void handle(String[] args, CommandSender sender, boolean enableOutput) {
        if (!sender.hasPermission("customitems.urlpack")) {
            if (enableOutput) sender.sendMessage(ChatColor.DARK_RED + "You don't have access to this command.");
            return;
        }

        if (args.length != 2) {
            if (enableOutput) sender.sendMessage(ChatColor.RED + "You should use /kci urlpack <url>");
            return;
        }

        if (!(sender instanceof Player)) {
            if (enableOutput) sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        String url = args[1];
        Player player = (Player) sender;
        if (trySetResourcePack(player, url)) {
            if (enableOutput) sender.sendMessage(ChatColor.GREEN + "Sent resource pack prompt.");
        } else if (enableOutput) {
            sender.sendMessage(ChatColor.RED + "Failed to send resource pack prompt.");
        }
    }

    private static boolean trySetResourcePack(Player player, String url) {
        try {
            Method oneArg = player.getClass().getMethod("setResourcePack", String.class);
            oneArg.invoke(player, url);
            return true;
        } catch (NoSuchMethodException ignored) {
            // Try other signatures
        } catch (Throwable ex) {
            return false;
        }

        try {
            Method twoArgs = player.getClass().getMethod("setResourcePack", String.class, byte[].class);
            twoArgs.invoke(player, url, (Object) null);
            return true;
        } catch (NoSuchMethodException ignored) {
            // Try other signatures
        } catch (Throwable ex) {
            return false;
        }

        try {
            Method threeArgs = player.getClass().getMethod("setResourcePack", String.class, byte[].class, String.class);
            threeArgs.invoke(player, url, null, null);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }
}
