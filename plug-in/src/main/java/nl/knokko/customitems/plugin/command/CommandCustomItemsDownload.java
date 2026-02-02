package nl.knokko.customitems.plugin.command;

import nl.knokko.customitems.plugin.set.loading.ItemSetLoader;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class CommandCustomItemsDownload {

    private final ItemSetLoader loader;

    CommandCustomItemsDownload(ItemSetLoader loader) {
        this.loader = loader;
    }

    void handle(String[] args, CommandSender sender, boolean enableOutput) {
        if (args.length == 2 && "resourcepack".equals(args[1])) {
            String url = loader.getResourcePackDownloadUrl();
            if (url == null) {
                if (enableOutput) sender.sendMessage(ChatColor.RED + "The resource pack is not available right now.");
                return;
            }

            if (sender instanceof Player) {
                sendClickableUrl((Player) sender, url);
            } else if (enableOutput) {
                sender.sendMessage(url);
            }
        } else if (enableOutput) {
            sender.sendMessage(ChatColor.RED + "You should use /kci download resourcepack");
        }
    }

    private static void sendClickableUrl(Player player, String url) {
        String message = ChatColor.GREEN + "Resource pack download: " + ChatColor.AQUA + url;
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
            Object clickAction = Enum.valueOf((Class<Enum>) clickActionClass, "OPEN_URL");
            Object clickEvent = clickEventClass.getConstructor(clickActionClass, String.class)
                    .newInstance(clickAction, url);
            textComponentClass.getMethod("setClickEvent", clickEventClass).invoke(component, clickEvent);

            Object builder = componentBuilderClass.getConstructor(String.class).newInstance("Click to open");
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
}
