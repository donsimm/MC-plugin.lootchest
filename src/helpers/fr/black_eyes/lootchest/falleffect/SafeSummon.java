package fr.black_eyes.lootchest.falleffect;

import fr.black_eyes.lootchest.simpleJavaPlugin.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Ersetzt den Rumpf von {@code FallingPackageEntity.summon()}. Schlaegt der NMS-basierte Fall-Effekt fehl (z.B. weil
 * eine neue Minecraft-Version NMS-Signaturen geaendert hat), wird stattdessen ein normaler fallender Block verwendet,
 * statt eine NullPointerException im Server-Thread auszuloesen.
 */
public final class SafeSummon {
    private static final Method ORIGINAL = lookup();

    private SafeSummon() {
    }

    /** summon0 ist die vom Patcher umbenannte Original-Methode und existiert beim Kompilieren noch nicht. */
    private static Method lookup() {
        try {
            return FallingPackageEntity.class.getMethod("summon0");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void callOriginal(FallingPackageEntity entity) throws Throwable {
        try {
            ORIGINAL.invoke(entity);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    public static void summon(FallingPackageEntity entity) throws Throwable {
        try {
            callOriginal(entity);
        } catch (Throwable t) {
            if (!entity.armorstand) {
                throw t;
            }
            Utils.logInfo("&cArmorstand-Fall-Effekt nicht kompatibel mit dieser Minecraft-Version (" + t + "), verwende fallenden Block.");
            entity.armorstand = false;
            entity.armorstandFall = null;
            callOriginal(entity);
        }
    }
}
