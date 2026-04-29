package nl.knokko.customitems.plugin.command;

import nl.knokko.customitems.item.KciItem;
import nl.knokko.customitems.plugin.CustomItemsPlugin;
import nl.knokko.customitems.plugin.config.LanguageFile;
import nl.knokko.customitems.plugin.set.ItemSetWrapper;
import nl.knokko.customitems.plugin.util.ItemUtils;
import nl.knokko.customitems.plugin.util.ItemUtils.GiveResult;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static nl.knokko.customitems.plugin.command.CommandCustomItems.getOnlinePlayer;

public class CommandCustomItemsGive {

    final ItemSetWrapper itemSet;
    final LanguageFile lang;

    CommandCustomItemsGive(ItemSetWrapper itemSet, LanguageFile lang) {
        this.itemSet = itemSet;
        this.lang = lang;
    }

    private void sendGiveUseage(CommandSender sender) {
        sender.sendMessage(lang.getCommandGiveUseage());
    }

    void handle(String[] args, CommandSender sender, boolean enableOutput) {
        if (
                !sender.hasPermission("customitems.give") && itemSet.get().items.stream().noneMatch(
                        item -> sender.hasPermission("customitems.give." + item.getName())
                )
        ) {
            if (enableOutput) sender.sendMessage(ChatColor.DARK_RED + "You don't have access to this command.");
            return;
        }

        if (args.length == 2 || args.length == 3 || args.length == 4) {
            if (args[1].equalsIgnoreCase("all")) {
                handleGiveAll(args, sender, enableOutput);
                return;
            }

            // Try to find a custom item with the give name
            KciItem item = itemSet.getItem(args[1]);

            // If no such item is found, try to find one with the given alias
            if (item == null) {
                for (KciItem candidate : itemSet.get().items) {
                    if (candidate.getAlias().equals(args[1])) {
                        item = candidate;
                        break;
                    }
                }
            }

            if (item != null) {
                if (!sender.hasPermission("customitems.give") && !sender.hasPermission("customitems.give." + item.getName())) {
                    if (enableOutput) sender.sendMessage(ChatColor.DARK_RED + "You don't have permission to give this item to yourself.");
                    return;
                }

                Player receiver = null;
                int amount = 1;
                if (args.length == 2) {
                    if (sender instanceof Player) {
                        receiver = (Player) sender;
                    } else {
                        if (enableOutput) sender.sendMessage(lang.getCommandNoPlayerSpecified());
                    }
                }
                if (args.length >= 3) {
                    receiver = getOnlinePlayer(args[2]);
                    if (receiver == null) {
                        if (enableOutput) sender.sendMessage(lang.getCommandPlayerNotFound(args[2]));
                    }
                }
                if (args.length == 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ex) {
                        if (enableOutput) sender.sendMessage(ChatColor.RED + "The amount (" + args[3] + ") should be an integer.");
                        return;
                    }
                }
                if (amount > item.getMaxStacksize()) {
                    if (enableOutput) sender.sendMessage(ChatColor.RED + "The amount can be at most " + item.getMaxStacksize());
                    return;
                }
                if (amount < 1) {
                    if (enableOutput) sender.sendMessage(ChatColor.RED + "The amount must be positive");
                    return;
                }
                if (receiver != null && !CustomItemsPlugin.getInstance().getEnabledAreas().isEnabled(receiver.getLocation())) {
                    receiver = null;
                    if (enableOutput) sender.sendMessage(lang.getCommandWorldDisabled());
                }
                if (receiver != null) {
                    if (receiver == sender || sender.hasPermission("customitems.give") || sender.hasPermission("customitems.giveother")) {
                        giveTheItem(sender, receiver, item, amount, enableOutput);
                    } else {
                        if (enableOutput) sender.sendMessage(ChatColor.DARK_RED + "You don't have permission to give custom items to other players");
                    }
                }
            } else {
                if (enableOutput) sender.sendMessage(lang.getCommandNoSuchItem(args[1]));
            }
        } else {
            if (enableOutput) sendGiveUseage(sender);
        }
    }

    private void handleGiveAll(String[] args, CommandSender sender, boolean enableOutput) {
        Player receiver = null;
        int amount = 1;

        if (args.length == 2) {
            if (sender instanceof Player) {
                receiver = (Player) sender;
            } else if (enableOutput) {
                sender.sendMessage(lang.getCommandNoPlayerSpecified());
                return;
            }
        }

        if (args.length >= 3) {
            receiver = getOnlinePlayer(args[2]);
            if (receiver == null) {
                if (enableOutput) sender.sendMessage(lang.getCommandPlayerNotFound(args[2]));
                return;
            }
        }

        if (args.length == 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                if (enableOutput) sender.sendMessage(ChatColor.RED + "The amount (" + args[3] + ") should be an integer.");
                return;
            }
            if (amount < 1) {
                if (enableOutput) sender.sendMessage(ChatColor.RED + "The amount must be positive");
                return;
            }
        }

        if (receiver == null) {
            return;
        }

        if (!CustomItemsPlugin.getInstance().getEnabledAreas().isEnabled(receiver.getLocation())) {
            if (enableOutput) sender.sendMessage(lang.getCommandWorldDisabled());
            return;
        }

        if (receiver != sender && !sender.hasPermission("customitems.give") && !sender.hasPermission("customitems.giveother")) {
            if (enableOutput) {
                sender.sendMessage(ChatColor.DARK_RED + "You don't have permission to give custom items to other players");
            }
            return;
        }

        int deliveredCount = 0;
        int droppedCount = 0;
        boolean inventoryFull = false;
        boolean trimmedAmount = false;

        for (KciItem item : itemSet.get().items) {
            if (!sender.hasPermission("customitems.give")
                    && !sender.hasPermission("customitems.give." + item.getName())) {
                continue;
            }

            int itemAmount = amount;
            if (itemAmount > item.getMaxStacksize()) {
                itemAmount = item.getMaxStacksize();
                trimmedAmount = true;
            }

            GiveResult deliveryResult = deliverItem(receiver, item, itemAmount);
            if (deliveryResult == GiveResult.FAILED) {
                inventoryFull = true;
            } else {
                deliveredCount++;
                if (deliveryResult == GiveResult.DROPPED_ON_GROUND) droppedCount++;
            }
        }

        if (!enableOutput) return;
        if (deliveredCount > 0) {
            sender.sendMessage(ChatColor.GREEN + "Gave " + deliveredCount + " custom items.");
        } else {
            sender.sendMessage(ChatColor.RED + "No custom items could be given.");
        }
        if (droppedCount > 0) {
            sender.sendMessage(ChatColor.YELLOW + "Some items were dropped on the ground due to full inventory.");
        }
        if (trimmedAmount) {
            sender.sendMessage(ChatColor.YELLOW + "Some items were given with a lower amount due to stack limits.");
        }
        if (inventoryFull) {
            sender.sendMessage(ChatColor.RED + "Some items could not be given due to full inventory.");
        }
    }

    private GiveResult deliverItem(Player receiver, KciItem item, int amount) {
        return ItemUtils.giveCustomItem(
                itemSet, receiver, item, amount,
                CustomItemsPlugin.getInstance().shouldDropGivenItemsWhenInventoryIsFull()
        );
    }

    private void giveTheItem(CommandSender sender, Player receiver, KciItem item, int amount, boolean enableOutput) {
        GiveResult deliveryResult = deliverItem(receiver, item, amount);
        if (enableOutput) {
            if (deliveryResult == GiveResult.ADDED_TO_INVENTORY) {
                sender.sendMessage(lang.getCommandItemGiven());
            } else if (deliveryResult == GiveResult.DROPPED_ON_GROUND) {
                sender.sendMessage(ChatColor.YELLOW + "Custom item was dropped on the ground because no inventory slot was available.");
            } else {
                sender.sendMessage(ChatColor.RED + "No available inventory slot was found");
            }
        }
    }
}
