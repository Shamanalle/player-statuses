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
}
