package nl.knokko.customitems.nms21r1;

import nl.knokko.customitems.nms21plus.KciNmsItems21Plus;
import org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class KciNmsItems21R1 extends KciNmsItems21Plus {

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

        throw new IllegalStateException("Can't find the NMS ItemStack name method for 1.21 R1");
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
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        try {
            Object name = GET_NAME_METHOD.invoke(nms);
            return componentToString(name);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }
}
