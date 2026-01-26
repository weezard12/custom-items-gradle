package nl.knokko.customitems;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MCVersions {

	private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

	public static final int VERSION1_12_2 = version(1, 12, 2);
	public static final int VERSION1_13_0 = version(1, 13, 0);
	public static final int VERSION1_13_1 = version(1, 13, 1);
	public static final int VERSION1_13_2 = version(1, 13, 2);
	public static final int VERSION1_14_0 = version(1, 14, 0);
	public static final int VERSION1_14_4 = version(1, 14, 4);
	public static final int VERSION1_15_0 = version(1, 15, 0);
	public static final int VERSION1_15_2 = version(1, 15, 2);
	public static final int VERSION1_16_0 = version(1, 16, 0);
	public static final int VERSION1_16_5 = version(1, 16, 5);
	public static final int VERSION1_17_0 = version(1, 17, 0);
	public static final int VERSION1_17_1 = version(1, 17, 1);
	public static final int VERSION1_18_0 = version(1, 18, 0);
	public static final int VERSION1_18_2 = version(1, 18, 2);
	public static final int VERSION1_19_0 = version(1, 19, 0);
	public static final int VERSION1_19_1 = version(1, 19, 1);
	public static final int VERSION1_19_2 = version(1, 19, 2);
	public static final int VERSION1_19_3 = version(1, 19, 3);
	public static final int VERSION1_19_4 = version(1, 19, 4);
	public static final int VERSION1_20_0 = version(1, 20, 0);
	public static final int VERSION1_20_6 = version(1, 20, 6);
	public static final int VERSION1_21_0 = version(1, 21, 0);
	public static final int VERSION1_21_1 = version(1, 21, 1);
	public static final int VERSION1_21_2 = version(1, 21, 2);
	public static final int VERSION1_21_3 = version(1, 21, 3);
	public static final int VERSION1_21_4 = version(1, 21, 4);
	public static final int VERSION1_21_5 = version(1, 21, 5);
	public static final int VERSION1_21_6 = version(1, 21, 6);
	public static final int VERSION1_21_7 = version(1, 21, 7);
	public static final int VERSION1_21_8 = version(1, 21, 8);
	public static final int VERSION1_21_9 = version(1, 21, 9);
	public static final int VERSION1_21_10 = version(1, 21, 10);
	public static final int VERSION1_21_11 = version(1, 21, 11);

	public static final int VERSION1_12 = VERSION1_12_2;
	public static final int VERSION1_13 = VERSION1_13_0;
	public static final int VERSION1_14 = VERSION1_14_0;
	public static final int VERSION1_15 = VERSION1_15_0;
	public static final int VERSION1_16 = VERSION1_16_0;
	public static final int VERSION1_17 = VERSION1_17_0;
	public static final int VERSION1_18 = VERSION1_18_0;
	public static final int VERSION1_19 = VERSION1_19_0;
	public static final int VERSION1_20 = VERSION1_20_0;
	public static final int VERSION1_21 = VERSION1_21_0;

	public static final int FIRST_VERSION = VERSION1_12;
	public static final int LAST_VERSION = VERSION1_21_11;

	public static int version(int major, int minor, int patch) {
		return major * 10000 + minor * 100 + patch;
	}

	public static int version(int minor, int patch) {
		return version(1, minor, patch);
	}

	public static int normalizeLowerBound(int version) {
		if (version >= 1000) return version;
		switch (version) {
			case 12: return VERSION1_12_2;
			case 13: return VERSION1_13_0;
			case 14: return VERSION1_14_0;
			case 15: return VERSION1_15_0;
			case 16: return VERSION1_16_0;
			case 17: return VERSION1_17_0;
			case 18: return VERSION1_18_0;
			case 19: return VERSION1_19_0;
			case 20: return VERSION1_20_0;
			case 21: return VERSION1_21_0;
			default: return version;
		}
	}

	public static int normalizeUpperBound(int version) {
		if (version < 1000) {
			return latestPatchForMinor(1, version, version);
		}

		int patch = getPatch(version);
		if (patch > 0) return version;

		int major = getMajor(version);
		int minor = getMinor(version);
		return latestPatchForMinor(major, minor, version);
	}

	private static int latestPatchForMinor(int major, int minor, int fallback) {
		if (major != 1) return fallback;
		switch (minor) {
			case 12: return VERSION1_12_2;
			case 13: return VERSION1_13_2;
			case 14: return VERSION1_14_4;
			case 15: return VERSION1_15_2;
			case 16: return VERSION1_16_5;
			case 17: return VERSION1_17_1;
			case 18: return VERSION1_18_2;
			case 19: return VERSION1_19_4;
			case 20: return VERSION1_20_6;
			case 21: return VERSION1_21_11;
			default: return fallback;
		}
	}

	public static int normalize(int version) {
		if (version >= 1000) return version;
		switch (version) {
			case 12: return VERSION1_12_2;
			case 13: return VERSION1_13_2;
			case 14: return VERSION1_14_4;
			case 15: return VERSION1_15_2;
			case 16: return VERSION1_16_5;
			case 17: return VERSION1_17_1;
			case 18: return VERSION1_18_2;
			case 19: return VERSION1_19_4;
			case 20: return VERSION1_20_6;
			case 21: return VERSION1_21_11;
			default: return version;
		}
	}

	public static int getMajor(int version) {
		return normalize(version) / 10000;
	}

	public static int getMinor(int version) {
		return (normalize(version) / 100) % 100;
	}

	public static int getPatch(int version) {
		return normalize(version) % 100;
	}

	public static boolean isAtLeast(int version, int major, int minor, int patch) {
		int normalized = normalize(version);
		int other = version(major, minor, patch);
		return normalized >= other;
	}

	public static Integer parseVersion(String raw) {
		if (raw == null) return null;
		Matcher matcher = VERSION_PATTERN.matcher(raw);
		if (!matcher.find()) return null;
		int major = Integer.parseInt(matcher.group(1));
		int minor = Integer.parseInt(matcher.group(2));
		int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
		return version(major, minor, patch);
	}

	public static String createString(int version) {
		int normalized = normalize(version);
		int major = getMajor(normalized);
		int minor = getMinor(normalized);
		int patch = getPatch(normalized);
		if (major <= 0) {
			return "1." + normalized;
		}
		if (patch == 0) {
			return major + "." + minor;
		}
		return major + "." + minor + "." + patch;
	}
}
