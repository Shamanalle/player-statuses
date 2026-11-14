package com.kasper.playerstatuses;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The bytes the client and the server exchange, the same on every loader and on the Bukkit plugin:
 * one custom payload channel ({@link #NAMESPACE}:{@link #PATH}) whose payload is exactly the bytes
 * below, without a length in front. Each message starts with its kind and the protocol version;
 * a message of an unknown kind or a newer version is ignored, so old and new versions of the mod can
 * meet on one server.
 * <ul>
 * <li>{@link #UPDATE} (client to server): activity byte, flags byte, idle seconds (VarInt). The text a
 * player types is never part of it.</li>
 * <li>{@link #STATUSES} (server to client): a VarInt count, then per player its UUID (two longs) and the
 * same three fields. A player whose status is {@link PlayerStatus#NONE} is no longer shown.</li>
 * <li>{@link #PROJECTION_UPDATE} / {@link #PROJECTION_BROADCAST}: close-range 3D holo layout, optional
 * 4-bit slot occupancy palette, normalized cursor, click cadence and progress.</li>
 * <li>{@link #HOLO_INTERACT} / {@link #HOLO_EVENT}: tactile slot ping (with optional held item id) or
 * AFK bell nudge triggered by raycasting a nearby player's hologram.</li>
 * </ul>
 */
public final class StatusMessages {

    public static final String NAMESPACE = "playerstatuses";
    public static final String PATH = "status";
    /** The channel as plugin messaging writes it. */
    public static final String CHANNEL = NAMESPACE + ":" + PATH;

    public static final int UPDATE = 1;
    public static final int STATUSES = 2;
    public static final int PROJECTION_UPDATE = 3;
    public static final int PROJECTION_BROADCAST = 4;
    public static final int HOLO_INTERACT = 5;
    public static final int HOLO_EVENT = 6;
    public static final int VERSION = 1;

    /** More players than this in one message are sent as several messages. */
    public static final int MAX_ENTRIES = 256;
    /** More projection entries than this in one broadcast are split across messages. */
    public static final int MAX_PROJECTION_ENTRIES = 64;
    /** Idle time is capped (a week), so a VarInt of it stays short and nothing overflows. */
    public static final int MAX_IDLE_SECONDS = 7 * 24 * 3600;
    private static final int MAX_STRING_BYTES = 128;

    private StatusMessages() {
    }

    /** Returns the message kind byte if the header matches {@link #VERSION}, or {@code -1} otherwise. */
    public static int peekKind(byte[] data) {
        if (data == null || data.length < 2 || (data[1] & 0xff) != VERSION) {
            return -1;
        }
        return data[0] & 0xff;
    }

    /** One player's status in a {@link #STATUSES} message. */
    public static final class Entry {
        private final UUID player;
        private final PlayerStatus status;

        public Entry(UUID player, PlayerStatus status) {
            this.player = player;
            this.status = status;
        }

        public UUID player() {
            return player;
        }

        public PlayerStatus status() {
            return status;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Entry && ((Entry) o).player.equals(player) && ((Entry) o).status.equals(status);
        }

        @Override
        public int hashCode() {
            return player.hashCode() * 31 + status.hashCode();
        }

        @Override
        public String toString() {
            return player + "=" + status;
        }
    }

    public static byte[] update(PlayerStatus status) {
        Writer w = new Writer();
        w.b(UPDATE).b(VERSION);
        writeStatus(w, status);
        return w.bytes();
    }

    /** The client's status from an {@link #UPDATE} message, or {@code null} if it is not one this version reads. */
    public static PlayerStatus readUpdate(byte[] data) {
        try {
            Reader r = new Reader(data);
            if (r.u8() != UPDATE || r.u8() != VERSION) {
                return null;
            }
            return readStatus(r);
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return null;
        }
    }

    /** One {@link #STATUSES} message for up to {@link #MAX_ENTRIES} entries (see {@link #statusesSplit}). */
    public static byte[] statuses(List<Entry> entries) {
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("At most " + MAX_ENTRIES + " entries per message");
        }
        Writer w = new Writer();
        w.b(STATUSES).b(VERSION).varInt(entries.size());
        for (Entry e : entries) {
            w.l(e.player.getMostSignificantBits()).l(e.player.getLeastSignificantBits());
            writeStatus(w, e.status);
        }
        return w.bytes();
    }

    /** As many {@link #STATUSES} messages as {@code entries} need. */
    public static List<byte[]> statusesSplit(List<Entry> entries) {
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i += MAX_ENTRIES) {
            out.add(statuses(entries.subList(i, Math.min(entries.size(), i + MAX_ENTRIES))));
        }
        return out;
    }

    /** The entries of a {@link #STATUSES} message, or {@code null} if it is not one this version reads. */
    public static List<Entry> readStatuses(byte[] data) {
        try {
            Reader r = new Reader(data);
            if (r.u8() != STATUSES || r.u8() != VERSION) {
                return null;
            }
            int n = r.varInt();
            if (n < 0 || n > MAX_ENTRIES) {
                return null;
            }
            List<Entry> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                UUID id = new UUID(r.l(), r.l());
                out.add(new Entry(id, readStatus(r)));
            }
            return Collections.unmodifiableList(out);
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return null;
        }
    }

    private static void writeStatus(Writer w, PlayerStatus s) {
        w.b(s.activity().id()).b(s.flags()).varInt(Math.min(MAX_IDLE_SECONDS, s.idleSeconds()));
    }

    private static PlayerStatus readStatus(Reader r) {
        Activity activity = Activity.byId(r.u8());
        int flags = r.u8();
        int idle = r.varInt();
        if (idle < 0) {
            throw new IllegalArgumentException("negative idle time");
        }
        return new PlayerStatus(activity, flags, Math.min(MAX_IDLE_SECONDS, idle));
    }

    /** One player's projection snapshot in a {@link #PROJECTION_BROADCAST} message. */
    public static final class ProjectionEntry {
        private final UUID player;
        private final HoloState.Projection projection;

        public ProjectionEntry(UUID player, HoloState.Projection projection) {
            this.player = player;
            this.projection = projection == null ? HoloState.Projection.EMPTY : projection;
        }

        public UUID player() {
            return player;
        }

        public HoloState.Projection projection() {
            return projection;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ProjectionEntry
                    && ((ProjectionEntry) o).player.equals(player)
                    && ((ProjectionEntry) o).projection.equals(projection);
        }

        @Override
        public int hashCode() {
            return player.hashCode() * 31 + projection.hashCode();
        }
    }

    public static byte[] projectionUpdate(HoloState.Projection projection) {
        HoloState.Projection p = projection == null ? HoloState.Projection.EMPTY : projection;
        Writer w = new Writer();
        w.b(PROJECTION_UPDATE).b(VERSION);
        writeProjection(w, p);
        return w.bytes();
    }

    public static HoloState.Projection readProjectionUpdate(byte[] data) {
        try {
            Reader r = new Reader(data);
            if (r.u8() != PROJECTION_UPDATE || r.u8() != VERSION) {
                return null;
            }
            return readProjection(r);
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return null;
        }
    }

    public static byte[] projectionBroadcast(List<ProjectionEntry> entries) {
        if (entries.size() > MAX_PROJECTION_ENTRIES) {
            throw new IllegalArgumentException("At most " + MAX_PROJECTION_ENTRIES + " projection entries per message");
        }
        Writer w = new Writer();
        w.b(PROJECTION_BROADCAST).b(VERSION).varInt(entries.size());
        for (ProjectionEntry e : entries) {
            w.l(e.player.getMostSignificantBits()).l(e.player.getLeastSignificantBits());
            writeProjection(w, e.projection);
        }
        return w.bytes();
    }

    public static List<byte[]> projectionBroadcastSplit(List<ProjectionEntry> entries) {
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i += MAX_PROJECTION_ENTRIES) {
            out.add(projectionBroadcast(entries.subList(i, Math.min(entries.size(), i + MAX_PROJECTION_ENTRIES))));
        }
        return out;
    }

    public static List<ProjectionEntry> readProjectionBroadcast(byte[] data) {
        try {
            Reader r = new Reader(data);
            if (r.u8() != PROJECTION_BROADCAST || r.u8() != VERSION) {
                return null;
            }
            int n = r.varInt();
            if (n < 0 || n > MAX_PROJECTION_ENTRIES) {
                return null;
            }
            List<ProjectionEntry> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                UUID id = new UUID(r.l(), r.l());
                out.add(new ProjectionEntry(id, readProjection(r)));
            }
            return Collections.unmodifiableList(out);
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return null;
        }
    }

    public static byte[] holoInteract(HoloState.Interact interact) {
        Writer w = new Writer();
        w.b(HOLO_INTERACT).b(VERSION)
                .l(interact.target().getMostSignificantBits())
                .l(interact.target().getLeastSignificantBits())
                .b(interact.action().id())
                .b(encodeSlot(interact.slotIndex()))
                .str(interact.ghostItemDescId());
        return w.bytes();
    }

    public static HoloState.Interact readHoloInteract(byte[] data) {
        try {
            Reader r = new Reader(data);
            if (r.u8() != HOLO_INTERACT || r.u8() != VERSION) {
                return null;
            }
            UUID target = new UUID(r.l(), r.l());
            HoloState.Action action = HoloState.Action.byId(r.u8());
            int slot = decodeSlot(r.u8());
            String ghostDesc = r.str(MAX_STRING_BYTES);
            if (action == null || !HoloState.isValidSlotIndex(slot)) {
                return null;
            }
            return new HoloState.Interact(target, action, slot, ghostDesc);
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return null;
        }
    }

    public static byte[] holoEvent(HoloState.Event event) {
        Writer w = new Writer();
        w.b(HOLO_EVENT).b(VERSION)
                .l(event.sender().getMostSignificantBits())
                .l(event.sender().getLeastSignificantBits())
                .l(event.target().getMostSignificantBits())
                .l(event.target().getLeastSignificantBits())
                .b(event.action().id())
                .b(encodeSlot(event.slotIndex()))
                .str(event.ghostItemDescId());
        return w.bytes();
    }

    public static HoloState.Event readHoloEvent(byte[] data, long now) {
        try {
            Reader r = new Reader(data);
            if (r.u8() != HOLO_EVENT || r.u8() != VERSION) {
                return null;
            }
            UUID sender = new UUID(r.l(), r.l());
            UUID target = new UUID(r.l(), r.l());
            HoloState.Action action = HoloState.Action.byId(r.u8());
            int slot = decodeSlot(r.u8());
            String ghostDesc = r.str(MAX_STRING_BYTES);
            if (action == null || !HoloState.isValidSlotIndex(slot)) {
                return null;
            }
            return new HoloState.Event(sender, target, action, slot, ghostDesc, now);
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return null;
        }
    }

    private static int encodeSlot(int slotIndex) {
        return slotIndex < 0 ? 255 : Math.min(254, slotIndex);
    }

    private static int decodeSlot(int raw) {
        return raw == 255 ? -1 : raw;
    }

    private static void writeProjection(Writer w, HoloState.Projection p) {
        w.b(p.layout().id());
        int count = p.slotCount();
        w.b(count);
        for (int i = 0; i < count; i += 2) {
            int lo = p.slotState(i) & 0x0f;
            int hi = (i + 1 < count ? p.slotState(i + 1) : 0) & 0x0f;
            w.b(lo | (hi << 4));
        }
        w.b(Math.round(p.cursorU() * 250f));
        w.b(Math.round(p.cursorV() * 250f));
        w.b(p.pulseSeq());
        w.b(p.progress());
        w.b(p.aux());
    }

    private static HoloState.Projection readProjection(Reader r) {
        HoloState.LayoutKind layout = HoloState.LayoutKind.byId(r.u8());
        int count = r.u8();
        if (count < 0 || count > HoloState.MAX_SLOTS) {
            throw new IllegalArgumentException("bad slot count");
        }
        byte[] slots = new byte[count];
        for (int i = 0; i < count; i += 2) {
            int packed = r.u8();
            slots[i] = (byte) HoloState.sanitizeSlotState(packed & 0x0f);
            if (i + 1 < count) {
                slots[i + 1] = (byte) HoloState.sanitizeSlotState((packed >>> 4) & 0x0f);
            }
        }
        float cu = Math.min(250, r.u8()) / 250f;
        float cv = Math.min(250, r.u8()) / 250f;
        int pulse = r.u8();
        int progress = r.u8();
        int aux = r.u8();
        return new HoloState.Projection(layout, slots, cu, cv, pulse, progress, aux);
    }

    /** VarInt, longs in big-endian order: the way Minecraft's own buffers write them. */
    static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(32);

        Writer b(int v) {
            out.write(v & 0xff);
            return this;
        }

        Writer l(long v) {
            for (int i = 7; i >= 0; i--) {
                out.write((int) (v >>> (i * 8)) & 0xff);
            }
            return this;
        }

        Writer varInt(int v) {
            while ((v & ~0x7f) != 0) {
                out.write((v & 0x7f) | 0x80);
                v >>>= 7;
            }
            out.write(v);
            return this;
        }

        Writer str(String s) {
            byte[] utf = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
            varInt(utf.length);
            out.write(utf, 0, utf.length);
            return this;
        }

        byte[] bytes() {
            return out.toByteArray();
        }
    }

    static final class Reader {
        private final byte[] data;
        private int pos;

        Reader(byte[] data) {
            if (data == null) {
                throw new IllegalArgumentException("no data");
            }
            this.data = data;
        }

        int u8() {
            if (pos >= data.length) {
                throw new IndexOutOfBoundsException("end of message");
            }
            return data[pos++] & 0xff;
        }

        long l() {
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | u8();
            }
            return v;
        }

        int varInt() {
            int v = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int b = u8();
                v |= (b & 0x7f) << shift;
                if ((b & 0x80) == 0) {
                    return v;
                }
            }
            throw new IllegalArgumentException("VarInt too long");
        }

        String str(int maxBytes) {
            int len = varInt();
            if (len < 0 || len > maxBytes || pos + len > data.length) {
                throw new IllegalArgumentException("bad string length");
            }
            String s = new String(data, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }
    }
}
