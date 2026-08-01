package nl.knokko.customitems.plugin.resourcepack;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.util.ProgrammingValidationException;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ResourcepackVersionHelper {

    private static final Pattern CRAFTBUKKIT_VERSION = Pattern.compile("v(\\d+)_(\\d+)_R(\\d+)");
    private static final int MODERN_1_21_CRAFTBUKKIT_REVISION = 6;
    private static final int LEGACY_1_21_PACK_FORMAT = 34;
    private static final int MODERN_1_21_PACK_FORMAT = 55;

    private ResourcepackVersionHelper() {
    }

    static boolean useModernItemModels(int mcVersion) {
        int major = MCVersions.getMajor(mcVersion);
        if (major > 1) return true;
        int minor = MCVersions.getMinor(mcVersion);
        if (minor < 21) return false;
        if (minor > 21) return true;

        Integer craftBukkitRevision = getCraftBukkitRevision();
        if (craftBukkitRevision != null) {
            return craftBukkitRevision >= MODERN_1_21_CRAFTBUKKIT_REVISION;
        }

        Integer version = getMinecraftVersion();
        if (version != null) return MCVersions.isAtLeast(version, 1, 21, 10);

        return MCVersions.isAtLeast(mcVersion, 1, 21, 10);
    }

    static int getPackFormat(int mcVersion) throws ProgrammingValidationException {
        int major = MCVersions.getMajor(mcVersion);
        int minor = MCVersions.getMinor(mcVersion);
        if (major == 26) {
            if (minor == 1) return 84;
            if (minor == 2) return 88;
            throw new ProgrammingValidationException("Unknown pack format for mc version " + mcVersion);
        } else if (minor == 12) {
            return 3;
        } else if (minor == 13 || minor == 14) {
            return 4;
        } else if (minor == 15) {
            return 5;
        } else if (minor == 16) {
            return 6;
        } else if (minor == 17) {
            return 7;
        } else if (minor == 18) {
            return 8;
        } else if (minor == 19) {
            return 13;
        } else if (minor == 20) {
            if (MCVersions.isAtLeast(mcVersion, 1, 20, 5)) {
                return 32;
            } else if (MCVersions.isAtLeast(mcVersion, 1, 20, 4)) {
                return 22;
            } else if (MCVersions.isAtLeast(mcVersion, 1, 20, 2)) {
                return 18;
            } else {
                return 15;
            }
        } else if (minor == 21) {
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

    private static Integer getMinecraftVersion() {
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
        return MCVersions.parseVersion(raw);
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
}
