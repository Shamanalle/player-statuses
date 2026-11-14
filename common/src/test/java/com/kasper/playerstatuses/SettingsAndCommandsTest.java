package com.kasper.playerstatuses;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SettingsAndCommandsTest {

    @TempDir
    Path dir;

    @Test
    void writesDefaultsWithComments() throws IOException {
        Path file = dir.resolve("config/playerstatuses.properties");
        ClientSettings s = new ClientSettings();
        s.load(file);
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(text.contains("afk_minutes=3"));
        assertTrue(text.contains("# Minutes without input"));
        assertTrue(text.contains("send_inventory=true"));
        assertTrue(s.problems().isEmpty(), s.problems().toString());
    }

    @Test
    void keepsWhatThePersonTypedEvenWhenItIsWrong() throws IOException {
        Path file = dir.resolve("playerstatuses.properties");
        Files.write(file, "afk_minutes=500\nsend_typing=off\nmy_note=hello\n".getBytes(StandardCharsets.UTF_8));
        ClientSettings s = new ClientSettings();
        s.load(file);
        assertEquals(3, s.get(ClientSettings.AFK_MINUTES), "out of range: the default for now");
        assertFalse(s.sends(Category.TYPING));
        assertEquals(1, s.problems().size());
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(text.contains("afk_minutes=500"), "the typed value stays in the file");
        assertTrue(text.contains("my_note=hello"), "unknown keys stay");
        assertTrue(text.contains("show_typing=true"), "missing keys are added");
    }

    @Test
    void doesNotRewriteACompleteFile() throws IOException {
        Path file = dir.resolve("s.properties");
        ServerSettings s = new ServerSettings();
        s.load(file);
        String custom = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("afk_minutes=5", "afk_minutes = 7   ");
        Files.write(file, custom.getBytes(StandardCharsets.UTF_8));
        s.load(file);
        assertEquals(7, s.get(ServerSettings.AFK_MINUTES));
        assertEquals(custom, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test
    void saveKeepsUnknownKeysAndChangesValues() throws IOException {
        Path file = dir.resolve("c.properties");
        Files.write(file, "extra=1\n".getBytes(StandardCharsets.UTF_8));
        ClientSettings s = new ClientSettings();
        s.load(file);
        s.set(ClientSettings.OPACITY, 50);
        s.save();
        ClientSettings again = new ClientSettings();
        again.load(file);
        assertEquals(50, again.get(ClientSettings.OPACITY));
        assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("extra=1"));
    }

    @Test
    void resetDefaultsRestoresAllSettingsAndEscapesSpecialUnknownKeys() throws IOException {
        Path file = dir.resolve("reset.properties");
        Files.write(file, "custom\\ key\\:one=hello\n".getBytes(StandardCharsets.UTF_8));
        ClientSettings s = new ClientSettings();
        s.load(file);
        s.set(ClientSettings.OPACITY, 40);
        s.set(ClientSettings.SHOW_OWN, true);
        s.set(ClientSettings.send(Category.TYPING), false);
        s.save();

        s.resetDefaults();
        s.save();

        ClientSettings reloaded = new ClientSettings();
        reloaded.load(file);
        assertEquals(ClientSettings.OPACITY.def(), reloaded.get(ClientSettings.OPACITY));
        assertEquals(ClientSettings.SHOW_OWN.def(), reloaded.get(ClientSettings.SHOW_OWN));
        assertTrue(reloaded.sends(Category.TYPING));
        String raw = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(raw.contains("custom\\ key\\:one=hello"), raw);
    }

    @Test
    void serverTextHandlesMalformedInputsAndNonStringJsonValues() {
        assertTrue(ServerText.parse(null).isEmpty());
        assertTrue(ServerText.parse("not json at all").isEmpty());
        java.util.Map<String, String> parsed = ServerText.parse(
                "{\"count\": 42, \"enabled\": true, \"nested\": {\"a\": \"b\"}, \"arr\": [1, \"two\"], \"k\": \"val\"}"
        );
        assertEquals("val", parsed.get("k"));
        assertFalse(parsed.containsKey("a"));
    }

    private static StatusCommands.Source source(UUID player, String lang, boolean admin) {
        return new StatusCommands.Source() {
            @Override
            public UUID player() {
                return player;
            }

            @Override
            public String language() {
                return lang;
            }

            @Override
            public boolean isAdmin() {
                return admin;
            }
        };
    }

    @Test
    void statusCommandAnswersInThePlayersLanguage() {
        StatusServer server = new StatusServer();
        server.start(dir.resolve("server.properties"));
        UUID alex = new UUID(1, 2);
        server.relay().onClientMessage(alex, StatusMessages.update(PlayerStatus.of(Activity.INVENTORY)), 0);
        String en = StatusCommands.run("", source(alex, "en_us", false), server, 3, 0).plain();
        assertTrue(en.contains("Players with the mod: 1 / 3"), en);
        assertTrue(en.contains("Your status: Inventory"), en);
        String ru = StatusCommands.run("status", source(alex, "ru_ru", false), server, 3, 0).plain();
        assertTrue(ru.contains("Игроки с модом: 1 / 3"), ru);
        assertFalse(ru.contains("command.playerstatuses"), ru);
    }

    @Test
    void reloadNeedsAnOperator() {
        StatusServer server = new StatusServer();
        server.start(dir.resolve("server.properties"));
        CommandReply r = StatusCommands.run("reload", source(new UUID(0, 1), "", false), server, 1, 0);
        assertFalse(r.success());
        assertTrue(StatusCommands.run("reload", source(null, "", true), server, 1, 0).success());
        CommandReply unknown = StatusCommands.run("relaod", source(null, "", true), server, 1, 0);
        assertFalse(unknown.success());
        assertTrue(unknown.plain().contains("Unknown subcommand: relaod"));
    }

    @Test
    void afkCommandToggles() {
        StatusServer server = new StatusServer();
        server.start(dir.resolve("server.properties"));
        UUID sam = new UUID(5, 5);
        StatusCommands.Source s = source(sam, "en_us", false);
        assertTrue(StatusCommands.afk(s, server, 0).plain().startsWith("You are AFK now"));
        assertTrue(server.relay().current(sam, 120_000).has(StatusFlag.AFK));
        assertEquals("AFK 2m", StatusCommands.describe("en_us", server.relay().current(sam, 120_000)));
        assertEquals("Welcome back.", StatusCommands.afk(s, server, 1000).plain());
        assertFalse(StatusCommands.afk(source(null, "", true), server, 0).success());
        server.settings().set(ServerSettings.AFK_COMMAND, false);
        assertFalse(StatusCommands.afk(s, server, 0).success());
    }

    @Test
    void settingsLayoutFitsWithoutOverlapAcrossGuiScales() {
        ClientSettings s = new ClientSettings();
        for (int i = 0; i < SettingsLayout.PREVIEW_COUNT * 2; i++) {
            PlayerStatus p = SettingsLayout.previewStatus(i, s);
            assertFalse(p.isNone(), "Preview " + i + " must not be NONE");
        }

        SettingsLayout.setAllCategories(s, true, false);
        for (Category c : Category.values()) {
            assertFalse(s.sends(c));
            assertTrue(s.shows(c));
        }
        assertFalse(s.get(ClientSettings.SEND_PROJECTIONS));
        assertFalse(s.get(ClientSettings.SEND_PROJECTION_SLOTS));
        SettingsLayout.setAllCategories(s, false, false);
        for (Category c : Category.values()) {
            assertFalse(s.shows(c));
        }
        SettingsLayout.setAllCategories(s, true, true);
        SettingsLayout.setAllCategories(s, false, true);
        assertTrue(s.get(ClientSettings.SEND_PROJECTIONS));
        assertTrue(s.get(ClientSettings.SEND_PROJECTION_SLOTS));

        int[][] resolutions = {{276, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}};
        for (int[] res : resolutions) {
            SettingsLayout.Metrics m = new SettingsLayout.Metrics(res[0], res[1]);
            assertTrue(m.previewY > m.titleY + 7, "Title overlaps preview at " + res[0] + "x" + res[1]);
            assertTrue(m.tabsY >= m.previewY + m.previewH + 2, "Preview overlaps tabs at " + res[0] + "x" + res[1]);
            int generalBottom = m.generalStartY + 5 * m.rowStep + m.btnH;
            assertTrue(generalBottom + 2 <= m.footerY, "General rows overlap footer at " + res[0] + "x" + res[1]);
            int categoryRows = (Category.values().length + 1) / 2;
            int gridBottom = m.gridStartY + categoryRows * m.rowStep + m.btnH;
            assertTrue(m.gridStartY >= m.bulkY + m.subBtnH + 2, "Bulk buttons overlap grid at " + res[0] + "x" + res[1]);
            assertTrue(gridBottom + 2 <= m.footerY, "Category grid + privacy row overlap footer at " + res[0] + "x" + res[1]);

            java.util.List<int[]> rects = new java.util.ArrayList<>();
            int[] origin = new int[6];
            StatusIcon.CardLayout card = SettingsLayout.drawHeaderChrome(
                    (x0, y0, x1, y1, argb) -> rects.add(new int[]{x0, y0, x1, y1, argb}),
                    m, SettingsLayout.TAB_SEND, true,
                    SettingsLayout.previewStatus(0, s), 34, 90, 56, 1234L, origin);
            assertNotNull(card);
            assertFalse(rects.isEmpty());
            assertTrue(origin[0] > m.panelX);
            assertTrue(origin[4] > m.panelX + m.previewCardW / 2);
        }
    }
}
