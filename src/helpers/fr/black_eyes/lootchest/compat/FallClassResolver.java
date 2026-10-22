package fr.black_eyes.lootchest.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Findet zur laufenden Minecraft-Version die passende {@code Fallv_*}-Klasse. Gibt es keine exakte, wird die naechst
 * aeltere vorhandene verwendet (26_2 -> 26_1). Eingabe und Ausgabe haben das Format "26_2" bzw. "1_21_11".
 */
public final class FallClassResolver {
    private static final String PREFIX = "fr.black_eyes.lootchest.falleffect.Fallv_";
    private static final int MAX_PATCH = 30;
    private static final int MAX_MINOR_PREVIOUS_MAJOR = 12;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private FallClassResolver() {
    }

    public static String resolve(String version) {
        return CACHE.computeIfAbsent(version, FallClassResolver::lookup);
    }

    /** "26_2_build_121" -> "26_2" (Paper 26.x haengt "build.<nr>" an die Version). */
    private static String numericPrefix(String version) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\d+(_\\d+)*").matcher(version);
        return m.find() ? m.group() : version;
    }

    private static String lookup(String raw) {
        String version = numericPrefix(raw);
        if (exists(version)) {
            return version;
        }
        String[] parts = version.split("_");
        if (parts.length < 2) {
            return version;
        }
        int major;
        int minor;
        int patch = 0;
        try {
            major = Integer.parseInt(parts[0]);
            minor = Integer.parseInt(parts[1]);
            if (parts.length > 2) {
                patch = Integer.parseInt(parts[2]);
            }
        } catch (NumberFormatException e) {
            return version;
        }

        for (int mi = minor; mi >= 0; mi--) {
            for (int pa = mi == minor ? patch : MAX_PATCH; pa >= 0; pa--) {
                String found = tryCandidate(major, mi, pa);
                if (found != null) {
                    return found;
                }
            }
        }
        if (major > 1) {
            for (int mi = MAX_MINOR_PREVIOUS_MAJOR; mi >= 0; mi--) {
                for (int pa = MAX_PATCH; pa >= 0; pa--) {
                    String found = tryCandidate(major - 1, mi, pa);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return version;
    }

    private static String tryCandidate(int major, int minor, int patch) {
        String name = patch == 0 ? major + "_" + minor : major + "_" + minor + "_" + patch;
        return exists(name) ? name : null;
    }

    private static boolean exists(String version) {
        try {
            Class.forName(PREFIX + version, false, FallClassResolver.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
