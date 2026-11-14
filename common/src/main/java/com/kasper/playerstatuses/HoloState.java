package com.kasper.playerstatuses;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable data types for close-range interactive 3D Holo-Projections. Pure Java 8 with no
 * Minecraft imports so core logic compiles and tests without a game jar.
 */
public final class HoloState {

    /** Maximum number of slots encoded inside one projection snapshot (9x6 container). */
    public static final int MAX_SLOTS = 54;

    /** Virtual slot id representing the interactive "Call" button on {@link LayoutKind#IDLE_CARD}. */
    public static final int AFK_CALL_BUTTON_SLOT = 60;

    /** Slot occupancy / tier levels (fits in 3 bits, carried in 4-bit nibbles). */
    public static final int SLOT_EMPTY = 0;
    public static final int SLOT_COMMON = 1;
    public static final int SLOT_UNCOMMON = 2;
    public static final int SLOT_ENCHANTED = 3;
    public static final int SLOT_FULL_STACK = 4;

    public static final int MAX_GHOST_DESC_LEN = 64;

    private HoloState() {
    }

    /** Visual template rendered on a nearby player's chest hologram. */
    public enum LayoutKind {
        NONE(0),
        INVENTORY(1),
        CONTAINER_27(2),
        CONTAINER_54(3),
        CRAFTING(4),
        FURNACE(5),
        ENCHANTING(6),
        ANVIL(7),
        TRADING(8),
        TYPING(9),
        BOOK(10),
        SIGN(11),
        MAP(12),
        IDLE_CARD(13),
        GENERIC_PANEL(14);

        private static final LayoutKind[] BY_ID = new LayoutKind[32];

        static {
            for (LayoutKind kind : values()) {
                BY_ID[kind.id] = kind;
            }
        }

        private final int id;

        LayoutKind(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public boolean isNone() {
            return this == NONE;
        }

        public static LayoutKind byId(int id) {
            if (id < 0 || id >= BY_ID.length) {
                return GENERIC_PANEL;
            }
            LayoutKind kind = BY_ID[id];
            return kind == null ? GENERIC_PANEL : kind;
        }

        /** Maps an overhead {@link PlayerStatus} to its fallback 3D procedural hologram layout. */
        public static LayoutKind defaultFor(PlayerStatus status) {
            if (status == null) {
                return NONE;
            }
            Activity activity = status.activity();
            switch (activity) {
                case INVENTORY:
                    return INVENTORY;
                case CRAFTING:
                    return CRAFTING;
                case CONTAINER:
                    return CONTAINER_27;
                case FURNACE:
                    return FURNACE;
                case ENCHANTING:
                    return ENCHANTING;
                case ANVIL:
                    return ANVIL;
                case TRADING:
                    return TRADING;
                case CHAT:
                case TYPING:
                case COMMAND:
                    return TYPING;
                case BOOK:
                    return BOOK;
                case SIGN:
                    return SIGN;
                case MAP:
                    return MAP;
                case PAUSE:
                case SETTINGS:
                case ADVANCEMENTS:
                case BUSY:
                case LOADING:
                    return status.has(StatusFlag.AFK) ? IDLE_CARD : GENERIC_PANEL;
                case NONE:
                default:
                    if (status.has(StatusFlag.AFK)) {
                        return IDLE_CARD;
                    }
                    if (status.has(StatusFlag.UNFOCUSED)) {
                        return GENERIC_PANEL;
                    }
                    return NONE;
            }
        }
    }

    /** Player-triggered tactile hologram interactions sent over the network. */
    public enum Action {
        PING_SLOT(1),
        KNOCK_AFK(2);

        private final int id;

        Action(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Action byId(int id) {
            if (id == PING_SLOT.id) {
                return PING_SLOT;
            }
            if (id == KNOCK_AFK.id) {
                return KNOCK_AFK;
            }
            return null;
        }
    }

    public static int sanitizeSlotState(int raw) {
        if (raw <= SLOT_EMPTY) {
            return SLOT_EMPTY;
        }
        return raw > SLOT_FULL_STACK ? SLOT_COMMON : raw;
    }

    public static float clamp01(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) {
            return 0.5f;
        }
        return Math.max(0f, Math.min(1f, v));
    }

    public static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static boolean isValidSlotIndex(int slotIndex) {
        return slotIndex == -1
                || (slotIndex >= 0 && slotIndex < MAX_SLOTS)
                || slotIndex == AFK_CALL_BUTTON_SLOT;
    }

    /** Filters an item translation key so untrusted peers can never smuggle unexpected characters. */
    public static String sanitizeDescId(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int limit = Math.min(raw.length(), MAX_GHOST_DESC_LEN);
        StringBuilder sb = new StringBuilder(limit);
        for (int i = 0; i < limit; i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-' || c == ':' || c == '/';
            if (!ok) {
                return "";
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Snapshot of what a player's chest hologram should show right now. */
    public static final class Projection {

        public static final Projection EMPTY = new Projection(
                LayoutKind.NONE, new byte[0], 0.5f, 0.5f, 0, 0, 0);

        private final LayoutKind layout;
        private final byte[] slots;
        private final float cursorU;
        private final float cursorV;
        private final int pulseSeq;
        private final int progress;
        private final int aux;

        public Projection(LayoutKind layout, byte[] slots, float cursorU, float cursorV,
                          int pulseSeq, int progress, int aux) {
            this.layout = layout == null ? LayoutKind.NONE : layout;
            this.slots = sanitizeSlots(slots);
            this.cursorU = clamp01(cursorU);
            this.cursorV = clamp01(cursorV);
            this.pulseSeq = pulseSeq & 0xFF;
            this.progress = clampByte(progress);
            this.aux = clampByte(aux);
        }

        private static byte[] sanitizeSlots(byte[] raw) {
            if (raw == null || raw.length == 0) {
                return new byte[0];
            }
            int len = Math.min(raw.length, MAX_SLOTS);
            byte[] copy = new byte[len];
            for (int i = 0; i < len; i++) {
                copy[i] = (byte) sanitizeSlotState(raw[i] & 0xFF);
            }
            return copy;
        }

        public LayoutKind layout() {
            return layout;
        }

        public int slotCount() {
            return slots.length;
        }

        public int slotState(int index) {
            if (index < 0 || index >= slots.length) {
                return SLOT_EMPTY;
            }
            return slots[index] & 0xFF;
        }

        public byte[] slotsCopy() {
            return Arrays.copyOf(slots, slots.length);
        }

        public float cursorU() {
            return cursorU;
        }

        public float cursorV() {
            return cursorV;
        }

        public int pulseSeq() {
            return pulseSeq;
        }

        public int progress() {
            return progress;
        }

        public int aux() {
            return aux;
        }

        public boolean isNone() {
            return layout == LayoutKind.NONE;
        }

        /** Strips slot occupancy details for privacy while keeping layout, cursor and pulses. */
        public Projection withoutSlots() {
            if (slots.length == 0) {
                return this;
            }
            return new Projection(layout, new byte[0], cursorU, cursorV, pulseSeq, progress, aux);
        }

        public Projection withCursor(float newU, float newV) {
            return new Projection(layout, slots, newU, newV, pulseSeq, progress, aux);
        }

        /** True when a network sync is warranted (ignores sub-2.5% cursor micro-jitter). */
        public boolean meaningfullyDiffersFrom(Projection other) {
            if (other == null) {
                return true;
            }
            if (layout != other.layout
                    || pulseSeq != other.pulseSeq
                    || progress != other.progress
                    || aux != other.aux
                    || !Arrays.equals(slots, other.slots)) {
                return true;
            }
            return Math.abs(cursorU - other.cursorU) > 0.025f
                    || Math.abs(cursorV - other.cursorV) > 0.025f;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Projection)) {
                return false;
            }
            Projection p = (Projection) o;
            return layout == p.layout
                    && Float.compare(p.cursorU, cursorU) == 0
                    && Float.compare(p.cursorV, cursorV) == 0
                    && pulseSeq == p.pulseSeq
                    && progress == p.progress
                    && aux == p.aux
                    && Arrays.equals(slots, p.slots);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(layout, cursorU, cursorV, pulseSeq, progress, aux);
            result = 31 * result + Arrays.hashCode(slots);
            return result;
        }
    }

    /** Client-to-server request to ping a slot or nudge an AFK player via their hologram. */
    public static final class Interact {

        private final UUID target;
        private final Action action;
        private final int slotIndex;
        private final String ghostItemDescId;

        public Interact(UUID target, Action action, int slotIndex, String ghostItemDescId) {
            this.target = Objects.requireNonNull(target, "target");
            this.action = Objects.requireNonNull(action, "action");
            this.slotIndex = isValidSlotIndex(slotIndex) ? slotIndex : -1;
            this.ghostItemDescId = sanitizeDescId(ghostItemDescId);
        }

        public UUID target() {
            return target;
        }

        public Action action() {
            return action;
        }

        public int slotIndex() {
            return slotIndex;
        }

        public String ghostItemDescId() {
            return ghostItemDescId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Interact)) {
                return false;
            }
            Interact that = (Interact) o;
            return slotIndex == that.slotIndex
                    && target.equals(that.target)
                    && action == that.action
                    && ghostItemDescId.equals(that.ghostItemDescId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(target, action, slotIndex, ghostItemDescId);
        }
    }

    /** Server-relayed hologram interaction event displayed on nearby clients and target overlays. */
    public static final class Event {

        private final UUID sender;
        private final UUID target;
        private final Action action;
        private final int slotIndex;
        private final String ghostItemDescId;
        private final long createdAtMs;

        public Event(UUID sender, UUID target, Action action, int slotIndex,
                     String ghostItemDescId, long createdAtMs) {
            this.sender = Objects.requireNonNull(sender, "sender");
            this.target = Objects.requireNonNull(target, "target");
            this.action = Objects.requireNonNull(action, "action");
            this.slotIndex = isValidSlotIndex(slotIndex) ? slotIndex : -1;
            this.ghostItemDescId = sanitizeDescId(ghostItemDescId);
            this.createdAtMs = createdAtMs;
        }

        public UUID sender() {
            return sender;
        }

        public UUID target() {
            return target;
        }

        public Action action() {
            return action;
        }

        public int slotIndex() {
            return slotIndex;
        }

        public String ghostItemDescId() {
            return ghostItemDescId;
        }

        public long createdAtMs() {
            return createdAtMs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Event)) {
                return false;
            }
            Event that = (Event) o;
            return slotIndex == that.slotIndex
                    && createdAtMs == that.createdAtMs
                    && sender.equals(that.sender)
                    && target.equals(that.target)
                    && action == that.action
                    && ghostItemDescId.equals(that.ghostItemDescId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sender, target, action, slotIndex, ghostItemDescId, createdAtMs);
        }
    }
}
