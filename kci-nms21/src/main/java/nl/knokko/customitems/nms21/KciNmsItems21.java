package nl.knokko.customitems.nms21;

import nl.knokko.customitems.nms21plus.KciNmsItems21Plus;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class KciNmsItems21 extends KciNmsItems21Plus {
    @Override
    public String getStackName(ItemStack stack) {
        net.minecraft.world.item.ItemStack nms = asNmsCopy(stack);
        return nms.y().getString();
    }

    private static net.minecraft.world.item.ItemStack asNmsCopy(ItemStack stack) {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String craftVersion = packageName.substring(packageName.lastIndexOf('.') + 1);
            Class<?> craftItemStack = Class.forName(
                    "org.bukkit.craftbukkit." + craftVersion + ".inventory.CraftItemStack"
            );
            Method asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            Object nmsStack = asNmsCopy.invoke(null, stack);
            return (net.minecraft.world.item.ItemStack) nmsStack;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            throw new IllegalStateException("Failed to convert ItemStack to NMS for 1.21.x", ex);
        }
    }
}
