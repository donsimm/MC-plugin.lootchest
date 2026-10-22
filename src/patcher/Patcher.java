import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Patcht das fertige LootChest-JAR (kein Quellcode vorhanden), damit es auch auf neueren Minecraft-Versionen laeuft.
 *
 * Aufruf: java Patcher <original.jar> <helperClassesDir> <output.jar>
 *
 * Jeder Patch prueft, dass er seine Zielstelle gefunden hat. Aendert sich das Original (neue Upstream-Version) so,
 * dass ein Patch nicht mehr passt, bricht der Build mit einer klaren Meldung ab, statt ein kaputtes JAR zu erzeugen.
 */
public final class Patcher {
    private static final String VERSION_CLASS = "fr/black_eyes/holograms/api/utils/reflect/Version";
    private static final String PLATFORM_CLASS = "fr/black_eyes/holograms/api/utils/reflect/Platform";
    private static final String FALL_ENTITY = "fr/black_eyes/lootchest/falleffect/FallingPackageEntity";
    private static final String VERSION_FALLBACK = "fr/black_eyes/lootchest/compat/VersionFallback";
    private static final String FALL_RESOLVER = "fr/black_eyes/lootchest/compat/FallClassResolver";
    private static final String SAFE_SUMMON = "fr/black_eyes/lootchest/falleffect/SafeSummon";
    /** Bukkit.getVersion() liefert z.B. "26.2.1-121-abc (MC: 26.2.1)"; "1.7" kann dort zufaellig vorkommen (26.11.7). */
    private static final String LEGACY_MARKER = "1.7";
    private static final String LEGACY_MARKER_FIXED = "(MC: 1.7";

    private final Set<String> applied = new HashSet<>();

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: Patcher <original.jar> <helperClassesDir> <output.jar>");
            System.exit(2);
        }
        new Patcher().run(Paths.get(args[0]), Paths.get(args[1]), Paths.get(args[2]));
    }

    private void run(Path input, Path helpers, Path output) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Set<String> written = new HashSet<>();
        try (JarFile jar = new JarFile(input.toFile());
             OutputStream os = Files.newOutputStream(output);
             JarOutputStream out = new JarOutputStream(os)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".SF") || entry.getName().endsWith(".RSA") || entry.getName().endsWith(".DSA")) {
                    continue;
                }
                byte[] data;
                try (InputStream is = jar.getInputStream(entry)) {
                    data = readAll(is);
                }
                if (entry.getName().endsWith(".class")) {
                    data = transform(entry.getName(), data);
                }
                put(out, written, entry.getName(), data);
            }

            try (Stream<Path> files = Files.walk(helpers)) {
                List<Path> classes = new ArrayList<>();
                files.filter(p -> p.toString().endsWith(".class")).forEach(classes::add);
                for (Path p : classes) {
                    String name = helpers.relativize(p).toString().replace('\\', '/');
                    if (!written.add(name)) {
                        throw new IllegalStateException("Helper-Klasse ueberschreibt Original: " + name);
                    }
                    put(out, null, name, Files.readAllBytes(p));
                }
            }
            put(out, written, "META-INF/lootchest-compat.txt", ("Patched for newer Minecraft versions: " + applied + "\n")
                    .getBytes(StandardCharsets.UTF_8));
        }

        String[] required = {"version-fallback", "fall-class-resolver", "safe-summon", "legacy-marker"};
        for (String patch : required) {
            if (!applied.contains(patch)) {
                Files.deleteIfExists(output);
                throw new IllegalStateException("Patch '" + patch + "' hat seine Zielstelle im Original nicht gefunden. "
                        + "Das Upstream-JAR hat sich geaendert, Patcher.java muss angepasst werden.");
            }
        }
        System.out.println("Patches angewendet: " + applied);
    }

    private static void put(JarOutputStream out, Set<String> written, String name, byte[] data) throws IOException {
        if (written != null && !written.add(name)) {
            return;
        }
        out.putNextEntry(new JarEntry(name));
        out.write(data);
        out.closeEntry();
    }

    private byte[] transform(String entryName, byte[] data) {
        String internal = entryName.substring(0, entryName.length() - ".class".length());
        if (!internal.startsWith("fr/black_eyes/")) {
            return data;
        }
        ClassNode node = new ClassNode();
        new ClassReader(data).accept(node, 0);
        boolean changed = false;

        if (internal.equals(VERSION_CLASS)) {
            changed |= patchVersionFallback(node);
        }
        if (internal.equals(FALL_ENTITY)) {
            changed |= patchFallEntity(node);
        }
        changed |= patchLegacyMarker(node);

        if (!changed) {
            return data;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    /** Version.fromMinecraftVersion: "return null" -> "return VersionFallback.resolve(version, platform)". */
    private boolean patchVersionFallback(ClassNode node) {
        MethodNode method = find(node, "fromMinecraftVersion");
        if (method == null) {
            return false;
        }
        AbstractInsnNode last = method.instructions.getLast();
        while (last != null && last.getOpcode() < 0) {
            last = last.getPrevious();
        }
        AbstractInsnNode prev = last == null ? null : last.getPrevious();
        if (last == null || last.getOpcode() != Opcodes.ARETURN || prev == null || prev.getOpcode() != Opcodes.ACONST_NULL) {
            return false;
        }
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, VERSION_FALLBACK, "resolve",
                "(Ljava/lang/String;L" + PLATFORM_CLASS + ";)L" + VERSION_CLASS + ";", false));
        method.instructions.insertBefore(prev, list);
        method.instructions.remove(prev);
        applied.add("version-fallback");
        return true;
    }

    /**
     * FallingPackageEntity: Klassenname-Aufloesung mit Fallback auf aeltere Fallv_*-Klassen und
     * summon() -> SafeSummon.summon(this) -> summon0() (Original).
     */
    private boolean patchFallEntity(ClassNode node) {
        MethodNode summon = find(node, "summon");
        if (summon == null || !summon.desc.equals("()V")) {
            return false;
        }

        boolean resolverInserted = false;
        for (AbstractInsnNode insn = summon.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                AbstractInsnNode next = insn.getNext();
                if (call.owner.equals("java/lang/String") && call.name.equals("replace")
                        && next != null && next.getOpcode() == Opcodes.ASTORE) {
                    summon.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, FALL_RESOLVER, "resolve",
                            "(Ljava/lang/String;)Ljava/lang/String;", false));
                    resolverInserted = true;
                    break;
                }
            }
        }
        if (!resolverInserted) {
            return false;
        }
        applied.add("fall-class-resolver");

        summon.name = "summon0";
        MethodNode wrapper = new MethodNode(Opcodes.ACC_PUBLIC, "summon", "()V", null, null);
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SAFE_SUMMON, "summon", "(L" + FALL_ENTITY + ";)V", false));
        wrapper.instructions.add(new InsnNode(Opcodes.RETURN));
        wrapper.maxStack = 1;
        wrapper.maxLocals = 1;
        node.methods.add(wrapper);
        applied.add("safe-summon");
        return true;
    }

    /** LDC "1.7" direkt nach Bukkit.getVersion() -> "(MC: 1.7", damit zufaellige Treffer in Build-Nummern entfallen. */
    private boolean patchLegacyMarker(ClassNode node) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode) || !LEGACY_MARKER.equals(((LdcInsnNode) insn).cst)) {
                    continue;
                }
                AbstractInsnNode prev = insn.getPrevious();
                while (prev != null && prev.getOpcode() < 0) {
                    prev = prev.getPrevious();
                }
                if (prev instanceof MethodInsnNode && ((MethodInsnNode) prev).name.equals("getVersion")
                        && ((MethodInsnNode) prev).owner.equals("org/bukkit/Bukkit")) {
                    ((LdcInsnNode) insn).cst = LEGACY_MARKER_FIXED;
                    applied.add("legacy-marker");
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static MethodNode find(ClassNode node, String name) {
        for (MethodNode m : node.methods) {
            if (m.name.equals(name)) {
                return m;
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
