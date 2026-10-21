package com.kasper.playerstatuses;

import java.io.ByteArrayOutputStream;
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
 * </ul>
 */
public final class StatusMessages {

    public static final String NAMESPACE = "playerstatuses";
    public static final String PATH = "status";
    /** The channel as plugin messaging writes it. */
    public static final String CHANNEL = NAMESPACE + ":" + PATH;

    public static final int UPDATE = 1;
    public static final int STATUSES = 2;
    public static final int VERSION = 1;

    /** More players than this in one message are sent as several messages. */
    public static final int MAX_ENTRIES = 256;
    /** Idle time is capped (a week), so a VarInt of it stays short and nothing overflows. */
    public static final int MAX_IDLE_SECONDS = 7 * 24 * 3600;

    private StatusMessages() {
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
    }
}
