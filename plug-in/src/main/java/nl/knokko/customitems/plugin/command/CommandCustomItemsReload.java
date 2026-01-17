package nl.knokko.customitems.plugin.command;

import nl.knokko.customitems.plugin.CustomItemsPlugin;
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

        instance.reloadPluginConfig();

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

        try {
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Class<?> clickEventClass = Class.forName("net.md_5.bungee.api.chat.ClickEvent");
            Class<?> clickActionClass = Class.forName("net.md_5.bungee.api.chat.ClickEvent$Action");
            Class<?> hoverEventClass = Class.forName("net.md_5.bungee.api.chat.HoverEvent");
            Class<?> hoverActionClass = Class.forName("net.md_5.bungee.api.chat.HoverEvent$Action");
            Class<?> componentBuilderClass = Class.forName("net.md_5.bungee.api.chat.ComponentBuilder");
            Class<?> baseComponentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> baseComponentArrayClass = java.lang.reflect.Array.newInstance(baseComponentClass, 0).getClass();

            Object component = textComponentClass.getConstructor(String.class).newInstance(message);
            Object clickAction = resolveCopyAction(clickActionClass);
            Object clickEvent = clickEventClass.getConstructor(clickActionClass, String.class)
                    .newInstance(clickAction, copyText);
            textComponentClass.getMethod("setClickEvent", clickEventClass).invoke(component, clickEvent);

            Object builder = componentBuilderClass.getConstructor(String.class).newInstance("Click to copy");
            Object hoverComponents = componentBuilderClass.getMethod("create").invoke(builder);
            Object hoverAction = Enum.valueOf((Class<Enum>) hoverActionClass, "SHOW_TEXT");
            Object hoverEvent = hoverEventClass.getConstructor(hoverActionClass, baseComponentArrayClass)
                    .newInstance(hoverAction, hoverComponents);
            textComponentClass.getMethod("setHoverEvent", hoverEventClass).invoke(component, hoverEvent);

            Object spigot = player.getClass().getMethod("spigot").invoke(player);
            try {
                spigot.getClass().getMethod("sendMessage", baseComponentClass).invoke(spigot, component);
            } catch (NoSuchMethodException ex) {
                Object array = java.lang.reflect.Array.newInstance(baseComponentClass, 1);
                java.lang.reflect.Array.set(array, 0, component);
                spigot.getClass().getMethod("sendMessage", array.getClass()).invoke(spigot, array);
            }
        } catch (Throwable ex) {
            player.sendMessage(message);
        }
    }

    private static Object resolveCopyAction(Class<?> actionClass) {
        try {
            return Enum.valueOf((Class<Enum>) actionClass, "COPY_TO_CLIPBOARD");
        } catch (IllegalArgumentException ex) {
            return Enum.valueOf((Class<Enum>) actionClass, "SUGGEST_COMMAND");
        }
    }
}
