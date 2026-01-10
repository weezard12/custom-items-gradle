package nl.knokko.customitems.plugin.command;

import nl.knokko.customitems.plugin.CustomItemsPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

class CommandCustomItemsReload {

    void handle(String[] args, CommandSender sender, boolean enableOutput) {
        if (!sender.hasPermission("customitems.reload")) {
            if (enableOutput) sender.sendMessage(ChatColor.DARK_RED + "You don't have access to this command.");
            return;
        }

        Consumer<String> sendMessage = message -> {
            if (enableOutput) sendMessage(sender, message);
        };

        CustomItemsPlugin instance = CustomItemsPlugin.getInstance();
        if (!instance.canRegisterRecipes()) {
            sender.sendMessage(ChatColor.RED + "You can't reload the first 5 seconds");
            return;
        }

        if (args.length == 1) {
            instance.getItemSetLoader().reload(sendMessage);
        } else if (args.length == 2) {
            instance.getItemSetLoader().reload(
                    sendMessage, instance.getSet().get().getExportSettings().getHostAddress(), args[1]
            );
        } else if (args.length == 3) {
            instance.getItemSetLoader().reload(sendMessage, args[2], args[1]);
        } else {
            sendMessage.accept(ChatColor.RED + "You should use /kci reload [hash] [host]");
        }
    }

    private static void sendMessage(CommandSender sender, String message) {
        if (sender instanceof Player && isYamlErrorLine(message)) {
            sendCopyableMessage((Player) sender, message);
        } else {
            sender.sendMessage(message);
        }
    }

    private static boolean isYamlErrorLine(String message) {
        return message != null && message.startsWith(ChatColor.RED.toString() + "- ");
    }

    private static void sendCopyableMessage(Player player, String message) {
        String copyText = ChatColor.stripColor(message);
        if (copyText == null) {
            player.sendMessage(message);
            return;
        }

        ClickEvent.Action clickAction = resolveCopyAction();
        TextComponent component = new TextComponent(message);
        component.setClickEvent(new ClickEvent(clickAction, copyText));
        component.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Click to copy").create()
        ));

        player.spigot().sendMessage(component);
    }

    private static ClickEvent.Action resolveCopyAction() {
        try {
            return ClickEvent.Action.valueOf("COPY_TO_CLIPBOARD");
        } catch (IllegalArgumentException ex) {
            return ClickEvent.Action.SUGGEST_COMMAND;
        }
    }
}
