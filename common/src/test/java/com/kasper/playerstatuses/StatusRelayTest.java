package com.kasper.playerstatuses;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StatusRelayTest {

    private final UUID alex = new UUID(1, 1);
    private final UUID sam = new UUID(2, 2);

    private static byte[] msg(PlayerStatus s) {
        return StatusMessages.update(s);
    }

    @Test
    void clientReportIsRelayedOnce() {
        StatusRelay relay = new StatusRelay();
        assertTrue(relay.onClientMessage(alex, msg(PlayerStatus.of(Activity.TYPING)), 1000));
        assertTrue(relay.hasMod(alex));
        assertEquals(Collections.singletonList(alex), relay.takeChanged());
        assertEquals(PlayerStatus.of(Activity.TYPING), relay.current(alex, 1000));
        // The same status again changes nothing
        relay.onClientMessage(alex, msg(PlayerStatus.of(Activity.TYPING)), 2000);
        assertTrue(relay.takeChanged().isEmpty());
    }

    @Test
    void garbageIsRejected() {
        StatusRelay relay = new StatusRelay();
        assertFalse(relay.onClientMessage(alex, new byte[]{9, 9, 9}, 0));
        assertFalse(relay.hasMod(alex));
    }

    @Test
    void afkTimeCountsUpOnTheServer() {
        StatusRelay relay = new StatusRelay();
        relay.onClientMessage(alex, msg(new PlayerStatus(Activity.NONE, StatusFlag.AFK.bit(), 180)), 10_000);
        assertEquals(180, relay.current(alex, 10_000).idleSeconds());
        assertEquals(240, relay.current(alex, 70_000).idleSeconds());
        // Still AFK in a later report: the start stays where it was
        relay.onClientMessage(alex, msg(new PlayerStatus(Activity.PAUSE, StatusFlag.AFK.bit(), 999)), 80_000);
        assertEquals(250, relay.current(alex, 80_000).idleSeconds());
    }

    @Test
    void floodIsLimitedButTheLastReportArrives() {
        StatusRelay relay = new StatusRelay();
        long now = 0;
        for (int i = 0; i < 100; i++) {
            Activity a = i % 2 == 0 ? Activity.INVENTORY : Activity.NONE;
            relay.onClientMessage(alex, msg(PlayerStatus.of(a)), now);
        }
        // The burst was used up; the rest waits
        relay.onClientMessage(alex, msg(PlayerStatus.of(Activity.FURNACE)), now);
        assertNotEquals(Activity.FURNACE, relay.current(alex, now).activity());
        relay.tick(now + 1000);
        assertEquals(Activity.FURNACE, relay.current(alex, now + 1000).activity());
    }

    @Test
    void serverDetectionOnlyForPlayersWithoutTheMod() {
        StatusRelay relay = new StatusRelay();
        relay.onServerDetected(sam, Activity.CONTAINER, false, 0, 1000);
        assertEquals(Activity.CONTAINER, relay.current(sam, 1000).activity());
        assertFalse(relay.hasMod(sam));

        relay.onClientMessage(alex, msg(PlayerStatus.NONE), 1000);
        relay.onServerDetected(alex, Activity.CONTAINER, true, 0, 1000);
        assertEquals(PlayerStatus.NONE, relay.current(alex, 1000));
    }

    @Test
    void manualAfkEndsWhenThePlayerActs() {
        StatusRelay relay = new StatusRelay();
        relay.onClientMessage(alex, msg(PlayerStatus.of(Activity.NONE)), 0);
        relay.takeChanged();
        relay.setManualAfk(alex, true, 5000);
        assertTrue(relay.current(alex, 65_000).has(StatusFlag.AFK));
        assertEquals(60, relay.current(alex, 65_000).idleSeconds());
        assertEquals(Collections.singletonList(alex), relay.takeChanged());
        relay.onActed(alex, 70_000);
        assertFalse(relay.current(alex, 70_000).has(StatusFlag.AFK));
        assertEquals(Collections.singletonList(alex), relay.takeChanged());
    }

    @Test
    void leavingForgetsThePlayer() {
        StatusRelay relay = new StatusRelay();
        relay.onClientMessage(alex, msg(PlayerStatus.of(Activity.TYPING)), 0);
        relay.remove(alex);
        assertEquals(PlayerStatus.NONE, relay.current(alex, 0));
        assertTrue(relay.takeChanged().isEmpty());
        assertEquals(0, relay.moddedCount());
    }

    @Test
    void serverDetectorTracksIdleTimeAndOpenContainers() {
        StatusRelay relay = new StatusRelay();
        ServerSettings settings = new ServerSettings();
        ServerDetector detector = new ServerDetector();

        detector.onPose(sam, 0.0, 64.0, 0.0, 0.0f, 0.0f, relay, 1000L);
        detector.updatePlayer(sam, settings, relay, 1000L);
        assertEquals(PlayerStatus.NONE, relay.current(sam, 1000L));

        // Open a furnace on the server
        detector.setOpenContainer(sam, ServerDetector.fromBukkitInventoryType("FURNACE"), relay, 2000L);
        detector.updatePlayer(sam, settings, relay, 2000L);
        assertEquals(Activity.FURNACE, relay.current(sam, 2000L).activity());
        assertFalse(relay.current(sam, 2000L).has(StatusFlag.AFK));

        // Wait 5 minutes without acting -> becomes AFK while still in the furnace
        long afterFiveMin = 2000L + 5 * 60_000L;
        detector.updatePlayer(sam, settings, relay, afterFiveMin);
        PlayerStatus s = relay.current(sam, afterFiveMin);
        assertEquals(Activity.FURNACE, s.activity());
        assertTrue(s.has(StatusFlag.AFK));
        assertEquals(300, s.idleSeconds());

        // Move slightly -> clears AFK
        detector.onPose(sam, 1.0, 64.0, 0.0, 0.0f, 0.0f, relay, afterFiveMin + 1000L);
        detector.updatePlayer(sam, settings, relay, afterFiveMin + 1000L);
        assertFalse(relay.current(sam, afterFiveMin + 1000L).has(StatusFlag.AFK));

        // Close container -> back to NONE
        detector.setOpenContainer(sam, ServerDetector.fromBukkitInventoryType("CRAFTING"), relay, afterFiveMin + 2000L);
        detector.updatePlayer(sam, settings, relay, afterFiveMin + 2000L);
        assertEquals(PlayerStatus.NONE, relay.current(sam, afterFiveMin + 2000L));
    }

    @Test
    void bukkitInventoryTypesAndAfkCommandDetection() {
        assertEquals(Activity.NONE, ServerDetector.fromBukkitInventoryType("PLAYER"));
        assertEquals(Activity.NONE, ServerDetector.fromBukkitInventoryType("CRAFTING"));
        assertEquals(Activity.CRAFTING, ServerDetector.fromBukkitInventoryType("WORKBENCH"));
        assertEquals(Activity.CRAFTING, ServerDetector.fromBukkitInventoryType("STONECUTTER"));
        assertEquals(Activity.CONTAINER, ServerDetector.fromBukkitInventoryType("CHEST"));
        assertEquals(Activity.CONTAINER, ServerDetector.fromBukkitInventoryType("SHULKER_BOX"));
        assertEquals(Activity.FURNACE, ServerDetector.fromBukkitInventoryType("BLAST_FURNACE"));
        assertEquals(Activity.ENCHANTING, ServerDetector.fromBukkitInventoryType("ENCHANTING"));
        assertEquals(Activity.ANVIL, ServerDetector.fromBukkitInventoryType("ANVIL"));
        assertEquals(Activity.TRADING, ServerDetector.fromBukkitInventoryType("MERCHANT"));
        assertEquals(Activity.BOOK, ServerDetector.fromBukkitInventoryType("LECTERN"));

        assertTrue(ServerDetector.isAfkCommand("/afk"));
        assertTrue(ServerDetector.isAfkCommand("/playerstatuses:afk"));
        assertTrue(ServerDetector.isAfkCommand("  /AFK  "));
        assertFalse(ServerDetector.isAfkCommand("/say hello"));
        assertFalse(ServerDetector.isAfkCommand("/playerstatuses status"));
    }
}
