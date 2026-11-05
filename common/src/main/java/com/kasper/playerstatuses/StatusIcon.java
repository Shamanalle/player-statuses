package com.kasper.playerstatuses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Procedural 10x10 pixel-art sprites, accent colours and overhead card layout for every
 * {@link Activity} and {@link StatusFlag}. Pure Java with no Minecraft dependencies, so the same
 * geometry drives overhead badges across 1.16.5–26.x, the settings preview and unit tests.
 */
public final class StatusIcon {

    public static final int SPRITE_SIZE = 10;
    public static final int CARD_HEIGHT = 14;
    public static final int CARD_Y0 = -13;
    public static final int CARD_Y1 = CARD_Y0 + CARD_HEIGHT;

    /** Receives axis-aligned rectangles in local 2D card coordinates with ARGB colour. */
    public interface RectSink {
        void rect(float x0, float y0, float x1, float y1, int argb);
    }

    /** A 10x10 4-colour indexed pixel-art sprite ('.' = transparent, '1'..'3' = palette colours). */
    public static final class Sprite {
        private final String[] rows;
        private final int c1;
        private final int c2;
        private final int c3;

        Sprite(int c1, int c2, int c3, String... rows) {
            if (rows.length != SPRITE_SIZE) {
                throw new IllegalArgumentException("Expected " + SPRITE_SIZE + " rows");
            }
            for (String r : rows) {
                if (r.length() != SPRITE_SIZE) {
                    throw new IllegalArgumentException("Row must be " + SPRITE_SIZE + " chars: " + r);
                }
            }
            this.rows = rows;
            this.c1 = c1;
            this.c2 = c2;
            this.c3 = c3;
        }

        public int accentRgb() {
            return c1;
        }

        public void draw(RectSink sink, float x, float y, float pixelSize, int alpha255) {
            if (alpha255 <= 0) {
                return;
            }
            for (int r = 0; r < SPRITE_SIZE; r++) {
                String row = rows[r];
                int c = 0;
                while (c < SPRITE_SIZE) {
                    char ch = row.charAt(c);
                    int rgb = colorOf(ch);
                    if (rgb < 0) {
                        c++;
                        continue;
                    }
                    int end = c + 1;
                    while (end < SPRITE_SIZE && row.charAt(end) == ch) {
                        end++;
                    }
                    int argb = ((alpha255 & 0xff) << 24) | (rgb & 0xffffff);
                    sink.rect(
                            x + c * pixelSize,
                            y + r * pixelSize,
                            x + end * pixelSize,
                            y + (r + 1) * pixelSize,
                            argb
                    );
                    c = end;
                }
            }
        }

        private int colorOf(char ch) {
            switch (ch) {
                case '1':
                    return c1;
                case '2':
                    return c2;
                case '3':
                    return c3;
                default:
                    return -1;
            }
        }
    }

    /** Layout of an overhead status card in local billboard coordinates (where (0,0) is above the name tag). */
    public static final class CardLayout {
        private final float x0;
        private final float y0;
        private final float x1;
        private final float y1;
        private final float textX;
        private final float textY;
        private final int accentRgb;
        private final int textArgb;
        private final List<StatusFlag> extraFlags;

        CardLayout(
                float x0,
                float y0,
                float x1,
                float y1,
                float textX,
                float textY,
                int accentRgb,
                int textArgb,
                List<StatusFlag> extraFlags
        ) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.textX = textX;
            this.textY = textY;
            this.accentRgb = accentRgb;
            this.textArgb = textArgb;
            this.extraFlags = extraFlags;
        }

        public float x0() {
            return x0;
        }

        public float y0() {
            return y0;
        }

        public float x1() {
            return x1;
        }

        public float y1() {
            return y1;
        }

        public float width() {
            return x1 - x0;
        }

        public float height() {
            return y1 - y0;
        }

        public float textX() {
            return textX;
        }

        public float textY() {
            return textY;
        }

        public int accentRgb() {
            return accentRgb;
        }

        public int textArgb() {
            return textArgb;
        }

        public List<StatusFlag> extraFlags() {
            return extraFlags;
        }
    }

    private static final Map<Activity, Sprite> ACTIVITY_SPRITES = new EnumMap<>(Activity.class);
    private static final Map<StatusFlag, Sprite> FLAG_SPRITES = new EnumMap<>(StatusFlag.class);

    static {
        // Speech bubble (chat open)
        ACTIVITY_SPRITES.put(Activity.CHAT, new Sprite(0x64B5F6, 0xE3F2FD, 0x1E88E5,
                "..111111..",
                ".12222221.",
                "1222222221",
                "1222222221",
                "1222222221",
                "1222222221",
                ".12222221.",
                "..1122111.",
                "...121....",
                "..111....."
        ));
        // Speech bubble with three typing dots
        ACTIVITY_SPRITES.put(Activity.TYPING, new Sprite(0x4FC3F7, 0xFFFFFF, 0x0288D1,
                "..111111..",
                ".12222221.",
                "1222222221",
                "1232232231",
                "1232232231",
                "1222222221",
                ".12222221.",
                "..1122111.",
                "...121....",
                "..111....."
        ));
        // Command prompt (>_ / slash)
        ACTIVITY_SPRITES.put(Activity.COMMAND, new Sprite(0xCE93D8, 0xF3E5F5, 0x4A148C,
                ".11111111.",
                "1333333331",
                "1333332331",
                "1333322331",
                "1333223331",
                "1332233331",
                "1322332221",
                "1333333331",
                ".11111111.",
                ".........."
        ));
        // Backpack / player inventory
        ACTIVITY_SPRITES.put(Activity.INVENTORY, new Sprite(0xFFB74D, 0xFFE0B2, 0x8D4E17,
                "...1111...",
                "..133331..",
                ".11111111.",
                "1221111221",
                "1112222111",
                "1312332131",
                "1111111111",
                "1311111131",
                "1311111131",
                ".11111111."
        ));
        // Crafting table (3x3 grid + wood body)
        ACTIVITY_SPRITES.put(Activity.CRAFTING, new Sprite(0xD49A56, 0xFFF3E0, 0x5D3A1A,
                ".33333333.",
                "3223223223",
                "3223223223",
                "3333333333",
                "3223223223",
                "3333333333",
                "3113111133",
                "3113133133",
                "3113111133",
                ".33333333."
        ));
        // Chest (golden latch + oak planks)
        ACTIVITY_SPRITES.put(Activity.CONTAINER, new Sprite(0xE09F3E, 0xFFD54F, 0x5C3811,
                ".33333333.",
                "3111111113",
                "3111111113",
                "3333223333",
                "3113223113",
                "3111221113",
                "3111111113",
                "3111111113",
                ".33333333.",
                ".........."
        ));
        // Furnace with bright flame
        ACTIVITY_SPRITES.put(Activity.FURNACE, new Sprite(0xFF7043, 0xFFCA28, 0x616161,
                ".33333333.",
                "3333333333",
                "3331111333",
                "3311221133",
                "3333333333",
                "3333113333",
                "3331221333",
                "3312222133",
                "3331221333",
                ".33333333."
        ));
        // Enchanting table (gem + book + altar)
        ACTIVITY_SPRITES.put(Activity.ENCHANTING, new Sprite(0xAB47BC, 0x80DEEA, 0xE53935,
                "....22....",
                "...2222...",
                "....22....",
                ".11122111.",
                "1222112221",
                ".33333333.",
                "3323333233",
                "3333333333",
                ".33333333.",
                ".........."
        ));
        // Anvil
        ACTIVITY_SPRITES.put(Activity.ANVIL, new Sprite(0x90A4AE, 0xECEFF1, 0x37474F,
                "..........",
                "3322222233",
                "3222222223",
                ".33111133.",
                "...3113...",
                "...3113...",
                "..311113..",
                ".32222223.",
                "3333333333",
                ".........."
        ));
        // Trading emerald
        ACTIVITY_SPRITES.put(Activity.TRADING, new Sprite(0x2ECC71, 0xA9DFBF, 0x145A32,
                "...3333...",
                "..312213..",
                ".31221113.",
                "3121111113",
                "3121111113",
                "3111112113",
                "3111112113",
                ".31112113.",
                "..311113..",
                "...3333..."
        ));
        // Open book
        ACTIVITY_SPRITES.put(Activity.BOOK, new Sprite(0xFBC02D, 0xFFF9C4, 0x8D6E63,
                "..........",
                ".3333.3333",
                "3222332223",
                "3212332123",
                "3222332223",
                "3212332123",
                "3222332223",
                "3333113333",
                "....11....",
                ".........."
        ));
        // Signpost
        ACTIVITY_SPRITES.put(Activity.SIGN, new Sprite(0xD7A86E, 0xFFE0B2, 0x5D4037,
                ".33333333.",
                "3222222223",
                "3233333223",
                "3222222223",
                "3233332223",
                ".33333333.",
                "....31....",
                "....31....",
                "....31....",
                "....33...."
        ));
        // Map with compass marker
        ACTIVITY_SPRITES.put(Activity.MAP, new Sprite(0x66BB6A, 0xFFF59D, 0x2E7D32,
                ".22232223.",
                "2211221122",
                "2111221112",
                "2113223112",
                "2233221122",
                "2222111222",
                "2112111122",
                "2112211222",
                ".22232223.",
                ".........."
        ));
        // Pause bars
        ACTIVITY_SPRITES.put(Activity.PAUSE, new Sprite(0xB0BEC5, 0xFFFFFF, 0x455A64,
                "..........",
                ".333..333.",
                ".321..321.",
                ".321..321.",
                ".321..321.",
                ".321..321.",
                ".321..321.",
                ".321..321.",
                ".333..333.",
                ".........."
        ));
        // Settings gear
        ACTIVITY_SPRITES.put(Activity.SETTINGS, new Sprite(0x90A4AE, 0xECEFF1, 0x37474F,
                "...3113...",
                ".31222213.",
                "3122332213",
                "3223..3223",
                "123....321",
                "123....321",
                "3223..3223",
                "3122332213",
                ".31222213.",
                "...3113..."
        ));
        // Advancements star/trophy
        ACTIVITY_SPRITES.put(Activity.ADVANCEMENTS, new Sprite(0xFFD54F, 0xFFFDE7, 0xF57F17,
                "....11....",
                "...1221...",
                "1112222111",
                ".12222221.",
                "..122221..",
                "..121121..",
                ".121..121.",
                ".13....31.",
                "..........",
                ".........."
        ));
        // Busy hourglass
        ACTIVITY_SPRITES.put(Activity.BUSY, new Sprite(0xFFB300, 0xFFE082, 0x6D4C41,
                ".33333333.",
                "..122221..",
                "..121121..",
                "...1111...",
                "....11....",
                "....11....",
                "...1221...",
                "..121121..",
                "..111111..",
                ".33333333."
        ));
        // Loading portal / spinner
        ACTIVITY_SPRITES.put(Activity.LOADING, new Sprite(0x7E57C2, 0xD1C4E9, 0x311B92,
                "...1111...",
                ".11222211.",
                ".12333321.",
                "123....321",
                "123.22.321",
                "123.22.321",
                "123....321",
                ".12333321.",
                ".11222211.",
                "...1111..."
        ));
        ACTIVITY_SPRITES.put(Activity.NONE, ACTIVITY_SPRITES.get(Activity.BUSY));

        // AFK crescent moon + zzz
        FLAG_SPRITES.put(StatusFlag.AFK, new Sprite(0xFFCA28, 0xFFF8E1, 0xFF8F00,
                "..111..222",
                ".1221....2",
                "1221....2.",
                "1221...222",
                "1221......",
                "12221..111",
                ".12221..1.",
                "..1111.111",
                "..........",
                ".........."
        ));
        // Tabbed out / unfocused window
        FLAG_SPRITES.put(StatusFlag.UNFOCUSED, new Sprite(0x78909C, 0xCFD8DC, 0x37474F,
                "3333333...",
                "3222223...",
                "3111113333",
                "3111113223",
                "3333333113",
                "...3111113",
                "...3111113",
                "...3111113",
                "...3333333",
                ".........."
        ));
        // Microphone off (mic with red slash)
        FLAG_SPRITES.put(StatusFlag.MIC_OFF, new Sprite(0xEF5350, 0xECEFF1, 0x455A64,
                "1..3223...",
                ".1.3223...",
                "..13223...",
                "..31223..3",
                "..33123..3",
                "..3.313.3.",
                "...33313..",
                "....33.1..",
                "...3333.1.",
                ".........1"
        ));
        // Sound off / deafened (speaker with red slash)
        FLAG_SPRITES.put(StatusFlag.SOUND_OFF, new Sprite(0xE53935, 0xECEFF1, 0x455A64,
                "1....32...",
                ".1..322...",
                "..13222..1",
                "..32122.1.",
                "..322121..",
                "..322121..",
                "..32122.1.",
                "..13222..1",
                ".1..322...",
                "1....32..."
        ));
    }

    private StatusIcon() {
    }

    public static Sprite sprite(Activity activity) {
        Sprite s = ACTIVITY_SPRITES.get(activity);
        return s != null ? s : ACTIVITY_SPRITES.get(Activity.BUSY);
    }

    public static Sprite sprite(StatusFlag flag) {
        return FLAG_SPRITES.get(flag);
    }

    /** Primary sprite for a status (its activity if active, otherwise its highest-priority flag). */
    public static Sprite primarySprite(PlayerStatus status) {
        if (status.activity() != Activity.NONE) {
            return sprite(status.activity());
        }
        StatusFlag primary = primaryFlag(status);
        return primary != null ? sprite(primary) : sprite(Activity.BUSY);
    }

    /** If {@code status} has no activity, returns the flag shown as the primary icon & label. */
    public static StatusFlag primaryFlag(PlayerStatus status) {
        if (status.has(StatusFlag.AFK)) {
            return StatusFlag.AFK;
        }
        if (status.has(StatusFlag.UNFOCUSED)) {
            return StatusFlag.UNFOCUSED;
        }
        if (status.has(StatusFlag.SOUND_OFF)) {
            return StatusFlag.SOUND_OFF;
        }
        if (status.has(StatusFlag.MIC_OFF)) {
            return StatusFlag.MIC_OFF;
        }
        return null;
    }

    /** Flags to show as secondary icons on the right side of the card. */
    public static List<StatusFlag> secondaryFlags(PlayerStatus status) {
        StatusFlag primary = status.activity() == Activity.NONE ? primaryFlag(status) : null;
        List<StatusFlag> out = new ArrayList<>(4);
        for (StatusFlag f : StatusFlag.values()) {
            if (status.has(f) && f != primary) {
                out.add(f);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** 24-bit RGB accent colour of the status. */
    public static int accentRgb(PlayerStatus status) {
        return primarySprite(status).accentRgb();
    }

    /** Compact BMP symbol safe for Minecraft's default font in every version (1.16.5–26.x). */
    public static String symbol(PlayerStatus status) {
        if (status.activity() != Activity.NONE) {
            switch (status.activity()) {
                case CHAT:
                case TYPING:
                    return "\u270e"; // ✎
                case COMMAND:
                    return "/";
                case INVENTORY:
                    return "\u25a3"; // ▣
                case CRAFTING:
                    return "\u2692"; // ⚒
                case CONTAINER:
                    return "\u25a4"; // ▤
                case FURNACE:
                    return "\u2668"; // ♨
                case ENCHANTING:
                    return "\u2726"; // ✦
                case ANVIL:
                    return "\u2694"; // ⚔
                case TRADING:
                    return "\u25c6"; // ◆
                case BOOK:
                case SIGN:
                    return "\u270d"; // ✍
                case MAP:
                    return "\u2316"; // ⌖
                case PAUSE:
                    return "\u2016"; // ‖
                case SETTINGS:
                    return "\u2699"; // ⚙
                case ADVANCEMENTS:
                    return "\u2605"; // ★
                case LOADING:
                    return "\u25cc"; // ◌
                default:
                    return "\u2022"; // •
            }
        }
        StatusFlag f = primaryFlag(status);
        if (f == StatusFlag.AFK) {
            return "\u23f8"; // ⏸
        }
        if (f == StatusFlag.UNFOCUSED) {
            return "\u25a1"; // □
        }
        if (f == StatusFlag.SOUND_OFF || f == StatusFlag.MIC_OFF) {
            return "\u2715"; // ✕
        }
        return "\u2022";
    }

    /** Computes the card layout for a status and its rendered text width. */
    public static CardLayout layout(PlayerStatus status, int textWidth, int opacityPct) {
        List<StatusFlag> extra = secondaryFlags(status);
        int extraWidth = extra.size() * (SPRITE_SIZE + 2);
        int innerWidth = 3 + SPRITE_SIZE + (textWidth > 0 ? 3 + textWidth : 0) + (extraWidth > 0 ? 2 + extraWidth : 0) + 3;
        float x0 = -innerWidth / 2.0f;
        float x1 = x0 + innerWidth;
        float textX = x0 + 3 + SPRITE_SIZE + 3;
        float textY = CARD_Y0 + 3;
        int alpha = Math.max(0, Math.min(255, Math.round(255f * opacityPct / 100f)));
        int textArgb = (alpha << 24) | 0xF5F7FA;
        return new CardLayout(x0, CARD_Y0, x1, CARD_Y1, textX, textY, accentRgb(status), textArgb, extra);
    }

    /**
     * Draws the overhead pill background, accent border, primary 10x10 pixel-art icon and any
     * secondary flag icons into {@code sink}, and returns the {@link CardLayout} for placing text.
     */
    public static CardLayout drawCard(RectSink sink, PlayerStatus status, int textWidth, int opacityPct, long nowMillis) {
        CardLayout l = layout(status, textWidth, opacityPct);
        int alpha = (l.textArgb >>> 24) & 0xff;
        if (alpha <= 0) {
            return l;
        }
        int bgAlpha = Math.min(255, Math.round(alpha * 0.86f));
        int borderAlpha = Math.min(255, Math.round(alpha * 0.95f));
        int bg = (bgAlpha << 24) | 0x12151E;
        int border = (borderAlpha << 24) | (l.accentRgb & 0xffffff);

        // Rounded pixel pill background
        sink.rect(l.x0 + 1, l.y0, l.x1 - 1, l.y1, bg);
        sink.rect(l.x0, l.y0 + 1, l.x0 + 1, l.y1 - 1, bg);
        sink.rect(l.x1 - 1, l.y0 + 1, l.x1, l.y1 - 1, bg);

        // Crisp 1px top/bottom/left accent frame
        sink.rect(l.x0 + 1, l.y0, l.x1 - 1, l.y0 + 1, border);
        sink.rect(l.x0 + 1, l.y1 - 1, l.x1 - 1, l.y1, border);
        sink.rect(l.x0, l.y0 + 1, l.x0 + 2, l.y1 - 1, border);
        sink.rect(l.x1 - 1, l.y0 + 1, l.x1, l.y1 - 1, border);

        // Primary 10x10 sprite
        float iconX = l.x0 + 3;
        float iconY = l.y0 + 2;
        primarySprite(status).draw(sink, iconX, iconY, 1.0f, alpha);

        // Subtle typing dot animation on top of the typing speech bubble
        if (status.activity() == Activity.TYPING) {
            int dot = (int) ((Math.max(0L, nowMillis) / 280L) % 3L);
            int highlight = (alpha << 24) | 0x4FC3F7;
            float dx = iconX + 2 + dot * 3;
            sink.rect(dx, iconY + 3, dx + 1, iconY + 5, highlight);
        }

        // Secondary flag sprites on the right side
        float fx = l.x1 - 3 - l.extraFlags.size() * (SPRITE_SIZE + 2) + 2;
        for (StatusFlag flag : l.extraFlags) {
            sprite(flag).draw(sink, fx, iconY, 1.0f, alpha);
            fx += SPRITE_SIZE + 2;
        }
        return l;
    }
}
