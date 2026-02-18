package nl.knokko.customitems.nms20;

import kr.toxicity.libraries.datacomponent.DataComponentAPIBukkit;
import kr.toxicity.libraries.datacomponent.api.DataComponentAPI;
import kr.toxicity.libraries.datacomponent.api.ItemAdapter;
import kr.toxicity.libraries.datacomponent.api.NMS;
import kr.toxicity.libraries.datacomponent.api.wrapper.ItemLore;
import net.kyori.adventure.text.Component;
import nl.knokko.customitems.item.KciFood;
import nl.knokko.customitems.nms18plus.KciNmsItems18Plus;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KciNmsItems20 extends KciNmsItems18Plus {

    private static final boolean HAS_PAPER;
    private static final boolean DATA_COMPONENTS_API_SUPPORTS_VERSION;
    private static final Method GET_NAME_METHOD = findGetNameMethod();
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

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
            if (isMinecraftVersionAtLeast(1, 20, 5)) {
                try {
                    DataComponentAPIBukkit.load();
                    dataComponentApiSupportsVersion = true;
                } catch (Throwable versionNotSupported) {
                    Bukkit.getLogger().log(
                            Level.WARNING, "It looks like the DataComponentsAPI version bundled with CustomItems " +
                                    "doesn't support this minecraft version", versionNotSupported
                    );
                }
            } else {
                Bukkit.getLogger().warning(
                        "DataComponents are only available in Minecraft 1.20.5+, so translations are disabled on " +
                                getMinecraftVersionString()
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

    @Override
    public boolean applyNativeFoodProperties(ItemMeta meta, KciFood food) {
        if (meta == null || food == null) return false;
        if (!isMinecraftVersionAtLeast(1, 20, 5)) return false;

        try {
            FoodComponent component = meta.getFood();
            component.setNutrition(Math.max(0, food.getFoodValue()));
            component.setSaturation(0f);
            component.setCanAlwaysEat(!food.getEatEffects().isEmpty() || food.getFoodValue() < 0);
            component.setEatSeconds(food.getEatTime() / 20f);
            component.setEffects(Collections.emptyList());
            meta.setFood(component);
            return true;
        } catch (Throwable failed) {
            return false;
        }
    }

    @Override
    public boolean hasNativeFoodProperties(ItemStack stack) {
        if (!isMinecraftVersionAtLeast(1, 20, 5)) return false;
        if (stack == null || stack.getType() == Material.AIR) return false;

        try {
            ItemMeta meta = stack.getItemMeta();
            return meta != null && meta.hasFood();
        } catch (Throwable failed) {
            return false;
        }
    }

    private static boolean isMinecraftVersionAtLeast(int major, int minor, int patch) {
        String version = getMinecraftVersionString();
        int[] parsed = parseVersion(version);
        if (parsed == null) {
            return true;
        }
        if (parsed[0] != major) {
            return parsed[0] > major;
        }
        if (parsed[1] != minor) {
            return parsed[1] > minor;
        }
        return parsed[2] >= patch;
    }

    private static int[] parseVersion(String version) {
        if (version == null) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.find()) {
            return null;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return new int[] { major, minor, patch };
    }

    private static String getMinecraftVersionString() {
        try {
            Method method = Bukkit.class.getMethod("getMinecraftVersion");
            Object result = method.invoke(null);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        String bukkitVersion = Bukkit.getBukkitVersion();
        if (bukkitVersion != null) {
            int dashIndex = bukkitVersion.indexOf('-');
            if (dashIndex > 0) {
                return bukkitVersion.substring(0, dashIndex);
            }
            return bukkitVersion;
        }

        String serverVersion = Bukkit.getVersion();
        return serverVersion != null ? serverVersion : "unknown";
    }
}
