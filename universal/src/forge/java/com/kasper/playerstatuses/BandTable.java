package com.kasper.playerstatuses;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Picks the band of the running Minecraft in the universal Forge / NeoForge jar and creates its
 * {@code @Mod} class. The table (playerstatuses-bands.properties) is written by the build from each band's own
 * mods.toml / neoforge.mods.toml; mods.toml of the jar itself can only state what every band needs,
 * so the rest (the loader and Simple Voice Chat versions of the band) is checked here.
 */
final class BandTable {

    private BandTable() {
    }

    /**
     * @param loader   "forge" or "neoforge": which bands of the table to consider
     * @param fml      the loader's package, "net.minecraftforge.fml" or "net.neoforged.fml"
     * @param provided what the loader passed to the dispatcher, offered to the band's constructor
     */
    static Object create(String loader, String fml, Object... provided) {
        Properties table = load();
        String minecraft = version(fml, "minecraft");
        if (minecraft == null) {
            throw new IllegalStateException("Player Statuses: Minecraft's version is unknown");
        }
        for (String id : table.getProperty(loader + ".bands", "").split(",")) {
            if (id.isEmpty() || !matches(table.getProperty(id + ".minecraft"), minecraft)) {
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (String dep : table.getProperty(id + ".depends", "").split(",")) {
                if (dep.isEmpty()) {
                    continue;
                }
                String wanted = table.getProperty(id + ".depends." + dep);
                String have = version(fml, dep);
                if (have == null || !matches(wanted, have)) {
                    missing.add(dep + " " + wanted + " (found " + (have == null ? "none" : have) + ")");
                }
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Player Statuses on Minecraft " + minecraft + " needs " + String.join(", ", missing));
            }
            return construct(table.getProperty(id + ".mod"), provided);
        }
        throw new IllegalStateException("Player Statuses does not support Minecraft " + minecraft + " on " + loader);
    }

    private static Object construct(String name, Object[] provided) {
        if (name == null) {
            throw new IllegalStateException("Player Statuses: the band has no entry class");
        }
        try {
            Class<?> cls = Class.forName(name, true, BandTable.class.getClassLoader());
            for (Constructor<?> ctor : cls.getConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                Object[] args = new Object[types.length];
                boolean ok = true;
                for (int i = 0; i < types.length && ok; i++) {
                    args[i] = find(types[i], provided);
                    ok = args[i] != null;
                }
                if (ok) {
                    return ctor.newInstance(args);
                }
            }
            throw new IllegalStateException("Player Statuses: no constructor of " + name + " fits what the loader provides");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Player Statuses: " + name + " failed to start", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Player Statuses: cannot create " + name, e);
        }
    }

    private static Object find(Class<?> type, Object[] provided) {
        for (Object value : provided) {
            if (type.isInstance(value)) {
                return value;
            }
        }
        return null;
    }

    private static Properties load() {
        Properties table = new Properties();
        try (InputStream in = BandTable.class.getResourceAsStream("/playerstatuses-bands.properties")) {
            if (in == null) {
                throw new IllegalStateException("Player Statuses: playerstatuses-bands.properties is missing from the jar");
            }
            table.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Player Statuses: cannot read playerstatuses-bands.properties", e);
        }
        return table;
    }

    /** The version of a loaded mod ("minecraft", "forge", "voicechat"), or null. */
    static String version(String fml, String modId) {
        try {
            Class<?> listClass = Class.forName(fml + ".ModList");
            Method byId = listClass.getMethod("getModContainerById", String.class);
            // ModList.get().getModContainerById(..) before Forge 26, a static ModList.getModContainerById(..) since
            Object list = Modifier.isStatic(byId.getModifiers()) ? null : listClass.getMethod("get").invoke(null);
            Optional<?> container = (Optional<?>) byId.invoke(list, modId);
            if (!container.isPresent()) {
                return null;
            }
            // Through the declared API types: the loader's implementation classes may not be open to mods
            Method getModInfo = Class.forName(fml + ".ModContainer").getMethod("getModInfo");
            Object info = getModInfo.invoke(container.get());
            return String.valueOf(getModInfo.getReturnType().getMethod("getVersion").invoke(info));
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("Player Statuses: cannot read the version of " + modId, e);
        }
    }

    /** A Maven version range as mods.toml writes it: "[1.21,1.21.2)", "[61,)", several joined by commas. */
    static boolean matches(String spec, String version) {
        if (spec == null || version == null) {
            return false;
        }
        spec = spec.trim();
        if (spec.isEmpty() || spec.equals("*")) {
            return true;
        }
        int i = 0;
        while (i < spec.length()) {
            char open = spec.charAt(i);
            if (open != '[' && open != '(') {
                int comma = spec.indexOf(',', i);
                String bare = (comma < 0 ? spec.substring(i) : spec.substring(i, comma)).trim();
                if (!bare.isEmpty() && compareForBound(version, bare, true) >= 0) {
                    return true;
                }
                if (comma < 0) {
                    return false;
                }
                i = comma + 1;
                while (i < spec.length() && (spec.charAt(i) == ',' || spec.charAt(i) == ' ')) {
                    i++;
                }
                continue;
            }
            int close = indexOfAny(spec, i + 1);
            if (close < 0) {
                return compareForBound(version, spec.substring(i), true) >= 0;
            }
            String[] bounds = spec.substring(i + 1, close).split(",", -1);
            boolean inside;
            if (bounds.length == 1) {
                inside = compare(version, bounds[0].trim()) == 0;
            } else {
                String low = bounds[0].trim();
                String high = bounds[1].trim();
                boolean closeInc = spec.charAt(close) == ']';
                inside = (low.isEmpty() || (open == '[' ? compareForBound(version, low, true) >= 0 : compare(version, low) > 0))
                        && (high.isEmpty() || (closeInc ? compare(version, high) <= 0 : compareForBound(version, high, false) < 0));
            }
            if (inside) {
                return true;
            }
            i = close + 1;
            while (i < spec.length() && (spec.charAt(i) == ',' || spec.charAt(i) == ' ')) {
                i++;
            }
        }
        return false;
    }

    /**
     * When a range bound has no pre-release qualifier (e.g. "[21.11,)" or "[1.21,1.21.2)"), pre-releases of
     * the lower bound ("21.11.0-beta") belong inside the band, whereas pre-releases of an exclusive upper
     * bound ("1.21.2-rc1") belong to the next band rather than the previous one.
     */
    private static int compareForBound(String version, String bound, boolean inclusiveLowerOrExcludeUpper) {
        if (bound.indexOf('-') < 0) {
            String vClean = stripBuild(version);
            int dashV = vClean.indexOf('-');
            if (dashV >= 0 && !startsWithDigit(vClean.substring(dashV + 1))) {
                return compareParts(vClean.substring(0, dashV), stripBuild(bound));
            }
        }
        return compare(version, bound);
    }

    private static String stripBuild(String s) {
        s = s.trim();
        int plus = s.indexOf('+');
        return plus < 0 ? s : s.substring(0, plus);
    }

    private static int indexOfAny(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == ']' || s.charAt(i) == ')') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Compares versions the way Maven does for the versions the loaders report: the part before the first
     * '-' first, number by number with missing numbers as 0 ("1.21" = "1.21.0" &lt; "1.21.1"), then the part
     * after it ("1.21-2.6.24" &gt; "1.21-2.5.0"). No part after '-' is less than a number there ("1.21" &lt;
     * "1.21-2.5.0") but more than a word ("21.11.0" &gt; "21.11.0-beta"). Build data after '+' is ignored.
     */
    static int compare(String a, String b) {
        a = a.trim();
        b = b.trim();
        int plusA = a.indexOf('+');
        int plusB = b.indexOf('+');
        a = plusA < 0 ? a : a.substring(0, plusA);
        b = plusB < 0 ? b : b.substring(0, plusB);
        int dashA = a.indexOf('-');
        int dashB = b.indexOf('-');
        int c = compareParts(dashA < 0 ? a : a.substring(0, dashA), dashB < 0 ? b : b.substring(0, dashB));
        if (c != 0) {
            return c;
        }
        String restA = dashA < 0 ? null : a.substring(dashA + 1);
        String restB = dashB < 0 ? null : b.substring(dashB + 1);
        if (restA == null || restB == null) {
            if (restA == restB) {
                return 0;
            }
            String rest = restA == null ? restB : restA;
            int sign = startsWithDigit(rest) ? -1 : 1;
            return restA == null ? sign : -sign;
        }
        return compareParts(restA, restB);
    }

    /** Dot-, dash-, or letter/digit-separated parts: numbers as numbers, missing number is 0, number beats word. */
    private static int compareParts(String a, String b) {
        List<String> x = tokens(a);
        List<String> y = tokens(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            String p = i < x.size() ? x.get(i) : "0";
            String q = i < y.size() ? y.get(i) : "0";
            int c;
            if (isNumber(p) && isNumber(q)) {
                c = Long.compare(Long.parseLong(p), Long.parseLong(q));
            } else if (isNumber(p) != isNumber(q)) {
                c = isNumber(p) ? 1 : -1;
            } else {
                int rp = qualifierRank(p);
                int rq = qualifierRank(q);
                c = (rp != rq) ? Integer.compare(rp, rq) : p.compareToIgnoreCase(q);
            }
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        int start = -1;
        boolean prevDigit = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '.' || ch == '-' || ch == '_') {
                if (start >= 0) {
                    out.add(s.substring(start, i));
                    start = -1;
                }
                continue;
            }
            boolean digit = Character.isDigit(ch);
            if (start < 0) {
                start = i;
                prevDigit = digit;
            } else if (digit != prevDigit) {
                out.add(s.substring(start, i));
                start = i;
                prevDigit = digit;
            }
        }
        if (start >= 0) {
            out.add(s.substring(start));
        }
        return out;
    }

    private static int qualifierRank(String q) {
        String s = q.toLowerCase(java.util.Locale.ROOT);
        if (s.equals("snapshot")) return 0;
        if (s.equals("alpha") || s.equals("a")) return 1;
        if (s.equals("beta") || s.equals("b")) return 2;
        if (s.equals("milestone") || s.equals("m")) return 3;
        if (s.equals("pre")) return 4;
        if (s.equals("rc") || s.equals("cr")) return 5;
        return 6;
    }

    private static boolean startsWithDigit(String s) {
        return !s.isEmpty() && Character.isDigit(s.charAt(0));
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty() || s.length() > 18) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
