package nl.knokko.customitems.plugin.command;

import nl.knokko.customitems.block.KciBlock;
import nl.knokko.customitems.item.KciItem;
import nl.knokko.customitems.plugin.set.ItemSetWrapper;
import nl.knokko.customitems.recipe.KciCraftingRecipe;
import nl.knokko.customitems.recipe.KciShapedRecipe;
import nl.knokko.customitems.recipe.KciShapelessRecipe;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

class CommandCustomItemsList {

    final ItemSetWrapper itemSet;

    CommandCustomItemsList(ItemSetWrapper itemSet) {
        this.itemSet = itemSet;
    }

    void handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customitems.list")) {
            sender.sendMessage(ChatColor.DARK_RED + "You don't have access to this command");
            return;
        }

        if (args.length >= 2) {
            String category = args[1].toLowerCase();
            switch (category) {
                case "items":
                    listItems(sender);
                    return;
                case "blocks":
                    listBlocks(sender);
                    return;
                case "recipes":
                    listRecipes(sender);
                    return;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown list category '" + args[1]
                            + "'. Use items, blocks, or recipes.");
                    return;
            }
        }

        listItems(sender);
        listBlocks(sender);
        listRecipes(sender);
    }

    private void listItems(CommandSender sender) {
        if (!itemSet.get().items.isEmpty()) {
            sender.sendMessage(ChatColor.AQUA + "All custom items:");
            for (KciItem item : itemSet.get().items) {
                if (item.getAlias().isEmpty()) {
                    sender.sendMessage(item.getName());
                } else {
                    sender.sendMessage(item.getName() + " (" + item.getAlias() + ")");
                }
            }
        } else {
            sender.sendMessage(ChatColor.RED + "There are 0 custom items");
        }
    }

    private void listBlocks(CommandSender sender) {
        if (!itemSet.get().blocks.isEmpty()) {
            sender.sendMessage(ChatColor.AQUA + "All custom blocks:");
            for (KciBlock block : itemSet.get().blocks) {
                sender.sendMessage(block.getName());
            }
        } else {
            sender.sendMessage(ChatColor.AQUA + "There are 0 custom blocks");
        }
    }

    private void listRecipes(CommandSender sender) {
        if (!itemSet.get().craftingRecipes.isEmpty()) {
            sender.sendMessage(ChatColor.AQUA + "All custom crafting recipes:");
            for (KciCraftingRecipe recipe : itemSet.get().craftingRecipes) {
                String type = recipe instanceof KciShapedRecipe ? "shaped"
                        : recipe instanceof KciShapelessRecipe ? "shapeless"
                        : "unknown";
                sender.sendMessage(type + ": " + recipe.getResult());
            }
        } else {
            sender.sendMessage(ChatColor.AQUA + "There are 0 custom crafting recipes");
        }
    }
}
