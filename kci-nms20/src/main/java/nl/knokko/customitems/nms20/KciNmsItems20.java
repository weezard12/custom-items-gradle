package nl.knokko.customitems.nms20;

import kr.toxicity.libraries.datacomponent.DataComponentAPIBukkit;
import kr.toxicity.libraries.datacomponent.api.DataComponentAPI;
import kr.toxicity.libraries.datacomponent.api.ItemAdapter;
import kr.toxicity.libraries.datacomponent.api.NMS;
import kr.toxicity.libraries.datacomponent.api.wrapper.ItemLore;
import net.kyori.adventure.text.Component;
import nl.knokko.customitems.nms18plus.KciNmsItems18Plus;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class KciNmsItems20 extends KciNmsItems18Plus {

    private static final boolean HAS_PAPER;
    private static final boolean DATA_COMPONENTS_API_SUPPORTS_VERSION;
    private static final Method GET_NAME_METHOD = findGetNameMethod();

    static {
        boolean foundPaper;
        try {
            Class.forName("io.papermc.paper.adventure.PaperAdventure");
            foundPaper = true;
        } catch (ClassNotFoundException noPaper) {
            foundPaper = false;
        }
        HAS_PAPER = foundPaper;

        boolean dataComponentApiSupportsVersion = false;
        if (HAS_PAPER) {
            try {
                DataComponentAPIBukkit.load();
                dataComponentApiSupportsVersion = true;
            } catch (UnsupportedOperationException versionNotSupported) {
                Bukkit.getLogger().log(
                        Level.WARNING, "It looks like the DataComponentsAPI version bundled with CustomItems " +
                                "doesn't support this minecraft version", versionNotSupported
                );
            }
        }

        DATA_COMPONENTS_API_SUPPORTS_VERSION = dataComponentApiSupportsVersion;
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

    private static Method findGetNameMethod() {
        String[] candidateNames = { "x", "y", "w", "getName" };
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

        throw new IllegalStateException("Can't find the NMS ItemStack name method for 1.20.x");
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
            throw new IllegalStateException("Failed to convert ItemStack to NMS for 1.20.x", ex);
        }
    }

    @Override
    public ItemStack translate(ItemStack item, String itemName, boolean translateDisplayName, int loreSize) {
        if (!HAS_PAPER) {
            Bukkit.getLogger().warning("Translations in MC 1.20+ require PaperMC");
            return item;
        }

        if (!DATA_COMPONENTS_API_SUPPORTS_VERSION) {
            Bukkit.getLogger().warning("The bundled DCAPI version doesn't support this minecraft version");
            return item;
        }

        ItemAdapter dataComponents = DataComponentAPI.api().adapter(item);
        if (translateDisplayName) {
            dataComponents.set(NMS.nms().customName(), Component.translatable("kci." + itemName + ".name"));
        }
        if (loreSize > 0) {
            List<Component> loreComponents = new ArrayList<>(loreSize);
            for (int index = 0; index < loreSize; index++) {
                loreComponents.add(Component.translatable("kci." + itemName + ".lore." + index));
            }
            dataComponents.set(NMS.nms().lore(), new ItemLore(loreComponents, loreComponents));
        }
        return dataComponents.build();
    }
}
