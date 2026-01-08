package nl.knokko.customitems.plugin.resourcepack;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.util.ProgrammingValidationException;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ResourcepackVersionHelper {

    private static final Pattern CRAFTBUKKIT_VERSION = Pattern.compile("v(\\d+)_(\\d+)_R(\\d+)");
    private static final Pattern MC_VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final int MODERN_1_21_CRAFTBUKKIT_REVISION = 6;
    private static final int LEGACY_1_21_PACK_FORMAT = 34;
    private static final int MODERN_1_21_PACK_FORMAT = 55;

    private ResourcepackVersionHelper() {
    }

    static boolean useModernItemModels(int mcVersion) {
        if (mcVersion < MCVersions.VERSION1_21) return false;
        if (mcVersion > MCVersions.VERSION1_21) return true;

        Integer craftBukkitRevision = getCraftBukkitRevision();
        if (craftBukkitRevision != null) {
            return craftBukkitRevision >= MODERN_1_21_CRAFTBUKKIT_REVISION;
        }

        McVersion version = getMinecraftVersion();
        if (version != null) {
            return version.isAtLeast(1, 21, 10);
        }

        return true;
    }

    static int getPackFormat(int mcVersion) throws ProgrammingValidationException {
        if (mcVersion == MCVersions.VERSION1_12) {
            return 3;
        } else if (mcVersion == MCVersions.VERSION1_13 || mcVersion == MCVersions.VERSION1_14) {
            return 4;
        } else if (mcVersion == MCVersions.VERSION1_15) {
            return 5;
        } else if (mcVersion == MCVersions.VERSION1_16) {
            return 6;
        } else if (mcVersion == MCVersions.VERSION1_17) {
            return 7;
        } else if (mcVersion == MCVersions.VERSION1_18) {
            return 8;
        } else if (mcVersion == MCVersions.VERSION1_19) {
            return 13;
        } else if (mcVersion == MCVersions.VERSION1_20) {
            return 32;
        } else if (mcVersion == MCVersions.VERSION1_21) {
            return useModernItemModels(mcVersion) ? MODERN_1_21_PACK_FORMAT : LEGACY_1_21_PACK_FORMAT;
        } else {
            throw new ProgrammingValidationException("Unknown pack format for mc version " + mcVersion);
        }
    }

    private static Integer getCraftBukkitRevision() {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String token = packageName.substring(packageName.lastIndexOf('.') + 1);
            Matcher matcher = CRAFTBUKKIT_VERSION.matcher(token);
            if (!matcher.matches()) return null;

            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int revision = Integer.parseInt(matcher.group(3));
            if (major == 1 && minor == 21) {
                return revision;
            }
        } catch (RuntimeException | NoClassDefFoundError ignored) {
            return null;
        }
        return null;
    }

    private static McVersion getMinecraftVersion() {
        String raw = null;
        try {
            raw = Bukkit.getVersion();
        } catch (RuntimeException | NoClassDefFoundError ignored) {
            raw = null;
        }
        McVersion parsed = parseMinecraftVersion(raw);
        if (parsed != null) return parsed;

        raw = reflectServerString("getMinecraftVersion");
        parsed = parseMinecraftVersion(raw);
        if (parsed != null) return parsed;

        raw = reflectServerString("getBukkitVersion");
        return parseMinecraftVersion(raw);
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

    private static McVersion parseMinecraftVersion(String raw) {
        if (raw == null) return null;
        Matcher matcher = MC_VERSION.matcher(raw);
        if (!matcher.find()) return null;

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        return new McVersion(major, minor, patch);
    }

    private static final class McVersion {

        private final int major;
        private final int minor;
        private final int patch;

        McVersion(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        boolean isAtLeast(int major, int minor, int patch) {
            if (this.major != major) return this.major > major;
            if (this.minor != minor) return this.minor > minor;
            return this.patch >= patch;
        }
    }
}
