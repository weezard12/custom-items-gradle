package nl.knokko.customitems.nms;

import nl.knokko.customitems.MCVersions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class KciNms {

    static {
        KciNms supportedInstance = null;
        int chosenMcVersion = -1;
        NmsCandidate[] candidates = {
                new NmsCandidate("nl.knokko.customitems.nms12.KciNms12", 12),
                new NmsCandidate("nl.knokko.customitems.nms13.KciNms13", 13),
                new NmsCandidate("nl.knokko.customitems.nms14.KciNms14", 14),
                new NmsCandidate("nl.knokko.customitems.nms15.KciNms15", 15),
                new NmsCandidate("nl.knokko.customitems.nms16.KciNms16", 16),
                new NmsCandidate("nl.knokko.customitems.nms17.KciNms17", 17),
                new NmsCandidate("nl.knokko.customitems.nms18.KciNms18", 18),
                new NmsCandidate("nl.knokko.customitems.nms19.KciNms19", 19),
                new NmsCandidate("nl.knokko.customitems.nms20.KciNms20", 20),
                new NmsCandidate("nl.knokko.customitems.nms21.KciNms21", 21),
                new NmsCandidate("nl.knokko.customitems.nms21r1.KciNms21R1", 21)
        };

        for (NmsCandidate candidate : candidates) {
            try {
                Class<?> nmsClass = Class.forName(candidate.className);
                String[] nmsVersions = resolveNmsVersions(nmsClass);
                boolean matched = false;
                for (String nmsVersion : nmsVersions) {
                    try {
                        // If the candidate version matches the actual NMS version of the server implementation,
                        // we are good to go.
                        Class.forName("org.bukkit.craftbukkit.v" + nmsVersion + ".inventory.CraftItemStack");
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
        Integer parsed = MCVersions.parseVersion(raw);
        if (parsed != null) return parsed;

        raw = reflectServerString("getMinecraftVersion");
        parsed = MCVersions.parseVersion(raw);
        if (parsed != null) return parsed;

        raw = reflectServerString("getBukkitVersion");
        parsed = MCVersions.parseVersion(raw);
        if (parsed != null) return parsed;

        return MCVersions.normalize(fallbackMajor);
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
}
