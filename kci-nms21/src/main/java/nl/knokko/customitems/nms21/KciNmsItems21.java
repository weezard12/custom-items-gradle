package nl.knokko.customitems.nms21;

import nl.knokko.customitems.nms21plus.KciNmsItems21Plus;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class KciNmsItems21 extends KciNmsItems21Plus {

    private static final Method GET_NAME_METHOD = findGetNameMethod();

    private static Method findGetNameMethod() {
        String[] candidateNames = { "y", "x", "w", "getName" };
        Class<?> nmsClass = net.minecraft.world.item.ItemStack.class;

        for (String candidateName : candidateNames) {
            try {
                return nmsClass.getMethod(candidateName);
            } catch (NoSuchMethodException ignored) {
            }
        }

        for (Method method : nmsClass.getMethods()) {
            if (method.getParameterCount() == 0 && looksLikeChatComponent(method.getReturnType())) {
                return method;
            }
        }

        throw new IllegalStateException("Can't find the NMS ItemStack name method for 1.21.x");
    }

    private static boolean looksLikeChatComponent(Class<?> type) {
        if (type == null) return false;
        String name = type.getName();
        return name.startsWith("net.minecraft.network.chat.")
                || name.endsWith(".IChatBaseComponent")
                || name.endsWith(".Component");
    }

    private static String componentToString(Object component) throws ReflectiveOperationException {
        if (component == null) return "";
        try {
            Method getString = component.getClass().getMethod("getString");
            Object result = getString.invoke(component);
            if (result instanceof String) return (String) result;
        } catch (NoSuchMethodException ignored) {
        }
        return String.valueOf(component);
    }

    @Override
    public String getStackName(ItemStack stack) {
        net.minecraft.world.item.ItemStack nms = asNmsCopy(stack);
        try {
            Object name = GET_NAME_METHOD.invoke(nms);
            return componentToString(name);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
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
