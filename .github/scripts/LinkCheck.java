import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Checks that a mod jar built against one Minecraft version links against another.
 * <p>
 * Every class, method and field the jar uses outside itself is resolved twice: against the
 * classpath it was built with (the reference) and against the classpath of the target version.
 * The same is done for the methods the jar overrides and for abstract methods it must implement.
 * Any result that differs between the two is reported: a missing class or member, a method that
 * became static, a changed return type, an override that no longer overrides, a new abstract
 * method. Comparing with the reference keeps the check honest: anything the tool cannot see
 * (reflection, optional mods) looks the same on both sides and is not reported.
 * <p>
 * Usage: java LinkCheck.java mod.jar reference-classpath.txt target-classpath.txt [allowed.txt]
 * (a classpath file lists one jar or class directory per line). The optional allowed file lists
 * differences that were reviewed and are safe, one key per line as LinkCheck prints it after "DIFF";
 * "#" starts a comment. Needs Java 24+ (ClassFile API).
 */
public final class LinkCheck {

    record Info(String name, String superName, List<String> interfaces, boolean isInterface, boolean isAbstract,
                Map<String, Integer> methods, Map<String, Integer> fields) {
    }

    /** Classes by internal name, from jars, directories and the running JDK. */
    static final class Classpath {
        private final Map<String, Path> dirs = new HashMap<>();
        private final Map<String, JarFile> jars = new HashMap<>();
        private final Map<String, Path> jdk = new HashMap<>();
        private final Map<String, Info> cache = new HashMap<>();

        Classpath(List<Path> entries, Map<String, Path> jdkIndex) throws IOException {
            jdk.putAll(jdkIndex);
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    try (Stream<Path> walk = Files.walk(entry)) {
                        walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                            String name = entry.relativize(p).toString().replace('\\', '/');
                            dirs.putIfAbsent(name.substring(0, name.length() - 6), p);
                        });
                    }
                } else if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
                    JarFile jar = new JarFile(entry.toFile());
                    jar.stream().map(e -> e.getName())
                            .filter(n -> n.endsWith(".class") && !n.startsWith("META-INF/"))
                            .forEach(n -> jars.putIfAbsent(n.substring(0, n.length() - 6), jar));
                }
            }
        }

        Info get(String name) {
            if (cache.containsKey(name)) {
                return cache.get(name);
            }
            Info info = null;
            try {
                byte[] bytes = null;
                if (dirs.containsKey(name)) {
                    bytes = Files.readAllBytes(dirs.get(name));
                } else if (jars.containsKey(name)) {
                    JarFile jar = jars.get(name);
                    try (InputStream in = jar.getInputStream(jar.getEntry(name + ".class"))) {
                        bytes = in.readAllBytes();
                    }
                } else if (jdk.containsKey(name)) {
                    bytes = Files.readAllBytes(jdk.get(name));
                }
                if (bytes != null) {
                    info = parse(ClassFile.of().parse(bytes));
                }
            } catch (IOException | IllegalArgumentException e) {
                info = null;
            }
            cache.put(name, info);
            return info;
        }
    }

    static Info parse(ClassModel cm) {
        Map<String, Integer> methods = new LinkedHashMap<>();
        for (MethodModel m : cm.methods()) {
            methods.put(m.methodName().stringValue() + m.methodType().stringValue(), m.flags().flagsMask());
        }
        Map<String, Integer> fields = new LinkedHashMap<>();
        for (FieldModel f : cm.fields()) {
            fields.put(f.fieldName().stringValue() + ":" + f.fieldType().stringValue(), f.flags().flagsMask());
        }
        List<String> interfaces = cm.interfaces().stream().map(ClassEntry::asInternalName).toList();
        return new Info(cm.thisClass().asInternalName(),
                cm.superclass().map(ClassEntry::asInternalName).orElse(null), interfaces,
                cm.flags().has(AccessFlag.INTERFACE), cm.flags().has(AccessFlag.ABSTRACT), methods, fields);
    }

    /** The class and all its ancestors, nearest first; unknown ancestors are listed as "?name". */
    static List<String> ancestry(Classpath cp, String start) {
        List<String> out = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (!seen.add(name)) {
                continue;
            }
            Info info = cp.get(name);
            if (info == null) {
                out.add("?" + name);
                continue;
            }
            out.add(name);
            if (info.superName() != null) {
                queue.add(info.superName());
            }
            queue.addAll(info.interfaces());
        }
        return out;
    }

    static String describeMethod(Classpath cp, String owner, String key) {
        for (String name : ancestry(cp, owner)) {
            if (name.startsWith("?")) {
                continue;
            }
            Integer flags = cp.get(name).methods().get(key);
            if (flags != null) {
                return "found " + flagText(flags);
            }
        }
        return "MISSING";
    }

    static String describeField(Classpath cp, String owner, String key) {
        for (String name : ancestry(cp, owner)) {
            if (name.startsWith("?")) {
                continue;
            }
            Integer flags = cp.get(name).fields().get(key);
            if (flags != null) {
                return "found " + flagText(flags);
            }
        }
        return "MISSING";
    }

    static String flagText(int flags) {
        List<String> parts = new ArrayList<>();
        if ((flags & 0x0001) != 0) parts.add("public");
        if ((flags & 0x0002) != 0) parts.add("private");
        if ((flags & 0x0004) != 0) parts.add("protected");
        if ((flags & 0x0008) != 0) parts.add("static");
        if ((flags & 0x0010) != 0) parts.add("final");
        if ((flags & 0x0400) != 0) parts.add("abstract");
        return "[" + String.join(" ", parts) + "]";
    }

    static String describeClass(Classpath cp, String name) {
        Info info = cp.get(name);
        if (info == null) {
            return "MISSING";
        }
        return info.isInterface() ? "interface" : (info.isAbstract() ? "abstract class" : "class");
    }

    static void typesOf(String descriptor, Set<String> out) {
        if (descriptor.startsWith("(")) {
            MethodTypeDesc type = MethodTypeDesc.ofDescriptor(descriptor);
            addType(type.returnType(), out);
            type.parameterList().forEach(p -> addType(p, out));
        } else {
            addType(ClassDesc.ofDescriptor(descriptor), out);
        }
    }

    static void addType(ClassDesc desc, Set<String> out) {
        while (desc.isArray()) {
            desc = desc.componentType();
        }
        if (desc.isClassOrInterface()) {
            String d = desc.descriptorString();
            out.add(d.substring(1, d.length() - 1));
        }
    }

    /** Everything the mod needs from outside, mapped to how it resolves on one classpath. */
    static Map<String, String> resolve(List<ClassModel> mod, Set<String> own, Classpath cp) {
        Map<String, String> out = new TreeMap<>();
        Set<String> types = new LinkedHashSet<>();
        for (ClassModel cm : mod) {
            String self = cm.thisClass().asInternalName();
            for (PoolEntry e : cm.constantPool()) {
                if (e instanceof MemberRefEntry ref) {
                    String owner = ref.owner().asInternalName();
                    String desc = ref.type().stringValue();
                    typesOf(desc, types);
                    // Inherited members are referenced through the mod's own class (javac writes the
                    // receiver's type), so own owners are resolved too, up through their ancestors
                    if (owner.startsWith("[")) {
                        continue;
                    }
                    String key = ref.name().stringValue() + (e instanceof FieldRefEntry ? ":" : "") + desc;
                    String kind = e instanceof FieldRefEntry ? "field" : "method";
                    String result = e instanceof FieldRefEntry ? describeField(cp, owner, key) : describeMethod(cp, owner, key);
                    if (e instanceof InterfaceMethodRefEntry || e instanceof MethodRefEntry) {
                        result += " (owner is " + describeClass(cp, owner) + ")";
                    }
                    out.put(kind + " " + owner + "." + key, result);
                } else if (e instanceof ClassEntry ce) {
                    String name = ce.asInternalName();
                    if (name.startsWith("[")) {
                        typesOf(name, types);
                    } else {
                        types.add(name);
                    }
                }
            }
            for (MethodModel m : cm.methods()) {
                typesOf(m.methodType().stringValue(), types);
            }
            for (FieldModel f : cm.fields()) {
                typesOf(f.fieldType().stringValue(), types);
            }
            checkInheritance(cm, self, own, cp, out);
        }
        for (String type : types) {
            if (!own.contains(type)) {
                out.put("type " + type, describeClass(cp, type));
            }
        }
        return out;
    }

    /** Overrides of outside methods, and abstract methods that nothing implements. */
    static void checkInheritance(ClassModel cm, String self, Set<String> own, Classpath cp, Map<String, String> out) {
        List<String> ancestors = ancestry(cp, self);
        List<String> outside = ancestors.stream().filter(a -> !own.contains(a.startsWith("?") ? a.substring(1) : a)).toList();
        for (String a : outside) {
            out.put("ancestor of " + self + ": " + (a.startsWith("?") ? a.substring(1) : a), a.startsWith("?") ? "MISSING" : "present");
        }
        for (MethodModel m : cm.methods()) {
            String name = m.methodName().stringValue();
            if (name.startsWith("<") || m.flags().has(AccessFlag.STATIC) || m.flags().has(AccessFlag.PRIVATE)) {
                continue;
            }
            String key = name + m.methodType().stringValue();
            // Where the overridden method is declared may move up or down the hierarchy; only
            // whether something is overridden (and whether it became final) matters
            String result = "overrides nothing outside";
            for (String a : outside) {
                if (!a.startsWith("?") && cp.get(a).methods().containsKey(key)) {
                    int flags = cp.get(a).methods().get(key);
                    if ((flags & (0x0002 | 0x0008)) == 0) {
                        result = (flags & 0x0010) != 0 ? "overrides a FINAL method" : "overrides an outside method";
                        break;
                    }
                }
            }
            out.put("override " + self + "." + key, result);
        }
        if (cm.flags().has(AccessFlag.ABSTRACT) || cm.flags().has(AccessFlag.INTERFACE)) {
            return;
        }
        Set<String> unimplemented = new HashSet<>();
        Set<String> implemented = new HashSet<>();
        for (String a : ancestors) {
            if (a.startsWith("?")) {
                continue;
            }
            for (Map.Entry<String, Integer> m : cp.get(a).methods().entrySet()) {
                boolean isAbstract = (m.getValue() & 0x0400) != 0;
                boolean isStatic = (m.getValue() & 0x0008) != 0;
                if (isStatic || m.getKey().startsWith("<")) {
                    continue;
                }
                if (isAbstract) {
                    unimplemented.add(m.getKey());
                } else {
                    implemented.add(m.getKey());
                }
            }
        }
        unimplemented.removeAll(implemented);
        out.put("abstract methods left in " + self, unimplemented.isEmpty() ? "none" : new java.util.TreeSet<>(unimplemented).toString());
    }

    static List<Path> readClasspath(Path file) throws IOException {
        return Files.readAllLines(file).stream().map(String::strip).filter(s -> !s.isEmpty()).map(Path::of).toList();
    }

    static Map<String, Path> jdkIndex() throws IOException {
        Map<String, Path> index = new HashMap<>();
        FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
        try (Stream<Path> modules = Files.list(jrt.getPath("/modules"))) {
            for (Path module : modules.toList()) {
                try (Stream<Path> walk = Files.walk(module)) {
                    walk.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                        String name = module.relativize(p).toString();
                        index.putIfAbsent(name.substring(0, name.length() - 6), p);
                    });
                }
            }
        }
        return index;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3 && args.length != 4) {
            System.err.println("Usage: java LinkCheck.java mod.jar reference-classpath.txt target-classpath.txt [allowed.txt]");
            System.exit(2);
        }
        List<ClassModel> mod = new ArrayList<>();
        Set<String> own = new HashSet<>();
        try (JarFile jar = new JarFile(args[0])) {
            for (var entry : jar.stream().toList()) {
                if (entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/")) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        ClassModel cm = ClassFile.of().parse(in.readAllBytes());
                        mod.add(cm);
                        own.add(cm.thisClass().asInternalName());
                    }
                }
            }
        }
        Map<String, Path> jdk = jdkIndex();
        // The mod's own classes come first on both sides, so its hierarchy can be walked
        List<Path> referenceEntries = new ArrayList<>(List.of(Path.of(args[0])));
        referenceEntries.addAll(readClasspath(Path.of(args[1])));
        List<Path> targetEntries = new ArrayList<>(List.of(Path.of(args[0])));
        targetEntries.addAll(readClasspath(Path.of(args[2])));
        Classpath reference = new Classpath(referenceEntries, jdk);
        Classpath target = new Classpath(targetEntries, jdk);

        Map<String, String> expected = resolve(mod, own, reference);
        Map<String, String> actual = resolve(mod, own, target);

        Set<String> allowed = new HashSet<>();
        if (args.length == 4) {
            for (String line : Files.readAllLines(Path.of(args[3]))) {
                String key = line.replaceFirst("#.*", "").strip();
                if (!key.isEmpty()) {
                    allowed.add(key);
                }
            }
        }

        int problems = 0;
        int accepted = 0;
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String got = actual.get(e.getKey());
            if (!e.getValue().equals(got)) {
                boolean ok = allowed.contains(e.getKey());
                if (ok) {
                    accepted++;
                } else {
                    problems++;
                }
                System.out.println((ok ? "ALLOWED " : "DIFF ") + e.getKey());
                System.out.println("     reference: " + e.getValue());
                System.out.println("     target:    " + got);
            }
        }
        long unresolvedInReference = expected.values().stream().filter(v -> v.startsWith("MISSING")).count();
        System.out.println();
        System.out.println(mod.size() + " classes, " + expected.size() + " links checked, "
                + unresolvedInReference + " unresolved in the reference too (reflection or optional mods), "
                + problems + " differences" + (accepted > 0 ? ", " + accepted + " reviewed and allowed" : "") + ".");
        System.exit(problems == 0 ? 0 : 1);
    }
}
