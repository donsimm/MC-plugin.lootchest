package fr.black_eyes.lootchest.compat;

import fr.black_eyes.holograms.api.utils.reflect.Platform;
import fr.black_eyes.holograms.api.utils.reflect.Version;

import java.util.logging.Logger;

/**
 * Wird von {@code Version.fromMinecraftVersion} aufgerufen, wenn keine exakte Uebereinstimmung gefunden wurde.
 * Ist die laufende Minecraft-Version neuer als (oder gleich, aber auf anderer Plattform wie) alles, was das Plugin kennt, wird der neueste bekannte NMS-Adapter
 * verwendet, statt das Plugin zu deaktivieren. Aeltere Luecken (z.B. unbekannte alte Versionen) bleiben {@code null}.
 */
public final class VersionFallback {
    private static final Logger LOG = Logger.getLogger("LootChest");
    private static boolean warned = false;

    private VersionFallback() {
    }

    public static Version resolve(String minecraftVersion, Platform platform) {
        int[] target = parse(minecraftVersion);
        if (target == null) {
            return null;
        }

        int[] newestKnown = null;
        Version best = null;
        int[] bestVersion = null;
        for (Version candidate : Version.values()) {
            int[] entryMax = null;
            for (String s : candidate.getMinecraftVersions()) {
                int[] parsed = parse(s);
                if (parsed != null && (entryMax == null || compare(parsed, entryMax) > 0)) {
                    entryMax = parsed;
                }
            }
            if (entryMax == null) {
                continue;
            }
            if (newestKnown == null || compare(entryMax, newestKnown) > 0) {
                newestKnown = entryMax;
            }
            boolean usable = candidate.getPlatform() == Platform.ALL || candidate.getPlatform() == platform;
            int cmp = bestVersion == null ? 1 : compare(entryMax, bestVersion);
            // neuere Version gewinnt; bei Gleichstand gewinnt der Adapter, der zur Plattform passt
            if (cmp > 0 || (cmp == 0 && usable && best != null && best.getPlatform() != Platform.ALL
                    && best.getPlatform() != platform)) {
                best = candidate;
                bestVersion = entryMax;
            }
        }

        if (newestKnown == null || compare(target, newestKnown) < 0 || best == null) {
            return null;
        }
        if (!warned) {
            warned = true;
            LOG.warning("Minecraft " + minecraftVersion + " ist neuer als jede bekannte Version, verwende den NMS-Adapter "
                    + best.name() + " (Kompatibilitaets-Fallback).");
        }
        return best;
    }

    /**
     * Paper ab 26.x liefert z.B. "26.2.build.121-stable" statt "26.2-R0.1-SNAPSHOT". Gibt nur den fuehrenden numerischen
     * Teil zurueck ("26.2"); Strings ohne fuehrende Zahl bleiben unveraendert.
     */
    public static String sanitize(String version) {
        if (version == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\d+(\\.\\d+)*").matcher(version);
        return m.find() ? m.group() : version;
    }

    /** "26.2.1" -> {26,2,1}; "1.21" -> {1,21,0}; unbrauchbar -> null. */
    static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.split("[.\\-]");
        int[] out = new int[3];
        int n = 0;
        for (String part : parts) {
            if (n == 3) {
                break;
            }
            try {
                out[n++] = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                break;
            }
        }
        return n >= 2 ? out : null;
    }

    static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }
}
