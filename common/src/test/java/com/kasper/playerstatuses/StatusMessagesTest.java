package com.kasper.playerstatuses;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StatusMessagesTest {

    @Test
    void updateRoundTrip() {
        PlayerStatus s = new PlayerStatus(Activity.CONTAINER, StatusFlag.AFK.bit() | StatusFlag.MIC_OFF.bit(), 754);
        byte[] bytes = StatusMessages.update(s);
        assertEquals(s, StatusMessages.readUpdate(bytes));
        // kind, version, activity, flags, two bytes of VarInt
        assertEquals(6, bytes.length);
    }

    @Test
    void updateIsTiny() {
        assertEquals(5, StatusMessages.update(PlayerStatus.of(Activity.TYPING)).length);
    }

    @Test
    void statusesRoundTrip() {
        List<StatusMessages.Entry> entries = Arrays.asList(
                new StatusMessages.Entry(UUID.randomUUID(), PlayerStatus.of(Activity.INVENTORY)),
                new StatusMessages.Entry(UUID.randomUUID(), new PlayerStatus(Activity.NONE, StatusFlag.AFK.bit(), 3600)),
                new StatusMessages.Entry(UUID.randomUUID(), PlayerStatus.NONE));
        assertEquals(entries, StatusMessages.readStatuses(StatusMessages.statuses(entries)));
    }

    @Test
    void manyPlayersAreSplit() {
        List<StatusMessages.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            entries.add(new StatusMessages.Entry(new UUID(i, i), PlayerStatus.of(Activity.TYPING)));
        }
        List<byte[]> parts = StatusMessages.statusesSplit(entries);
        assertEquals(3, parts.size());
        List<StatusMessages.Entry> back = new ArrayList<>();
        parts.forEach(p -> back.addAll(StatusMessages.readStatuses(p)));
        assertEquals(entries, back);
    }

    @Test
    void unknownKindOrVersionIsIgnored() {
        byte[] update = StatusMessages.update(PlayerStatus.of(Activity.TYPING));
        assertNull(StatusMessages.readStatuses(update));
        byte[] newer = update.clone();
        newer[1] = (byte) (StatusMessages.VERSION + 1);
        assertNull(StatusMessages.readUpdate(newer));
    }

    @Test
    void brokenMessagesAreRejected() {
        assertNull(StatusMessages.readUpdate(new byte[0]));
        assertNull(StatusMessages.readUpdate(new byte[]{1, 1, 2}));
        assertNull(StatusMessages.readUpdate(null));
        // An endless VarInt
        assertNull(StatusMessages.readUpdate(new byte[]{1, 1, 2, 0, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 1}));
        // A count far above the limit
        assertNull(StatusMessages.readStatuses(new byte[]{2, 1, (byte) 0xff, (byte) 0xff, 0x7f}));
        // A count that promises more entries than there are
        assertNull(StatusMessages.readStatuses(new byte[]{2, 1, 2}));
    }

    @Test
    void newerActivitiesReadAsBusyAndUnknownFlagsAreDropped() {
        PlayerStatus s = StatusMessages.readUpdate(new byte[]{1, 1, (byte) 200, (byte) 0xf0, 0});
        assertEquals(Activity.BUSY, s.activity());
        assertEquals(0, s.flags());
    }

    @Test
    void idleTimeIsCapped() {
        PlayerStatus s = new PlayerStatus(Activity.NONE, StatusFlag.AFK.bit(), Integer.MAX_VALUE);
        assertEquals(StatusMessages.MAX_IDLE_SECONDS, StatusMessages.readUpdate(StatusMessages.update(s)).idleSeconds());
    }

    @Test
    void activityIdsAreUniqueAndFitAByte() {
        boolean[] seen = new boolean[256];
        for (Activity a : Activity.values()) {
            assertTrue(a.id() >= 0 && a.id() < 256);
            assertFalse(seen[a.id()], "Two activities share id " + a.id());
            seen[a.id()] = true;
            assertSame(a, Activity.byId(a.id()));
        }
    }
}
