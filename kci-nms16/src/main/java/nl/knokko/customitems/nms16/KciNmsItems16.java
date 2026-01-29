package nl.knokko.customitems.nms16;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Multimap;
import nl.knokko.customitems.nms.RawAttribute;
import nl.knokko.customitems.nms16plus.KciNmsItems16Plus;
import org.bukkit.Bukkit;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KciNmsItems16 extends KciNmsItems16Plus {

    private static final String CRAFT_VERSION = resolveCraftVersion();
    private static final Class<?> NMS_ITEM_STACK_CLASS = loadNmsClass("ItemStack");
    private static final Class<?> NMS_ENUM_ITEM_SLOT_CLASS = loadNmsClass("EnumItemSlot");

    private static final Method AS_NMS_COPY_METHOD = findAsNmsCopyMethod();
    private static final Method GET_NMS_SLOT_METHOD = findGetNmsSlotMethod();
    private static final Method GET_ATTRIBUTES_METHOD = findAttributesMethod();
    private static final Method GET_NAME_METHOD = findGetNameMethod();

    @Override
    protected RawAttribute[] getDefaultAttributes(ItemStack stack) {
        List<RawAttribute> attributeList = new ArrayList<>(2);
        Object nmsStack = asNmsCopy(stack);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            Object nmsSlot = getNmsSlot(slot);
            Multimap<?, ?> map = getAttributes(nmsStack, nmsSlot);
            if (map == null) continue;

            for (Map.Entry<?, ?> entry : map.entries()) {
                Object attributeBase = entry.getKey();
                Object attributeModifier = entry.getValue();
                if (attributeBase == null || attributeModifier == null) continue;

                UUID id = getAttributeId(attributeModifier);
                String attribute = getAttributeName(attributeBase);
                attribute = CaseFormat.LOWER_UNDERSCORE.to(
                        CaseFormat.LOWER_CAMEL, attribute.replace("attribute.name.", "")
                );
                String slotName = fromBukkitSlot(slot);
                int operation = getAttributeOperation(attributeModifier);
                double value = getAttributeAmount(attributeModifier);
                attributeList.add(new RawAttribute(id, attribute, slotName, operation, value));
            }
        }

        return attributeList.toArray(new RawAttribute[0]);
    }

    @Override
    public String getStackName(ItemStack stack) {
        Object nms = asNmsCopy(stack);
        try {
            Object component = GET_NAME_METHOD.invoke(nms);
            return componentToString(component);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static Object asNmsCopy(ItemStack stack) {
        try {
            return AS_NMS_COPY_METHOD.invoke(null, stack);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to convert ItemStack to NMS for 1.16.x", failure);
        }
    }

    private static Object getNmsSlot(EquipmentSlot slot) {
        try {
            return GET_NMS_SLOT_METHOD.invoke(null, slot);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to resolve NMS equipment slot for 1.16.x", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static Multimap<?, ?> getAttributes(Object nmsStack, Object nmsSlot) {
        try {
            Object result = GET_ATTRIBUTES_METHOD.invoke(nmsStack, nmsSlot);
            if (result instanceof Multimap) {
                return (Multimap<?, ?>) result;
            }
            return null;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to read default attributes for 1.16.x", failure);
        }
    }

    private static String getAttributeName(Object attributeBase) {
        Method method = findNoArgMethod(attributeBase.getClass(), String.class, "getName", "a");
        try {
            Object result = method.invoke(attributeBase);
            return result != null ? result.toString() : "";
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static UUID getAttributeId(Object attributeModifier) {
        Method method = findNoArgMethod(attributeModifier.getClass(), UUID.class, "getUniqueId", "a");
        try {
            return (UUID) method.invoke(attributeModifier);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static int getAttributeOperation(Object attributeModifier) {
        Method method = findNoArgMethod(attributeModifier.getClass(), Enum.class, "getOperation", "c", "b");
        try {
            Object result = method.invoke(attributeModifier);
            if (result instanceof Enum) return ((Enum<?>) result).ordinal();
            if (result instanceof Number) return ((Number) result).intValue();
            return 0;
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static double getAttributeAmount(Object attributeModifier) {
        Method method = findNoArgDoubleMethod(attributeModifier.getClass(), "getAmount", "d", "b", "a");
        try {
            Object result = method.invoke(attributeModifier);
            if (result instanceof Number) return ((Number) result).doubleValue();
            return 0.0;
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static Method findAsNmsCopyMethod() {
        try {
            Class<?> craftItemStack = Class.forName(
                    "org.bukkit.craftbukkit." + CRAFT_VERSION + ".inventory.CraftItemStack"
            );
            return craftItemStack.getMethod("asNMSCopy", ItemStack.class);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Can't find CraftItemStack.asNMSCopy for 1.16.x", failure);
        }
    }

    private static Method findGetNmsSlotMethod() {
        try {
            Class<?> craftEquipmentSlot = Class.forName(
                    "org.bukkit.craftbukkit." + CRAFT_VERSION + ".CraftEquipmentSlot"
            );
            return craftEquipmentSlot.getMethod("getNMS", EquipmentSlot.class);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Can't find CraftEquipmentSlot.getNMS for 1.16.x", failure);
        }
    }

    private static Method findAttributesMethod() {
        for (Method method : NMS_ITEM_STACK_CLASS.getMethods()) {
            if (method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(NMS_ENUM_ITEM_SLOT_CLASS)
                    && Multimap.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        for (Method method : NMS_ITEM_STACK_CLASS.getDeclaredMethods()) {
            if (method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(NMS_ENUM_ITEM_SLOT_CLASS)
                    && Multimap.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("Can't find attribute method on NMS ItemStack for 1.16.x");
    }

    private static Method findGetNameMethod() {
        String[] candidateNames = { "getName", "a", "b" };
        for (String candidateName : candidateNames) {
            Method method = findNoArgMethodIfExists(NMS_ITEM_STACK_CLASS, Object.class, candidateName);
            if (method != null && looksLikeChatComponent(method.getReturnType())) {
                return method;
            }
        }
        for (Method method : NMS_ITEM_STACK_CLASS.getMethods()) {
            if (method.getParameterCount() == 0 && looksLikeChatComponent(method.getReturnType())) {
                return method;
            }
        }
        for (Method method : NMS_ITEM_STACK_CLASS.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && looksLikeChatComponent(method.getReturnType())) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("Can't find ItemStack name method for 1.16.x");
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

    private static boolean looksLikeChatComponent(Class<?> type) {
        if (type == null) return false;
        String name = type.getName();
        return name.endsWith(".IChatBaseComponent")
                || name.endsWith(".Component")
                || name.contains(".IChatBaseComponent");
    }

    private static Method findNoArgMethod(Class<?> owner, Class<?> returnType, String... candidateNames) {
        for (String name : candidateNames) {
            try {
                Method method = owner.getMethod(name);
                if (returnType.isAssignableFrom(method.getReturnType()) || returnType == Object.class) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method method : owner.getMethods()) {
            if (method.getParameterCount() == 0
                    && (returnType.isAssignableFrom(method.getReturnType()) || returnType == Object.class)) {
                return method;
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getParameterCount() == 0
                    && (returnType.isAssignableFrom(method.getReturnType()) || returnType == Object.class)) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("Can't find method on " + owner.getName());
    }

    private static Method findNoArgMethodIfExists(Class<?> owner, Class<?> returnType, String name) {
        try {
            Method method = owner.getMethod(name);
            if (returnType.isAssignableFrom(method.getReturnType()) || returnType == Object.class) {
                return method;
            }
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Method method = owner.getDeclaredMethod(name);
            if (returnType.isAssignableFrom(method.getReturnType()) || returnType == Object.class) {
                method.setAccessible(true);
                return method;
            }
        } catch (NoSuchMethodException ignored) {
        }
        return null;
    }

    private static Method findNoArgDoubleMethod(Class<?> owner, String... candidateNames) {
        for (String name : candidateNames) {
            try {
                Method method = owner.getMethod(name);
                if (method.getReturnType() == double.class) return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method method : owner.getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == double.class) {
                return method;
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == double.class) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("Can't find double-returning method on " + owner.getName());
    }

    private static Class<?> loadNmsClass(String simpleName) {
        try {
            return Class.forName("net.minecraft.server." + CRAFT_VERSION + "." + simpleName);
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Can't find NMS class " + simpleName + " for 1.16.x", failure);
        }
    }

    private static String resolveCraftVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }
}
