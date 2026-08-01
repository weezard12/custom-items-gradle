package nl.knokko.customitems.nms;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class KciNms {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    static {
        KciNms supportedInstance = null;
        int chosenMcVersion = -1;
        NmsCandidate[] candidates = {
                new NmsCandidate("nl.knokko.customitems.nms12.KciNms12", version(1, 12, 2)),
                new NmsCandidate("nl.knokko.customitems.nms13.KciNms13", version(1, 13, 2)),
                new NmsCandidate("nl.knokko.customitems.nms14.KciNms14", version(1, 14, 4)),
                new NmsCandidate("nl.knokko.customitems.nms15.KciNms15", version(1, 15, 2)),
                new NmsCandidate("nl.knokko.customitems.nms16.KciNms16", version(1, 16, 5)),
                new NmsCandidate("nl.knokko.customitems.nms17.KciNms17", version(1, 17, 1)),
                new NmsCandidate("nl.knokko.customitems.nms18.KciNms18", version(1, 18, 2)),
                new NmsCandidate("nl.knokko.customitems.nms19.KciNms19", version(1, 19, 4)),
                new NmsCandidate("nl.knokko.customitems.nms20.KciNms20", version(1, 20, 6)),
                new NmsCandidate("nl.knokko.customitems.nms21.KciNms21", version(1, 21, 11)),
                new NmsCandidate("nl.knokko.customitems.nms21r1.KciNms21R1", version(1, 21, 11)),
                new NmsCandidate("nl.knokko.customitems.nms26.KciNms26", version(26, 2, 0))
        };

        for (NmsCandidate candidate : candidates) {
            try {
                Class<?> nmsClass = Class.forName(candidate.className);
                String[] craftItemStackClassNames = resolveCraftItemStackClassNames(nmsClass);
                boolean matched = false;
                for (String craftItemStackClassName : craftItemStackClassNames) {
                    try {
                        // If the candidate version matches the actual NMS version of the server implementation,
                        // we are good to go.
                        Class.forName(craftItemStackClassName);
                        supportedInstance = (KciNms) nmsClass.getConstructor().newInstance();
                        chosenMcVersion = candidate.mcVersion;
                        if (!supportedInstance.isCompatible()) {
                            supportedInstance = null;
                            chosenMcVersion = -1;
                        }
                        matched = supportedInstance != null;
                        if (matched) break;
                    } catch (ClassNotFoundException unavailable) {
                        // Try the next NMS version string for this candidate
                    }
                }
                if (matched) break;
            } catch (ClassNotFoundException unavailable) {
                // This block will be reached if this candidate version doesn't match the NMS version of the server
                // To handle this, we just continue with the next candidate version
            } catch (
                    NoSuchFieldException | IllegalAccessException | InstantiationException
                            | NoSuchMethodException | InvocationTargetException unexpectedError
            ) {
                throw new RuntimeException(unexpectedError);
            }
        }

        instance = supportedInstance;
        mcVersion = chosenMcVersion >= 0 ? detectMinecraftVersion(chosenMcVersion) : chosenMcVersion;
    }

    private static int detectMinecraftVersion(int fallbackMajor) {
        String raw = null;
        try {
            raw = Bukkit.getVersion();
        } catch (RuntimeException | NoClassDefFoundError ignored) {
            raw = null;
        }
        Integer parsed = parseVersion(raw);
        if (parsed != null) return parsed;

        raw = reflectServerString("getMinecraftVersion");
        parsed = parseVersion(raw);
        if (parsed != null) return parsed;

        raw = reflectServerString("getBukkitVersion");
        parsed = parseVersion(raw);
        if (parsed != null) return parsed;

        return fallbackMajor;
    }

    private static Integer parseVersion(String raw) {
        if (raw == null) return null;
        Matcher matcher = VERSION_PATTERN.matcher(raw);
        if (!matcher.find()) return null;
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        return version(major, minor, patch);
    }

    private static int version(int major, int minor, int patch) {
        return major * 10000 + minor * 100 + patch;
    }

    private static String reflectServerString(String methodName) {
        try {
            Method method = Bukkit.getServer().getClass().getMethod(methodName);
            Object value = method.invoke(Bukkit.getServer());
            return value != null ? value.toString() : null;
        } catch (ReflectiveOperationException | RuntimeException | NoClassDefFoundError ignored) {
            return null;
        }
    }

    private static String[] resolveCraftItemStackClassNames(Class<?> nmsClass)
            throws NoSuchFieldException, IllegalAccessException {
        try {
            Object fieldValue = nmsClass.getField("CRAFT_ITEM_STACK_CLASS_NAMES").get(null);
            if (fieldValue instanceof String[]) {
                String[] values = (String[]) fieldValue;
                if (values.length > 0) return values;
            }
        } catch (NoSuchFieldException ignored) {
            // Fallback to the older NMS-version fields
        }

        String[] nmsVersions = resolveNmsVersions(nmsClass);
        String[] result = new String[nmsVersions.length];
        for (int index = 0; index < nmsVersions.length; index++) {
            result[index] = "org.bukkit.craftbukkit.v" + nmsVersions[index] + ".inventory.CraftItemStack";
        }
        return result;
    }

    private static String[] resolveNmsVersions(Class<?> nmsClass) throws NoSuchFieldException, IllegalAccessException {
        try {
            Object fieldValue = nmsClass.getField("NMS_VERSION_STRINGS").get(null);
            if (fieldValue instanceof String[]) {
                String[] values = (String[]) fieldValue;
                if (values.length > 0) return values;
            }
        } catch (NoSuchFieldException ignored) {
            // Fallback to the single version field
        }
        String single = (String) nmsClass.getField("NMS_VERSION_STRING").get(null);
        return new String[] { single };
    }

    private static final class NmsCandidate {

        final String className;
        final int mcVersion;

        NmsCandidate(String className, int mcVersion) {
            this.className = className;
            this.mcVersion = mcVersion;
        }
    }

    public static final KciNms instance;

    public static final int mcVersion;

    public final KciNmsBlocks blocks;

    public final KciNmsEntities entities;

    public final KciNmsItems items;

    public KciNms(KciNmsBlocks blocks, KciNmsEntities entities, KciNmsItems items) {
        this.blocks = blocks;
        this.entities = entities;
        this.items = items;
    }

    /**
     * <p>Performs a raytrace from {@code startLocation} towards {@code startLocation + vector}.
     * The {@code vector} determines both the direction and the maximum distance of the raytrace!</p>
     *
     * <p>If an intersection with any block or entity was found, a RaytraceResult representing the intersection
     * that is closest to {@code startLocation} will be returned. If no such intersection was found, this
     * method will return null.</p>
     *
     * <p>Entities included in {@code entitiesToExclude} and dropped item entities will be ignored by
     * the raytrace.</p>
     *
     * @param startLocation The location from which the raytrace will start
     * @param vector The direction and maximum distance of the raytrace
     * @param entitiesToExclude An array of entities that will be ignored by this raytrace, may contain null
     * @return A RaytraceResult for the nearest intersection, or null if no intersection was found
     */
    public abstract RaytraceResult raytrace(Location startLocation, Vector vector, Entity...entitiesToExclude);

    /**
     * The command usage of minecraft changed drastically when the updated from 1.12 to 1.13.
     * This method can be used to determine whether the current server version uses the old command
     * system (1.12 and earlier) or the new command system (1.13 and later).
     *
     * @return True when running on MC 1.13 or later; false when running on MC 1.12
     */
    public abstract boolean useNewCommands();

    protected boolean isCompatible() {
        return true;
    }

    public Sound getVanillaSound(String key) {
        return Sound.valueOf(key);
    }

    public PotionEffectType getVanillaEffectType(String key) {
        return PotionEffectType.getByName(key);
    }

    public String getBiomeKey(Biome biome) {
        return biome.name();
    }
}
