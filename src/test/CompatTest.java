import fr.black_eyes.holograms.api.utils.reflect.Platform;
import fr.black_eyes.holograms.api.utils.reflect.Version;
import fr.black_eyes.lootchest.compat.FallClassResolver;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Proxy;

/** Prueft das gepatchte JAR gegen simulierte Bukkit-Versionen (ohne echten Server). Aufruf: CompatTest <bukkitVersion> */
public class CompatTest {
    public static void main(String[] args) throws Exception {
        String bukkitVersion = args[0];
        Bukkit.server = (Server) Proxy.newProxyInstance(CompatTest.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, a) -> method.getName().equals("getBukkitVersion") ? bukkitVersion : "git-Paper-121 (MC: " + bukkitVersion.split("-")[0] + ")");

        for (Platform platform : new Platform[]{Platform.PAPER, Platform.SPIGOT}) {
            Version v = Version.fromMinecraftVersion(bukkitVersion.split("-")[0], platform);
            System.out.println(bukkitVersion + " / " + platform + " -> " + v);
        }
        try {
            System.out.println("Main.getCompleteVersion = " + fr.black_eyes.lootchest.Main.getCompleteVersion()
                    + ", Main.getVersion = " + fr.black_eyes.lootchest.Main.getVersion());
        } catch (Throwable t) {
            System.out.println("Main.* FEHLER: " + t);
        }
        System.out.println("Version.CURRENT = " + Version.CURRENT);
        for (String s : new String[]{"26_2_build_121", "26_2", "26_2_1", "26_1", "26_1_2", "27_0", "1_21_11", "1_21_4", "1_21", "9_9"}) {
            System.out.println("Fall " + s + " -> " + FallClassResolver.resolve(s));
        }
    }
}
