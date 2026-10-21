package com.kasper.playerstatuses.packaging;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Builds one jar out of the jars of several Minecraft bands.
 * <p>
 * The common classes (no Minecraft in them) go in once, at their own names, together with the
 * "base": the dispatcher, which picks the band at startup, and the jar's metadata. Every band jar
 * brings its own glue: each glue class is moved to {@code com/kasper/playerstatuses/b/<band>/...}, so the
 * bands do not clash, and loses its {@code @Mod} (only the dispatcher is a Forge / NeoForge mod).
 * The band's copy of the common classes and its metadata are left out; any other file must be the
 * same in every band.
 * <p>
 * Moving the glue out of the common package is safe only if the glue uses nothing package-private
 * there, so that is checked: every class, field and method of the common classes a glue class uses
 * must be public (or protected, from a subclass), must exist, and no glue method may be meant to
 * override a package-private one. Class file versions are checked too: Forge and NeoForge read every
 * class of a jar at startup, so a class newer than their ASM can read breaks the game even if it is
 * never loaded.
 * <p>
 * The class that loses its {@code @Mod} is what the dispatcher has to create for the band: with
 * {@code --mod-table entry} it is written as {@code <band>.mod=<class>} at the end of that properties
 * file of the base.
 * <p>
 * Usage: MergeJars --out universal.jar --common dir|jar... --base dir|jar... --band id=jar...
 * [--band-java id=major] [--max-major n] [--base-max-major n] [--mod-table entry]
 */
public final class MergeJars {

    private static final String ROOT = "com/kasper/playerstatuses/";

    /** Band files that the base replaces: each band has its own, the merged jar one. */
    private static final Set<String> METADATA = Set.of(
            "META-INF/MANIFEST.MF", "fabric.mod.json", "quilt.mod.json", "pack.mcmeta",
            "META-INF/mods.toml", "META-INF/neoforge.mods.toml");

    private static final Set<String> STRIPPED = Set.of(
            "Lnet/minecraftforge/fml/common/Mod;", "Lnet/neoforged/fml/common/Mod;");

    /** Annotations a loader acts on in any class: in a moved glue class they would act a second time. */
    private static final Set<String> FORBIDDEN = Set.of(
            "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;",
            "Lnet/neoforged/fml/common/Mod$EventBusSubscriber;",
            "Lnet/neoforged/fml/common/EventBusSubscriber;",
            "Lde/maxhenkel/voicechat/api/ForgeVoicechatPlugin;");

    /** A fixed time for every entry, so the same input gives the same jar. */
    private static final long TIME = 315532800000L + 86400000L * 31;

    record Info(String name, int access, String superName, List<String> interfaces,
                Map<String, Integer> methods, Map<String, Integer> fields) {
        boolean isPublic() {
            return (access & Opcodes.ACC_PUBLIC) != 0;
        }
    }

    record Member(Info owner, int access) {
    }

    private final Map<String, byte[]> out = new TreeMap<>();
    private final Set<String> common = new HashSet<>();
    private final Set<String> base = new HashSet<>();
    private final Map<String, Info> commonInfo = new HashMap<>();
    private final Map<String, Info> jdkInfo = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final Map<String, String> modClasses = new TreeMap<>();

    public static void main(String[] args) throws IOException {
        Path outPath = null;
        List<Path> commonPaths = new ArrayList<>();
        List<Path> basePaths = new ArrayList<>();
        Map<String, Path> bands = new LinkedHashMap<>();
        Map<String, Integer> bandJava = new HashMap<>();
        int maxMajor = Integer.MAX_VALUE;
        int baseMaxMajor = Integer.MAX_VALUE;
        String modTable = null;
        for (int i = 0; i < args.length; i++) {
            String value = i + 1 < args.length ? args[i + 1] : null;
            if (value == null) {
                throw new IllegalArgumentException("No value after " + args[i]);
            }
            switch (args[i++]) {
                case "--out" -> outPath = Paths.get(value);
                case "--common" -> commonPaths.add(Paths.get(value));
                case "--base" -> basePaths.add(Paths.get(value));
                case "--band" -> {
                    String[] kv = pair(value);
                    if (!kv[0].matches("[a-z][a-z0-9_]*") || bands.put(kv[0], Paths.get(kv[1])) != null) {
                        throw new IllegalArgumentException("Bad or repeated band id: " + kv[0]);
                    }
                }
                case "--band-java" -> {
                    String[] kv = pair(value);
                    bandJava.put(kv[0], Integer.parseInt(kv[1]));
                }
                case "--max-major" -> maxMajor = Integer.parseInt(value);
                case "--base-max-major" -> baseMaxMajor = Integer.parseInt(value);
                case "--mod-table" -> modTable = value;
                default -> throw new IllegalArgumentException("Unknown option " + args[i - 1]);
            }
        }
        if (outPath == null || commonPaths.isEmpty() || bands.isEmpty()) {
            throw new IllegalArgumentException("Need --out, --common and at least one --band");
        }
        MergeJars merge = new MergeJars();
        merge.run(outPath, commonPaths, basePaths, bands, bandJava, maxMajor, baseMaxMajor, modTable);
    }

    private static String[] pair(String value) {
        int eq = value.indexOf('=');
        if (eq <= 0) {
            throw new IllegalArgumentException("Expected key=value: " + value);
        }
        return new String[]{value.substring(0, eq), value.substring(eq + 1)};
    }

    private void run(Path outPath, List<Path> commonPaths, List<Path> basePaths, Map<String, Path> bands,
                     Map<String, Integer> bandJava, int maxMajor, int baseMaxMajor, String modTable) throws IOException {
        for (Path p : commonPaths) {
            read(p).forEach((name, bytes) -> {
                add(name, bytes, "common");
                if (name.endsWith(".class")) {
                    common.add(className(name));
                }
            });
        }
        for (Path p : basePaths) {
            read(p).forEach((name, bytes) -> {
                if (name.endsWith(".class") && common.contains(className(name))) {
                    errors.add("Base class " + name + " is also a common class");
                }
                add(name, bytes, "base");
                if (name.endsWith(".class")) {
                    base.add(className(name));
                }
            });
        }
        for (String name : common) {
            commonInfo.put(name, info(out.get(name + ".class")));
        }
        for (String name : out.keySet()) {
            if (name.endsWith(".class")) {
                checkMajor(name, out.get(name), Math.min(maxMajor, baseMaxMajor), "common/base");
            }
        }

        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Path> band : bands.entrySet()) {
            summary.append(mergeBand(band.getKey(), band.getValue(),
                    Math.min(maxMajor, bandJava.getOrDefault(band.getKey(), Integer.MAX_VALUE))));
        }

        if (modTable != null) {
            writeModTable(modTable, bands.keySet());
        } else if (!modClasses.isEmpty()) {
            errors.add("@Mod was removed from " + modClasses.values() + ", but there is no --mod-table to create them by");
        }

        if (!errors.isEmpty()) {
            System.err.println("MergeJars: " + errors.size() + " problem(s) in " + outPath.getFileName());
            new TreeSet<>(errors).forEach(e -> System.err.println("  " + e));
            System.exit(1);
        }
        write(outPath);
        System.out.print(summary);
        System.out.println("MergeJars: wrote " + outPath + " (" + out.size() + " files, "
                + common.size() + " common classes)");
    }

    private String mergeBand(String id, Path jar, int bandMax) throws IOException {
        Map<String, byte[]> files = read(jar);
        Map<String, byte[]> glue = new TreeMap<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String name = e.getKey();
            if (name.startsWith("META-INF/versions/") || name.endsWith("module-info.class")
                    || name.startsWith("META-INF/jars/")) {
                errors.add(id + ": " + name + " is not supported in a merged jar");
            } else if (name.endsWith(".class")) {
                String cls = className(name);
                if (common.contains(cls)) {
                    continue;
                }
                if (base.contains(cls) || !cls.startsWith(ROOT) || cls.startsWith(ROOT + "b/")) {
                    errors.add(id + ": class " + cls + " is neither common nor glue");
                    continue;
                }
                glue.put(cls, e.getValue());
            } else if (!METADATA.contains(name) && !isSignature(name)) {
                add(name, e.getValue(), id);
            }
        }

        Map<String, Info> glueInfo = new HashMap<>();
        Map<String, String> moved = new HashMap<>();
        for (Map.Entry<String, byte[]> e : glue.entrySet()) {
            glueInfo.put(e.getKey(), info(e.getValue()));
            moved.put(e.getKey(), ROOT + "b/" + id + "/" + e.getKey().substring(ROOT.length()));
        }
        SimpleRemapper remapper = new SimpleRemapper(Opcodes.ASM9, moved);
        int highest = 0;
        int strippedMods = 0;
        for (Map.Entry<String, byte[]> e : glue.entrySet()) {
            String cls = e.getKey();
            byte[] bytes = e.getValue();
            check(id, cls, bytes, glueInfo);
            highest = Math.max(highest, major(bytes));
            String target = moved.get(cls) + ".class";
            checkMajor(target, bytes, bandMax, id);
            ClassWriter writer = new ClassWriter(0);
            int[] stripped = {0};
            ClassVisitor strip = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (STRIPPED.contains(descriptor)) {
                        stripped[0]++;
                        return null;
                    }
                    return super.visitAnnotation(descriptor, visible);
                }
            };
            new ClassReader(bytes).accept(new ClassRemapper(strip, remapper), 0);
            strippedMods += stripped[0];
            if (stripped[0] > 0 && modClasses.put(id, moved.get(cls).replace('/', '.')) != null) {
                errors.add(id + ": more than one class has @Mod");
            }
            add(target, writer.toByteArray(), id);
        }
        return String.format("  %-10s %4d glue classes, class files up to Java %d%s%n", id, glue.size(),
                highest - 44, strippedMods > 0 ? ", @Mod removed from " + strippedMods : "");
    }

    /** What changes for a glue class once it leaves the common package. */
    private void check(String band, String cls, byte[] bytes, Map<String, Info> glueInfo) {
        String where = band + ": " + cls.substring(ROOT.length());
        Info self = glueInfo.get(cls);
        Map<String, Info> known = new HashMap<>(commonInfo);
        known.putAll(glueInfo);

        // An override of a package-private method stops overriding in another package
        for (Info ancestor = lookup(self.superName, known); ancestor != null; ancestor = lookup(ancestor.superName, known)) {
            if (!common.contains(ancestor.name)) {
                continue;
            }
            for (Map.Entry<String, Integer> m : ancestor.methods.entrySet()) {
                int access = m.getValue();
                boolean packagePrivate = (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) == 0;
                if (packagePrivate && (access & Opcodes.ACC_STATIC) == 0 && !m.getKey().startsWith("<")
                        && self.methods.containsKey(m.getKey())) {
                    errors.add(where + " overrides package-private " + ancestor.name + "." + m.getKey());
                }
            }
        }

        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                useClass(superName);
                if (interfaces != null) {
                    Arrays.stream(interfaces).forEach(this::useClass);
                }
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (FORBIDDEN.contains(descriptor)) {
                    errors.add(where + " has " + descriptor + ", which a loader would act on in the moved copy");
                }
                return null;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        useType(Type.getObjectType(type));
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        useMember(owner, name, descriptor, false);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        useMember(owner, name, descriptor, true);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bsm, Object... args) {
                        useHandle(bsm);
                        for (Object arg : args) {
                            useConstant(arg);
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        useConstant(value);
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String descriptor, int dims) {
                        useType(Type.getType(descriptor));
                    }

                    @Override
                    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                        if (type != null) {
                            useClass(type);
                        }
                    }
                };
            }

            private void useConstant(Object value) {
                if (value instanceof Type type) {
                    useType(type);
                } else if (value instanceof Handle handle) {
                    useHandle(handle);
                }
            }

            private void useHandle(Handle handle) {
                useMember(handle.getOwner(), handle.getName(), handle.getDesc(), handle.getTag() >= Opcodes.H_INVOKEVIRTUAL);
            }

            private void useType(Type type) {
                if (type.getSort() == Type.ARRAY) {
                    type = type.getElementType();
                }
                if (type.getSort() == Type.OBJECT) {
                    useClass(type.getInternalName());
                }
            }

            private void useClass(String name) {
                Info info = commonInfo.get(name);
                if (info != null && !info.isPublic()) {
                    errors.add(where + " uses package-private class " + name);
                }
            }

            private void useMember(String owner, String name, String descriptor, boolean method) {
                if (owner.startsWith("[")) {
                    return;
                }
                useClass(owner);
                if (!common.contains(owner) && !glueInfo.containsKey(owner)) {
                    return;
                }
                Member found = method ? resolveMethod(owner, name + descriptor, known)
                        : resolveField(owner, name + ":" + descriptor, known, new HashSet<>());
                if (found == null) {
                    // The hierarchy runs into a class outside this jar and the JDK (Minecraft, a loader): not ours to check
                    return;
                }
                if (found.owner == null) {
                    if (common.contains(owner)) {
                        errors.add(where + " uses " + owner + "." + name + descriptor + ", which the common classes lack");
                    }
                    return;
                }
                if (!common.contains(found.owner.name)) {
                    return;
                }
                int access = found.access;
                if ((access & Opcodes.ACC_PUBLIC) != 0) {
                    return;
                }
                if ((access & Opcodes.ACC_PROTECTED) != 0 && isSubclass(cls, found.owner.name, known)) {
                    return;
                }
                errors.add(where + " uses non-public " + found.owner.name + "." + name + descriptor);
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    /** Where a method is declared (JVMS 5.4.3.3); null if unknowable, an empty Member if it does not exist. */
    private Member resolveMethod(String owner, String key, Map<String, Info> known) {
        List<Info> classes = new ArrayList<>();
        for (String name = owner; name != null; ) {
            Info info = lookup(name, known);
            if (info == null) {
                return null;
            }
            Integer access = info.methods.get(key);
            if (access != null) {
                return new Member(info, access);
            }
            classes.add(info);
            name = info.superName;
        }
        Deque<String> queue = new ArrayDeque<>();
        classes.forEach(c -> queue.addAll(c.interfaces));
        Set<String> seen = new HashSet<>();
        boolean unknown = false;
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (!seen.add(name)) {
                continue;
            }
            Info info = lookup(name, known);
            if (info == null) {
                unknown = true;
                continue;
            }
            Integer access = info.methods.get(key);
            if (access != null && (access & Opcodes.ACC_STATIC) == 0) {
                return new Member(info, access);
            }
            queue.addAll(info.interfaces);
        }
        if (unknown || isSignaturePolymorphic(owner)) {
            return null;
        }
        return new Member(null, 0);
    }

    /** Where a field is declared (JVMS 5.4.3.2); null if unknowable, an empty Member if it does not exist. */
    private Member resolveField(String owner, String key, Map<String, Info> known, Set<String> seen) {
        if (owner == null) {
            return new Member(null, 0);
        }
        if (!seen.add(owner)) {
            return new Member(null, 0);
        }
        Info info = lookup(owner, known);
        if (info == null) {
            return null;
        }
        Integer access = info.fields.get(key);
        if (access != null) {
            return new Member(info, access);
        }
        boolean unknown = false;
        for (String i : info.interfaces) {
            Member m = resolveField(i, key, known, seen);
            if (m == null) {
                unknown = true;
            } else if (m.owner != null) {
                return m;
            }
        }
        Member m = resolveField(info.superName, key, known, seen);
        if (m == null || m.owner != null) {
            return m;
        }
        return unknown ? null : m;
    }

    private static boolean isSignaturePolymorphic(String owner) {
        return owner.equals("java/lang/invoke/MethodHandle") || owner.equals("java/lang/invoke/VarHandle");
    }

    private boolean isSubclass(String cls, String ancestor, Map<String, Info> known) {
        for (Info info = lookup(cls, known); info != null; info = lookup(info.superName, known)) {
            if (info.name.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    /** A class of this jar or of the JDK; null for anything else. */
    private Info lookup(String name, Map<String, Info> known) {
        if (name == null) {
            return null;
        }
        Info info = known.get(name);
        if (info != null || !(name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/"))) {
            return info;
        }
        return jdkInfo.computeIfAbsent(name, n -> {
            try (InputStream in = ClassLoader.getSystemResourceAsStream(n + ".class")) {
                return in == null ? null : info(in.readAllBytes());
            } catch (IOException e) {
                return null;
            }
        });
    }

    private static Info info(byte[] bytes) {
        Map<String, Integer> methods = new HashMap<>();
        Map<String, Integer> fields = new HashMap<>();
        Object[] head = new Object[4];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                head[0] = name;
                head[1] = access;
                head[2] = superName;
                head[3] = interfaces == null ? List.of() : List.of(interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                fields.put(name + ":" + descriptor, access);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                methods.put(name + descriptor, access);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        @SuppressWarnings("unchecked")
        List<String> interfaces = (List<String>) head[3];
        return new Info((String) head[0], (Integer) head[1], (String) head[2], interfaces, methods, fields);
    }

    /** Appends the class each band's dispatcher creates to a properties file of the base. */
    private void writeModTable(String entry, Set<String> bands) {
        byte[] old = out.get(entry);
        if (old == null) {
            errors.add("--mod-table " + entry + " is not a file of the base");
            return;
        }
        StringBuilder text = new StringBuilder(new String(old, StandardCharsets.ISO_8859_1));
        if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
        for (String band : bands) {
            String cls = modClasses.get(band);
            if (cls == null) {
                errors.add(band + ": no class has @Mod, so the dispatcher has nothing to create");
            } else {
                text.append(band).append(".mod=").append(cls).append('\n');
            }
        }
        out.put(entry, text.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    private void checkMajor(String name, byte[] bytes, int max, String what) {
        int major = major(bytes);
        if (major > max) {
            errors.add(what + ": " + name + " is Java " + (major - 44) + " bytecode, above Java " + (max - 44));
        }
    }

    private static int major(byte[] bytes) {
        return ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
    }

    private void add(String name, byte[] bytes, String from) {
        byte[] old = out.putIfAbsent(name, bytes);
        if (old != null && !Arrays.equals(old, bytes)) {
            errors.add(from + ": " + name + " differs from the copy already in the jar");
        }
    }

    private static boolean isSignature(String name) {
        return name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"));
    }

    private static String className(String entry) {
        return entry.substring(0, entry.length() - ".class".length());
    }

    /** The files of a jar or a directory, by path. */
    private static Map<String, byte[]> read(Path path) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    files.put(path.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
                }
            }
        } else if (Files.isRegularFile(path)) {
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
                for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                    if (!e.isDirectory()) {
                        files.put(e.getName(), zip.readAllBytes());
                    }
                }
            }
        } else {
            throw new IOException("Missing input: " + path);
        }
        return files;
    }

    private void write(Path path) throws IOException {
        byte[] manifest = out.remove("META-INF/MANIFEST.MF");
        if (manifest == null) {
            manifest = "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            Set<String> dirs = new TreeSet<>();
            dirs.add("META-INF/");
            for (String name : out.keySet()) {
                for (int i = name.indexOf('/'); i >= 0; i = name.indexOf('/', i + 1)) {
                    dirs.add(name.substring(0, i + 1));
                }
            }
            put(zip, "META-INF/", null);
            put(zip, "META-INF/MANIFEST.MF", manifest);
            for (String dir : dirs) {
                if (!dir.equals("META-INF/")) {
                    put(zip, dir, null);
                }
            }
            for (Map.Entry<String, byte[]> e : out.entrySet()) {
                put(zip, e.getKey(), e.getValue());
            }
        }
        try (OutputStream file = Files.newOutputStream(path)) {
            buffer.writeTo(file);
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(TIME);
        zip.putNextEntry(entry);
        if (bytes != null) {
            zip.write(bytes);
        }
        zip.closeEntry();
    }
}
