package com.kasper.playerstatuses;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * A settings file in {@code key=value} form with a comment above each key. Values a person typed are
 * never thrown away: a value that cannot be read keeps the default for this run and is reported, but
 * stays in the file. The file is written only when it is missing, when keys are missing (they are
 * added with their defaults), or when the game changes a value itself.
 */
public class SettingsFile {

    /** One setting: its key, default, how a value is read, and the comment written above it. */
    public abstract static class Key<T> {
        private final String id;
        private final T def;
        private final String comment;

        Key(String id, T def, String comment) {
            this.id = id;
            this.def = def;
            this.comment = comment;
        }

        public String id() {
            return id;
        }

        public T def() {
            return def;
        }

        public String comment() {
            return comment;
        }

        /** The value, or {@code null} if the text is not a valid one. */
        abstract T parse(String text);

        String format(T value) {
            return String.valueOf(value);
        }
    }

    public static Key<Boolean> bool(String id, boolean def, String comment) {
        return new Key<Boolean>(id, def, comment) {
            @Override
            Boolean parse(String text) {
                String t = text.trim().toLowerCase(Locale.ROOT);
                if (t.equals("true") || t.equals("on") || t.equals("yes")) {
                    return true;
                }
                if (t.equals("false") || t.equals("off") || t.equals("no")) {
                    return false;
                }
                return null;
            }
        };
    }

    public static Key<Integer> integer(String id, int def, int min, int max, String comment) {
        return new Key<Integer>(id, def, comment + " (" + min + " - " + max + ")") {
            @Override
            Integer parse(String text) {
                try {
                    int v = Integer.parseInt(text.trim());
                    return v < min || v > max ? null : v;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        };
    }

    public static Key<String> choice(String id, String def, List<String> allowed, String comment) {
        return new Key<String>(id, def, comment + " (" + String.join(", ", allowed) + ")") {
            @Override
            String parse(String text) {
                String t = text.trim().toLowerCase(Locale.ROOT);
                return allowed.contains(t) ? t : null;
            }
        };
    }

    public static Key<String> text(String id, String def, String comment) {
        return new Key<String>(id, def, comment) {
            @Override
            String parse(String text) {
                return text.trim();
            }
        };
    }

    /** A comma-separated list; spaces around the items are dropped, empty items too. */
    public static Key<List<String>> list(String id, List<String> def, String comment) {
        return new Key<List<String>>(id, def, comment) {
            @Override
            List<String> parse(String text) {
                return splitList(text);
            }

            @Override
            String format(List<String> value) {
                return String.join(",", value);
            }
        };
    }

    static List<String> splitList(String text) {
        List<String> out = new ArrayList<>();
        for (String item : text.split(",")) {
            String t = item.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private final String header;
    private final List<Key<?>> keys;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();
    private Path path;
    private long loadedModified = Long.MIN_VALUE;

    protected SettingsFile(String header, List<Key<?>> keys) {
        this.header = header;
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
        for (Key<?> k : keys) {
            values.put(k.id(), k.def());
        }
    }

    public List<Key<?>> keys() {
        return keys;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        Object v = values.get(key.id());
        return v == null ? key.def() : (T) v;
    }

    public synchronized <T> void set(Key<T> key, T value) {
        values.put(key.id(), value);
    }

    /** Lines about values that could not be read in the last load, for the log. */
    public List<String> problems() {
        return Collections.unmodifiableList(problems);
    }

    public Path path() {
        return path;
    }

    /** Reads {@code file}, writing it (or its missing keys) first if needed. */
    public synchronized void load(Path file) {
        this.path = file;
        Properties props = new Properties();
        boolean exists = Files.isRegularFile(file);
        if (exists) {
            try (Reader r = new StringReader(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))) {
                props.load(r);
            } catch (IOException e) {
                problems.clear();
                problems.add("Could not read " + file + ": " + e);
                return;
            }
        }
        read(props);
        boolean missing = false;
        for (Key<?> k : keys) {
            missing |= !props.containsKey(k.id());
        }
        if (!exists || missing) {
            save(props);
        }
        loadedModified = modified(file);
    }

    /** Reads the file again if it was changed since it was read. */
    public synchronized boolean reloadIfChanged() {
        if (path == null || modified(path) == loadedModified) {
            return false;
        }
        load(path);
        return true;
    }

    /** Writes every value; unknown keys a person added stay in the file. */
    public synchronized void save() {
        if (path == null) {
            return;
        }
        Properties props = new Properties();
        if (Files.isRegularFile(path)) {
            try (Reader r = new StringReader(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))) {
                props.load(r);
            } catch (IOException ignored) {
                // Written anew below
            }
        }
        for (Key<?> k : keys) {
            props.setProperty(k.id(), formatValue(k));
        }
        save(props);
        loadedModified = modified(path);
    }

    void read(Properties props) {
        problems.clear();
        for (Key<?> k : keys) {
            String text = props.getProperty(k.id());
            if (text == null) {
                values.put(k.id(), k.def());
                continue;
            }
            Object v = k.parse(text);
            if (v == null) {
                problems.add(k.id() + "=" + text + " is not valid, using " + formatDefault(k));
                values.put(k.id(), k.def());
            } else {
                values.put(k.id(), v);
            }
        }
    }

    /** The file's text: the header, then each key with its comment; keys the file had but we do not know at the end. */
    String render(Properties existing) {
        StringBuilder b = new StringBuilder();
        for (String line : header.split("\n")) {
            b.append("# ").append(line).append('\n');
        }
        for (Key<?> k : keys) {
            b.append('\n');
            for (String line : k.comment().split("\n")) {
                b.append("# ").append(line).append('\n');
            }
            String raw = existing.getProperty(k.id());
            b.append(k.id()).append('=').append(escape(raw != null ? raw : formatDefault(k))).append('\n');
        }
        List<String> others = new ArrayList<>();
        for (String name : existing.stringPropertyNames()) {
            boolean known = false;
            for (Key<?> k : keys) {
                known |= k.id().equals(name);
            }
            if (!known) {
                others.add(name);
            }
        }
        Collections.sort(others);
        if (!others.isEmpty()) {
            b.append('\n');
            for (String name : others) {
                b.append(name).append('=').append(escape(existing.getProperty(name))).append('\n');
            }
        }
        return b.toString();
    }

    private void save(Properties existing) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, render(existing).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            problems.add("Could not write " + path + ": " + e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> String formatValue(Key<T> k) {
        return k.format((T) values.get(k.id()));
    }

    private static <T> String formatDefault(Key<T> k) {
        return k.format(k.def());
    }

    /** Escapes what Properties would misread; everything else (any language) is written as it is. */
    static String escape(String value) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (i == 0 && c == ' ') {
                        b.append("\\ ");
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.toString();
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }
}
