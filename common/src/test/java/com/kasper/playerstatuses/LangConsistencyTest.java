package com.kasper.playerstatuses;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against untranslated keys showing up in game: every key the code uses must exist in every
 * language file, and all language files must have the same keys and placeholders.
 */
class LangConsistencyTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path LANG = ROOT.resolve("common/src/main/resources/assets/playerstatuses/lang");

    private static Map<String, String> english;
    private static final Map<String, Map<String, String>> others = new TreeMap<>();
    private static String sources;

    @BeforeAll
    static void load() throws IOException {
        english = readLang(LANG.resolve("en_us.json"));
        try (Stream<Path> files = Files.list(LANG)) {
            for (Path f : files.filter(f -> f.toString().endsWith(".json") && !f.endsWith("en_us.json")).toList()) {
                others.put(f.getFileName().toString(), readLang(f));
            }
        }
        StringBuilder all = new StringBuilder();
        for (String dir : new String[]{"common/src/main", "shared", "versions", "bukkit/src"}) {
            Path p = ROOT.resolve(dir);
            if (!Files.isDirectory(p)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(p)) {
                for (Path f : files.filter(f -> f.toString().endsWith(".java") && !generated(p.relativize(f))).toList()) {
                    all.append(Files.readString(f, StandardCharsets.UTF_8)).append('\n');
                }
            }
        }
        sources = all.toString();
    }

    private static boolean generated(Path relative) {
        for (Path part : relative) {
            if (part.toString().equals("build") || part.toString().equals(".gradle")) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("Every language the server knows has a file, and the required ones are there")
    void languages() {
        for (String l : ServerText.LANGUAGES) {
            assertTrue(l.equals("en_us") || others.containsKey(l + ".json"), "No file for " + l);
        }
        for (String l : new String[]{"ru_ru", "uk_ua", "de_de", "es_es", "pt_br", "zh_cn"}) {
            assertTrue(others.containsKey(l + ".json"), l + " is required");
        }
        assertEquals(ServerText.LANGUAGES.length, others.size() + 1, "A language file is missing from ServerText.LANGUAGES");
    }

    @Test
    @DisplayName("All language files contain the same keys")
    void sameKeys() {
        for (Map.Entry<String, Map<String, String>> lang : others.entrySet()) {
            assertEquals(new TreeSet<>(english.keySet()), new TreeSet<>(lang.getValue().keySet()), lang.getKey());
        }
    }

    @Test
    @DisplayName("Placeholders match between languages")
    void samePlaceholders() {
        for (Map.Entry<String, Map<String, String>> lang : others.entrySet()) {
            for (String key : english.keySet()) {
                assertEquals(placeholders(english.get(key)), placeholders(lang.getValue().get(key)),
                        "Placeholders differ for " + key + " in " + lang.getKey());
            }
        }
    }

    @Test
    @DisplayName("No text is empty or left in English by mistake where it must not be")
    void noEmptyTexts() {
        for (Map.Entry<String, Map<String, String>> lang : others.entrySet()) {
            for (Map.Entry<String, String> e : lang.getValue().entrySet()) {
                assertFalse(e.getValue().trim().isEmpty(), e.getKey() + " is empty in " + lang.getKey());
            }
        }
    }

    @Test
    @DisplayName("Every key used by the code is translated")
    void usedKeysExist() {
        Set<String> used = new TreeSet<>();
        Matcher full = Pattern.compile("\"((?:gui|key|command)\\.playerstatuses\\.[\\w.\\-]*)\"").matcher(sources);
        while (full.find()) {
            if (!full.group(1).endsWith(".")) {
                used.add(full.group(1));
            }
        }
        // Command texts are looked up without their prefix: t(lang, "afk.on")
        Matcher relative = Pattern.compile("\\bt\\(lang, \"([\\w.]+)\"").matcher(sources);
        while (relative.find()) {
            used.add(ServerText.PREFIX + relative.group(1));
        }
        for (String k : new String[]{"on", "off"}) {
            used.add(ServerText.PREFIX + k);
        }
        for (Category c : Category.values()) {
            used.add(c.translationKey());
        }
        for (Activity a : Activity.values()) {
            used.add(a.translationKey());
        }
        for (StatusFlag f : StatusFlag.values()) {
            used.add(f.translationKey());
        }
        List<String> missing = new ArrayList<>();
        for (String k : used) {
            if (!english.containsKey(k)) {
                missing.add(k);
            }
        }
        assertTrue(missing.isEmpty(), "Missing translations: " + missing);
    }

    @Test
    @DisplayName("The server reads the files the same way the game does")
    void serverParserAgrees() throws IOException {
        for (String l : ServerText.LANGUAGES) {
            Map<String, String> parsed = ServerText.parse(Files.readString(LANG.resolve(l + ".json"), StandardCharsets.UTF_8));
            assertEquals(readLang(LANG.resolve(l + ".json")).keySet(), parsed.keySet(), l);
        }
        assertEquals(Arrays.asList("en_us", "ru_ru"), Arrays.asList(ServerText.language("EN"), ServerText.language("ru-RU")));
    }

    private static int placeholders(String s) {
        Matcher m = Pattern.compile("%(?:\\d+\\$)?s").matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Minimal reader for flat {"key": "value"} JSON files. */
    private static Map<String, String> readLang(Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, String> map = new TreeMap<>();
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        while (m.find()) {
            assertNull(map.put(m.group(1), m.group(2)), "Duplicate key " + m.group(1) + " in " + file.getFileName());
        }
        assertFalse(map.isEmpty(), "No entries in " + file);
        return map;
    }
}
