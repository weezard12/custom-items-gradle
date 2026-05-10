package nl.knokko.customitems.nms26;

import nl.knokko.customitems.nms21plus.KciNmsItems21Plus;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class KciNmsItems26 extends KciNmsItems21Plus {

    private static final Class<?> NMS_ITEM_STACK_CLASS = loadNmsItemStackClass();
    private static final Method GET_NAME_METHOD = findGetNameMethod();
    private static final Method AS_NMS_COPY_METHOD = findAsNmsCopyMethod();

    private static Class<?> loadNmsItemStackClass() {
        try {
            return Class.forName("net.minecraft.world.item.ItemStack");
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("Can't find the NMS ItemStack class for 26.1.x", missing);
        }
    }

    private static Method findGetNameMethod() {
        String[] candidateNames = { "getName", "y", "x", "w" };

        for (String candidateName : candidateNames) {
            try {
                return NMS_ITEM_STACK_CLASS.getMethod(candidateName);
            } catch (NoSuchMethodException ignored) {
            }
        }

        for (Method method : NMS_ITEM_STACK_CLASS.getMethods()) {
            if (method.getParameterCount() == 0 && looksLikeChatComponent(method.getReturnType())) {
                return method;
            }
        }

        throw new IllegalStateException("Can't find the NMS ItemStack name method for 26.1.x");
    }

    private static Method findAsNmsCopyMethod() {
        try {
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            return craftItemStack.getMethod("asNMSCopy", ItemStack.class);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Can't find CraftItemStack.asNMSCopy for 26.1.x", failure);
        }
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
        try {
            Object nms = AS_NMS_COPY_METHOD.invoke(null, stack);
            Object name = GET_NAME_METHOD.invoke(nms);
            return componentToString(name);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }
}
