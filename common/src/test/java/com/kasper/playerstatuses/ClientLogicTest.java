package com.kasper.playerstatuses;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Idle detection, the send limiter, privacy filters and texts of the client. */
class ClientLogicTest {

    @Test
    void idleTrackerCountsTurnsAndInputs() {
        IdleTracker t = new IdleTracker(0);
        assertFalse(t.look(1000, 10, 5));
        assertEquals(1000, t.idleMillis(1000));
        assertTrue(t.look(2000, 11, 5));
        assertEquals(0, t.idleMillis(2000));
        assertFalse(t.look(200_000, 11, 5));
        assertTrue(t.isAfk(200_000, 3));
        assertFalse(t.isAfk(200_000, 0));
        t.input(200_001);
        assertFalse(t.isAfk(200_001, 3));
    }

    @Test
    void limiterSendsChangesOnlyAndNotTooOften() {
        SendLimiter l = new SendLimiter(250);
        assertEquals(PlayerStatus.NONE, l.poll(PlayerStatus.NONE, 0), "the first poll always sends");
        assertNull(l.poll(PlayerStatus.NONE, 1000));
        assertEquals(PlayerStatus.of(Activity.INVENTORY), l.poll(PlayerStatus.of(Activity.INVENTORY), 1000));
        assertNull(l.poll(PlayerStatus.NONE, 1100), "too soon");
        assertEquals(PlayerStatus.NONE, l.poll(PlayerStatus.NONE, 1250));
        // Idle seconds alone are no reason to send
        PlayerStatus afk = new PlayerStatus(Activity.NONE, StatusFlag.AFK.bit(), 180);
        assertEquals(afk, l.poll(afk, 2000));
        assertNull(l.poll(afk.withIdleSeconds(181), 3000));
        l.reset();
        assertNotNull(l.poll(afk, 3001));
    }

    @Test
    void privacyTakesCategoriesOut() {
        ClientSettings s = new ClientSettings();
        s.set(ClientSettings.send(Category.INVENTORY), false);
        s.set(ClientSettings.send(Category.VOICE), false);
        PlayerStatus inInventory = PlayerStatus.of(Activity.INVENTORY, StatusFlag.AFK, StatusFlag.MIC_OFF);
        assertEquals(PlayerStatus.of(Activity.NONE, StatusFlag.AFK), s.outgoing(inInventory));
        assertEquals(PlayerStatus.of(Activity.CONTAINER), s.outgoing(PlayerStatus.of(Activity.CONTAINER)));
        s.set(ClientSettings.show(Category.TYPING), false);
        assertTrue(s.incoming(PlayerStatus.of(Activity.COMMAND)).isNone());
    }

    @Test
    void idleText() {
        assertEquals("", IdleText.duration(59));
        assertEquals("12m", IdleText.duration(12 * 60 + 30));
        assertEquals("1h 05m", IdleText.duration(3900));
        assertEquals("2d 3h", IdleText.duration((48 + 3) * 3600 + 59));
    }

    @Test
    void modScreens() {
        assertEquals(Activity.NONE, ScreenRules.modScreen("io.github.kosmx.emotes.main.screen.EmoteMenu", ScreenRules.DEFAULT_IGNORED));
        assertEquals(Activity.MAP, ScreenRules.modScreen("xaero.map.gui.GuiMap", ScreenRules.DEFAULT_IGNORED));
        assertEquals(Activity.BUSY, ScreenRules.modScreen("mezz.jei.gui.recipes.RecipesGui", ScreenRules.DEFAULT_IGNORED));
        // A prefix is a package or a class, not the start of any name
        assertFalse(ScreenRules.matches("journeymapper.Screen", Arrays.asList("journeymap")));
        assertTrue(ScreenRules.matches("com.example.Menu$Inner", Arrays.asList("com.example.Menu")));
        assertTrue(ScreenRules.matches("com.example.ui.Menu", Arrays.asList("com.example.")));
    }
}
