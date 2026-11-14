package com.kasper.playerstatuses;

/**
 * Pure-Java 3D math, analytical raycast and procedural 2D-in-3D layout geometry for
 * interactive Holo-Projections. Coordinate frame in panel space uses pixels across
 * {@code [-HALF_W, +HALF_W] x [-HALF_H, +HALF_H]} with {@code +X} right and {@code +Y} down.
 */
public final class HoloGeometry {

    public static final float PANEL_W = 116f;
    public static final float PANEL_H = 82f;
    public static final float HALF_W = PANEL_W * 0.5f;
    public static final float HALF_H = PANEL_H * 0.5f;

    public static final float FORWARD_DIST = 0.56f;
    public static final float HEIGHT_STANDING = 1.14f;
    public static final float HEIGHT_SNEAKING = 0.92f;
    public static final float TILT_DEG = 12f;
    public static final float PIXEL_SCALE = 0.0046f;

    public static final float CALL_BTN_X0 = -32f;
    public static final float CALL_BTN_Y0 = 12f;
    public static final float CALL_BTN_X1 = 32f;
    public static final float CALL_BTN_Y1 = 29f;
    public static final float CALL_TEXT_X = 0f;
    public static final float CALL_TEXT_Y = 17f;
    public static final float PAGE_TEXT_X = 0f;
    public static final float PAGE_TEXT_Y = 30f;

    public static final long UNFOLD_MS = 180L;
    public static final long PING_LIFETIME_MS = 4000L;
    public static final long KNOCK_LIFETIME_MS = 1200L;
    public static final long CLICK_PULSE_MS = 450L;

    private HoloGeometry() {
    }

    /** World/entity-relative orthonormal basis for a player's chest hologram plane. */
    public static final class Basis {
        public final double cx, cy, cz;
        public final double rx, ry, rz;
        public final double ux, uy, uz;
        public final double nx, ny, nz;

        public Basis(double cx, double cy, double cz,
                     double rx, double ry, double rz,
                     double ux, double uy, double uz,
                     double nx, double ny, double nz) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.ux = ux;
            this.uy = uy;
            this.uz = uz;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }

    /** Raycast hit against a hologram plane. */
    public static final class Hit {
        public final double distance;
        public final float u;
        public final float v;
        public final int hoveredSlot;

        public Hit(double distance, float u, float v, int hoveredSlot) {
            this.distance = distance;
            this.u = u;
            this.v = v;
            this.hoveredSlot = hoveredSlot;
        }
    }

    /** Axis-aligned rectangle in hologram pixel space. */
    public static final class Rect {
        public final float x0, y0, x1, y1;

        public Rect(float x0, float y0, float x1, float y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        public boolean contains(float px, float py, float margin) {
            return px >= x0 - margin && px <= x1 + margin && py >= y0 - margin && py <= y1 + margin;
        }
    }

    /** Builds the chest-anchored 3D plane basis for a player at {@code (px, py, pz)}. */
    public static Basis basisFor(double px, double py, double pz, float bodyYawDeg, boolean sneaking) {
        double yawRad = Math.toRadians(bodyYawDeg);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        // Observer facing -forward sees right = (-forward) x (+Y) = (+fz, 0, -fx).
        double rx = fz;
        double rz = -fx;

        double tiltRad = Math.toRadians(TILT_DEG);
        double cosT = Math.cos(tiltRad);
        double sinT = Math.sin(tiltRad);

        double nx = fx * cosT;
        double ny = sinT;
        double nz = fz * cosT;

        // Orthogonal upward vector on the tilted panel (normal x right).
        double ux = -fx * sinT;
        double uy = cosT;
        double uz = -fz * sinT;

        double height = sneaking ? HEIGHT_SNEAKING : HEIGHT_STANDING;
        double cx = px + fx * FORWARD_DIST;
        double cy = py + height;
        double cz = pz + fz * FORWARD_DIST;
        return new Basis(cx, cy, cz, rx, 0.0, rz, ux, uy, uz, nx, ny, nz);
    }

    public static float toU(float px) {
        return HoloState.clamp01((px + HALF_W) / PANEL_W);
    }

    public static float toV(float py) {
        return HoloState.clamp01((py + HALF_H) / PANEL_H);
    }

    public static float cursorPixelX(float u) {
        return -HALF_W + HoloState.clamp01(u) * PANEL_W;
    }

    public static float cursorPixelY(float v) {
        return -HALF_H + HoloState.clamp01(v) * PANEL_H;
    }

    /**
     * Intersects a view ray with the front side of a hologram plane and maps it to UV and
     * any interactive slot under the cursor.
     */
    public static Hit raycast(Basis b, double eyeX, double eyeY, double eyeZ,
                              double dirX, double dirY, double dirZ,
                              HoloState.Projection projection, double maxDist) {
        if (b == null || projection == null || projection.isNone()) {
            return null;
        }
        // Only interact from the front hemisphere so walking behind a player never steals clicks.
        double denom = dirX * b.nx + dirY * b.ny + dirZ * b.nz;
        if (denom >= -1e-4) {
            return null;
        }
        double ocX = b.cx - eyeX;
        double ocY = b.cy - eyeY;
        double ocZ = b.cz - eyeZ;
        double t = (ocX * b.nx + ocY * b.ny + ocZ * b.nz) / denom;
        if (t <= 0.25 || t > maxDist) {
            return null;
        }
        double hx = eyeX + t * dirX - b.cx;
        double hy = eyeY + t * dirY - b.cy;
        double hz = eyeZ + t * dirZ - b.cz;

        double lx = hx * b.rx + hy * b.ry + hz * b.rz;
        double ly = hx * b.ux + hy * b.uy + hz * b.uz;
        float px = (float) (lx / PIXEL_SCALE);
        float py = (float) (-ly / PIXEL_SCALE);
        if (px < -HALF_W - 2f || px > HALF_W + 2f || py < -HALF_H - 2f || py > HALF_H + 2f) {
            return null;
        }

        int hovered = -1;
        HoloState.LayoutKind layout = projection.layout();
        if (layout == HoloState.LayoutKind.IDLE_CARD) {
            if (px >= CALL_BTN_X0 - 1.5f && px <= CALL_BTN_X1 + 1.5f
                    && py >= CALL_BTN_Y0 - 1.5f && py <= CALL_BTN_Y1 + 1.5f) {
                hovered = HoloState.AFK_CALL_BUTTON_SLOT;
            }
        } else {
            int limit = maxInteractiveSlot(layout);
            for (int s = 0; s <= limit; s++) {
                Rect r = slotBounds(layout, s);
                if (r != null && r.contains(px, py, 0.8f)) {
                    hovered = s;
                    break;
                }
            }
        }
        return new Hit(t, toU(px), toV(py), hovered);
    }

    public static float unfoldFactor(long ageMs) {
        if (ageMs <= 0L) {
            return 0f;
        }
        if (ageMs >= UNFOLD_MS) {
            return 1f;
        }
        float t = ageMs / (float) UNFOLD_MS;
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    public static float wobbleAngleRad(long knockAgeMs) {
        if (knockAgeMs < 0L || knockAgeMs > KNOCK_LIFETIME_MS) {
            return 0f;
        }
        float t = knockAgeMs / 1000f;
        return (float) (Math.sin(t * Math.PI * 6.0) * Math.exp(-t * 3.8) * 0.22);
    }

    /** Highest interactive slot index displayed by a given layout, or {@code -1} if none. */
    public static int maxInteractiveSlot(HoloState.LayoutKind layout) {
        if (layout == null) {
            return -1;
        }
        switch (layout) {
            case INVENTORY:
                return 45;
            case CONTAINER_27:
                return 26;
            case CONTAINER_54:
                return 53;
            case CRAFTING:
                return 9;
            case FURNACE:
            case ANVIL:
            case TRADING:
                return 2;
            case ENCHANTING:
                return 1;
            case IDLE_CARD:
                return HoloState.AFK_CALL_BUTTON_SLOT;
            default:
                return -1;
        }
    }

    /** Pixel rectangle for slot {@code index} on {@code layout}, or {@code null} if not shown. */
    public static Rect slotBounds(HoloState.LayoutKind layout, int index) {
        if (layout == null || index < 0) {
            return null;
        }
        switch (layout) {
            case INVENTORY:
                return inventorySlot(index);
            case CONTAINER_27:
                return chest27Slot(index);
            case CONTAINER_54:
                return chest54Slot(index);
            case CRAFTING:
                return craftingSlot(index);
            case FURNACE:
                return furnaceSlot(index);
            case ENCHANTING:
                return enchantingSlot(index);
            case ANVIL:
                return anvilSlot(index);
            case TRADING:
                return tradingSlot(index);
            case IDLE_CARD:
                return index == HoloState.AFK_CALL_BUTTON_SLOT
                        ? new Rect(CALL_BTN_X0, CALL_BTN_Y0, CALL_BTN_X1, CALL_BTN_Y1)
                        : null;
            default:
                return null;
        }
    }

    private static Rect inventorySlot(int s) {
        if (s == 0) {
            return new Rect(41f, -21f, 51f, -11f);
        }
        if (s >= 1 && s <= 4) {
            int col = (s - 1) % 2;
            int row = (s - 1) / 2;
            float x = 12f + col * 10.5f;
            float y = -25f + row * 9f;
            return new Rect(x, y, x + 9f, y + 7.5f);
        }
        if (s >= 5 && s <= 8) {
            float x = -49f + (s - 5) * 10.5f;
            return new Rect(x, -24f, x + 9f, -16f);
        }
        if (s == 45) {
            return new Rect(-5f, -24f, 4f, -16f);
        }
        if (s >= 9 && s <= 35) {
            int col = (s - 9) % 9;
            int row = (s - 9) / 9;
            float x = -49.5f + col * 11f;
            float y = -3f + row * 9.5f;
            return new Rect(x, y, x + 9.5f, y + 8f);
        }
        if (s >= 36 && s <= 44) {
            int col = s - 36;
            float x = -49.5f + col * 11f;
            return new Rect(x, 28f, x + 9.5f, 36f);
        }
        return null;
    }

    private static Rect chest27Slot(int s) {
        if (s >= 27) {
            return null;
        }
        int col = s % 9;
        int row = s / 9;
        float x = -49.5f + col * 11f;
        float y = -17f + row * 12.5f;
        return new Rect(x, y, x + 9.5f, y + 10.5f);
    }

    private static Rect chest54Slot(int s) {
        if (s >= 54) {
            return null;
        }
        int col = s % 9;
        int row = s / 9;
        float x = -49.5f + col * 11f;
        float y = -24.5f + row * 10f;
        return new Rect(x, y, x + 9.5f, y + 8.5f);
    }

    private static Rect craftingSlot(int s) {
        if (s == 0) {
            return new Rect(27f, -6f, 44f, 11f);
        }
        if (s >= 1 && s <= 9) {
            int col = (s - 1) % 3;
            int row = (s - 1) / 3;
            float x = -42f + col * 15f;
            float y = -20f + row * 15f;
            return new Rect(x, y, x + 13f, y + 13f);
        }
        return null;
    }

    private static Rect furnaceSlot(int s) {
        if (s == 0) {
            return new Rect(-28f, -22f, -15f, -9f);
        }
        if (s == 1) {
            return new Rect(-28f, 9f, -15f, 22f);
        }
        if (s == 2) {
            return new Rect(21f, -8f, 37f, 8f);
        }
        return null;
    }

    private static Rect enchantingSlot(int s) {
        if (s == 0) {
            return new Rect(-42f, -12f, -28f, 2f);
        }
        if (s == 1) {
            return new Rect(-42f, 8f, -28f, 22f);
        }
        return null;
    }

    private static Rect anvilSlot(int s) {
        if (s == 0) {
            return new Rect(-44f, -7f, -30f, 7f);
        }
        if (s == 1) {
            return new Rect(-16f, -7f, -2f, 7f);
        }
        if (s == 2) {
            return new Rect(25f, -8f, 41f, 8f);
        }
        return null;
    }

    private static Rect tradingSlot(int s) {
        if (s == 0) {
            return new Rect(-10f, -7f, 3f, 6f);
        }
        if (s == 1) {
            return new Rect(6f, -7f, 19f, 6f);
        }
        if (s == 2) {
            return new Rect(34f, -8f, 49f, 7f);
        }
        return null;
    }

    /**
     * Emits the procedural 3D hologram panel into {@code sink}. Caller should configure pose
     * scaling and invoke once for the front pass ({@code opacityScale = 1f}) and optionally once
     * rotated 180° for the back pass ({@code opacityScale = 0.68f}).
     */
    public static void drawHologram(StatusIcon.RectSink sink, PlayerStatus status,
                                    HoloState.Projection projection, float opacityScale,
                                    int hoveredSlot, HoloState.Event activeEvent,
                                    long nowMs, long pulseAtMs, long eventAgeMs) {
        if (sink == null || projection == null || projection.isNone()) {
            return;
        }
        int baseAccent = StatusIcon.accentRgb(status == null ? PlayerStatus.NONE : status);
        int holoAccent = mixAccent(baseAccent, 0x38D9FF, 0.35f);

        drawBackdropAndFrame(sink, status, holoAccent, opacityScale, nowMs);
        HoloState.LayoutKind layout = projection.layout();

        int activePingSlot = (activeEvent != null && eventAgeMs >= 0L && eventAgeMs <= PING_LIFETIME_MS)
                ? activeEvent.slotIndex()
                : -99;
        float pingProgress = activePingSlot != -99 ? Math.min(1f, eventAgeMs / (float) PING_LIFETIME_MS) : 0f;

        if (maxInteractiveSlot(layout) >= 0 && layout != HoloState.LayoutKind.IDLE_CARD) {
            drawSlotLayout(sink, layout, projection, holoAccent, opacityScale, hoveredSlot,
                    activePingSlot, pingProgress, nowMs);
        } else {
            drawAmbientLayout(sink, layout, status, projection, holoAccent, opacityScale,
                    hoveredSlot, activePingSlot, pingProgress, nowMs);
        }

        if (layout != HoloState.LayoutKind.IDLE_CARD) {
            drawCursorAndPulse(sink, projection, holoAccent, opacityScale, nowMs, pulseAtMs);
        }
    }

    private static void drawBackdropAndFrame(StatusIcon.RectSink sink, PlayerStatus status,
                                             int accent, float opacity, long nowMs) {
        // Frosted dark glass panel + glowing chest anchor bar.
        sink.rect(-56f, -39f, 56f, 39f, argb(accent, opacity, 0.08f, 0x09131E));
        sink.rect(-55f, -38f, 55f, -28f, argb(accent, opacity, 0.16f, 0x0B1A2B));
        sink.rect(-55f, -28f, 55f, -27.2f, withAlpha(accent, opacity * 0.45f));

        // Miniature status icon tucked cleanly in header top-left.
        StatusIcon.Sprite sprite = StatusIcon.primarySprite(status == null ? PlayerStatus.NONE : status);
        if (sprite != null) {
            sprite.draw(sink, -51.5f, -37.2f, 0.78f, withAlpha(accent, opacity * 0.92f));
        }
        // Subtle status title bars next to sprite (abstract HUD indicator).
        sink.rect(-40f, -34.5f, -8f, -33.0f, withAlpha(accent, opacity * 0.65f));
        sink.rect(-40f, -31.2f, -20f, -30.2f, withAlpha(0xDCEBFF, opacity * 0.32f));
        // Window corner pill dots on top-right.
        sink.rect(38f, -34f, 41f, -31f, withAlpha(accent, opacity * 0.45f));
        sink.rect(44f, -34f, 47f, -31f, withAlpha(accent, opacity * 0.65f));
        sink.rect(50f, -34f, 53f, -31f, withAlpha(0xFF7070, opacity * 0.55f));

        // Corner L-brackets framing the holographic field.
        int cornerCol = withAlpha(accent, opacity * 0.88f);
        drawCornerBrackets(sink, cornerCol);

        // Moving subtle horizontal scanline across the glass.
        float scanPhase = ((nowMs % 2400L) / 2400f);
        float scanY = -38f + scanPhase * 75f;
        sink.rect(-55f, scanY, 55f, scanY + 1.2f, withAlpha(accent, opacity * 0.14f));

        // Chest emitter glow line at bottom center.
        sink.rect(-24f, 38.6f, 24f, 40.4f, withAlpha(accent, opacity * 0.70f));
        sink.rect(-12f, 39.8f, 12f, 41.2f, withAlpha(0xFFFFFF, opacity * 0.45f));
    }

    private static void drawCornerBrackets(StatusIcon.RectSink sink, int col) {
        // Top-left
        sink.rect(-57f, -40f, -47f, -38.6f, col);
        sink.rect(-57f, -40f, -55.6f, -30f, col);
        // Top-right
        sink.rect(47f, -40f, 57f, -38.6f, col);
        sink.rect(55.6f, -40f, 57f, -30f, col);
        // Bottom-left
        sink.rect(-57f, 38.6f, -47f, 40f, col);
        sink.rect(-57f, 30f, -55.6f, 40f, col);
        // Bottom-right
        sink.rect(47f, 38.6f, 57f, 40f, col);
        sink.rect(55.6f, 30f, 57f, 40f, col);
    }

    private static void drawSlotLayout(StatusIcon.RectSink sink, HoloState.LayoutKind layout,
                                       HoloState.Projection projection, int accent, float opacity,
                                       int hoveredSlot, int pingSlot, float pingProgress, long nowMs) {
        drawLayoutDecorations(sink, layout, projection, accent, opacity, nowMs);

        int limit = maxInteractiveSlot(layout);
        for (int s = 0; s <= limit; s++) {
            Rect r = slotBounds(layout, s);
            if (r == null) {
                continue;
            }
            int state = projection.slotState(s);
            boolean hovered = s == hoveredSlot;
            boolean hotbar = layout == HoloState.LayoutKind.INVENTORY && s >= 36 && s <= 44;
            drawOneSlot(sink, r, state, accent, opacity, hovered, hotbar, nowMs);
            if (s == pingSlot) {
                drawPingRipple(sink, r, pingProgress, opacity);
            }
        }
    }

    private static void drawLayoutDecorations(StatusIcon.RectSink sink, HoloState.LayoutKind layout,
                                              HoloState.Projection projection, int accent,
                                              float opacity, long nowMs) {
        switch (layout) {
            case INVENTORY:
                sink.rect(-50f, -6.2f, 50f, -5.4f, withAlpha(accent, opacity * 0.35f));
                drawArrow(sink, 32f, -16.5f, 38f, 1f, accent, opacity);
                break;
            case CRAFTING:
                drawArrow(sink, 7f, 1f, 22f, projection.progress() / 255f > 0 ? 1f : 0.7f, accent, opacity);
                break;
            case FURNACE: {
                float cook = projection.progress() / 255f;
                float lit = projection.aux() / 255f;
                drawArrow(sink, -8f, -1f, 15f, Math.max(0.15f, cook), accent, opacity);
                // Animated flame bar between input and fuel.
                sink.rect(-23.5f, -5f, -19.5f, 5f, withAlpha(0x30180A, opacity * 0.55f));
                if (lit > 0.02f) {
                    float h = 10f * lit;
                    float flicker = 0.88f + 0.12f * (float) Math.sin(nowMs * 0.015);
                    sink.rect(-23.5f, 5f - h, -19.5f, 5f, withAlpha(0xFF9933, opacity * flicker));
                }
                break;
            }
            case ENCHANTING:
                for (int i = 0; i < 3; i++) {
                    float y = -20f + i * 14f;
                    int tierCol = i == 2 ? 0x66EEFF : (i == 1 ? 0xB87BFF : 0x8C60CC);
                    sink.rect(-14f, y, 44f, y + 11f, withAlpha(0x140E28, opacity * 0.60f));
                    sink.rect(-14f, y, -12.5f, y + 11f, withAlpha(tierCol, opacity * 0.85f));
                    sink.rect(-9f, y + 3.5f, 24f + i * 6f, y + 5.5f, withAlpha(tierCol, opacity * 0.65f));
                    sink.rect(-9f, y + 7f, 12f + i * 5f, y + 8.5f, withAlpha(0xFFFFFF, opacity * 0.30f));
                }
                break;
            case ANVIL:
                sink.rect(-26f, -1f, -20f, 1f, withAlpha(accent, opacity * 0.75f));
                sink.rect(-24f, -3f, -22f, 3f, withAlpha(accent, opacity * 0.75f));
                drawArrow(sink, 5f, 0f, 19f, 1f, accent, opacity);
                sink.rect(-44f, 16f, 41f, 19f, withAlpha(0x55FF88, opacity * 0.35f));
                break;
            case TRADING:
                for (int i = 0; i < 3; i++) {
                    float y = -18f + i * 14f;
                    sink.rect(-48f, y, -20f, y + 10f, withAlpha(0x11241E, opacity * 0.62f));
                    sink.rect(-45f, y + 3f, -38f, y + 7f, withAlpha(0x44EE88, opacity * 0.75f));
                    sink.rect(-34f, y + 4f, -23f, y + 6f, withAlpha(0xD8E8FF, opacity * 0.45f));
                }
                drawArrow(sink, 22f, -0.5f, 31f, 1f, 0x44EE88, opacity);
                break;
            default:
                break;
        }
    }

    private static void drawArrow(StatusIcon.RectSink sink, float x0, float yCenter, float x1,
                                  float fillRatio, int accent, float opacity) {
        float midX = x0 + (x1 - x0) * HoloState.clamp01(fillRatio);
        sink.rect(x0, yCenter - 1.4f, x1, yCenter + 1.4f, withAlpha(0xFFFFFF, opacity * 0.22f));
        sink.rect(x0, yCenter - 1.4f, midX, yCenter + 1.4f, withAlpha(accent, opacity * 0.82f));
        sink.rect(x1 - 3f, yCenter - 3.5f, x1 - 1f, yCenter + 3.5f, withAlpha(accent, opacity * 0.65f));
        sink.rect(x1 - 1f, yCenter - 1.8f, x1 + 1f, yCenter + 1.8f, withAlpha(accent, opacity * 0.88f));
    }

    private static void drawOneSlot(StatusIcon.RectSink sink, Rect r, int state, int accent,
                                    float opacity, boolean hovered, boolean hotbar, long nowMs) {
        int bgCol = hovered ? withAlpha(accent, opacity * 0.38f)
                : withAlpha(0x0D1B2A, opacity * (hotbar ? 0.74f : 0.56f));
        sink.rect(r.x0, r.y0, r.x1, r.y1, bgCol);

        int borderCol = hovered ? withAlpha(0xFFFFFF, opacity * 0.96f)
                : withAlpha(accent, opacity * (hotbar ? 0.68f : 0.42f));
        float t = hovered ? 1.1f : 0.7f;
        sink.rect(r.x0, r.y0, r.x1, r.y0 + t, borderCol);
        sink.rect(r.x0, r.y1 - t, r.x1, r.y1, borderCol);
        sink.rect(r.x0, r.y0, r.x0 + t, r.y1, borderCol);
        sink.rect(r.x1 - t, r.y0, r.x1, r.y1, borderCol);

        if (state == HoloState.SLOT_EMPTY) {
            return;
        }
        float pad = Math.min(2.1f, (r.x1 - r.x0) * 0.22f);
        float ix0 = r.x0 + pad;
        float iy0 = r.y0 + pad;
        float ix1 = r.x1 - pad;
        float iy1 = r.y1 - pad;

        if (state == HoloState.SLOT_COMMON) {
            sink.rect(ix0, iy0, ix1, iy1, withAlpha(0xCFE8FF, opacity * 0.80f));
        } else if (state == HoloState.SLOT_UNCOMMON) {
            sink.rect(ix0, iy0, ix1, iy1, withAlpha(0x9EF7C4, opacity * 0.85f));
            sink.rect(ix0, iy1 - 1.2f, ix1, iy1, withAlpha(0x44DD88, opacity * 0.92f));
        } else if (state == HoloState.SLOT_ENCHANTED) {
            float shimmer = 0.70f + 0.25f * (float) Math.sin(nowMs * 0.008 + r.x0 * 0.2f);
            sink.rect(ix0, iy0, ix1, iy1, withAlpha(0xD888FF, opacity * shimmer));
            sink.rect(ix0 + 1f, iy0 + 1f, ix1 - 1f, iy0 + 2.2f, withAlpha(0x77FFFF, opacity * 0.9f));
        } else {
            sink.rect(ix0, iy0, ix1, iy1, withAlpha(0xFFE799, opacity * 0.88f));
            sink.rect(ix0 + 1.2f, iy1 - 1.8f, ix1, iy1, withAlpha(0xFFBB33, opacity * 0.95f));
        }
    }

    private static void drawAmbientLayout(StatusIcon.RectSink sink, HoloState.LayoutKind layout,
                                          PlayerStatus status, HoloState.Projection projection,
                                          int accent, float opacity, int hoveredSlot,
                                          int pingSlot, float pingProgress, long nowMs) {
        switch (layout) {
            case TYPING: {
                // Faint redacted message lines above an active chat input box.
                sink.rect(-48f, -22f, 18f, -18f, withAlpha(accent, opacity * 0.28f));
                sink.rect(-48f, -15f, 34f, -11f, withAlpha(accent, opacity * 0.22f));
                sink.rect(-48f, -4f, 48f, 22f, withAlpha(0x0A1522, opacity * 0.65f));
                sink.rect(-48f, -4f, 48f, -3f, withAlpha(accent, opacity * 0.72f));
                sink.rect(-48f, 21f, 48f, 22f, withAlpha(accent, opacity * 0.72f));
                boolean isCommand = projection.aux() != 0;
                int promptCol = withAlpha(isCommand ? 0xFFD060 : accent, opacity * 0.92f);
                sink.rect(-42f, 5f, -38f, 13f, promptCol);
                // Bouncing ellipsis cadence dots (never actual chat text).
                for (int i = 0; i < 3; i++) {
                    double wave = Math.sin(nowMs * 0.008 - i * 0.9);
                    float dy = (float) Math.max(0.0, wave) * -4.2f;
                    float dx = -12f + i * 12f;
                    sink.rect(dx, 6f + dy, dx + 5f, 11f + dy, withAlpha(0xFFFFFF, opacity * 0.88f));
                }
                break;
            }
            case BOOK: {
                sink.rect(-46f, -22f, -2f, 26f, withAlpha(0xF0E6CC, opacity * 0.20f));
                sink.rect(2f, -22f, 46f, 26f, withAlpha(0xF0E6CC, opacity * 0.24f));
                sink.rect(-0.8f, -22f, 0.8f, 26f, withAlpha(accent, opacity * 0.65f));
                int page = projection.progress();
                for (int row = 0; row < 5; row++) {
                    float y = -16f + row * 7.5f;
                    float wL = 28f + ((row * 7 + page) % 10);
                    float wR = 24f + ((row * 11 + page) % 12);
                    sink.rect(-41f, y, -41f + wL, y + 2.2f, withAlpha(accent, opacity * 0.45f));
                    sink.rect(7f, y, 7f + wR, y + 2.2f, withAlpha(accent, opacity * 0.45f));
                }
                break;
            }
            case SIGN: {
                sink.rect(-44f, -18f, 44f, 20f, withAlpha(0x7B542E, opacity * 0.48f));
                sink.rect(-44f, -18f, 44f, -16.5f, withAlpha(0xD8A866, opacity * 0.72f));
                sink.rect(-44f, 18.5f, 44f, 20f, withAlpha(0xD8A866, opacity * 0.72f));
                for (int row = 0; row < 4; row++) {
                    float y = -12f + row * 7.5f;
                    sink.rect(-36f, y, 22f + (row % 2) * 10f, y + 2.5f,
                            withAlpha(0xFFE9C2, opacity * 0.55f));
                }
                if ((nowMs / 450L) % 2L == 0L) {
                    sink.rect(25f, 10f, 27f, 16f, withAlpha(0xFFFFFF, opacity * 0.85f));
                }
                break;
            }
            case MAP: {
                sink.rect(-38f, -22f, 38f, 30f, withAlpha(0x3B5440, opacity * 0.42f));
                for (int i = -24; i <= 24; i += 16) {
                    sink.rect(i, -22f, i + 0.7f, 30f, withAlpha(accent, opacity * 0.25f));
                }
                for (int j = -10; j <= 18; j += 12) {
                    sink.rect(-38f, j, 38f, j + 0.7f, withAlpha(accent, opacity * 0.25f));
                }
                sink.rect(-26f, -12f, -4f, 8f, withAlpha(0x44BBFF, opacity * 0.45f));
                sink.rect(4f, -4f, 28f, 18f, withAlpha(0x66DD88, opacity * 0.45f));
                break;
            }
            case IDLE_CARD: {
                StatusIcon.Sprite moon = StatusIcon.sprite(StatusFlag.AFK);
                if (moon != null) {
                    moon.draw(sink, -36f, -14f, 1.8f, withAlpha(0x88BBFF, opacity * 0.92f));
                }
                sink.rect(-10f, -10f, 34f, -7f, withAlpha(0xB8D8FF, opacity * 0.72f));
                sink.rect(-10f, -3f, 22f, -0.5f, withAlpha(0xB8D8FF, opacity * 0.45f));

                // Interactive [ Call ] button.
                boolean hovered = hoveredSlot == HoloState.AFK_CALL_BUTTON_SLOT;
                int btnBg = hovered ? withAlpha(0xFFBB33, opacity * 0.52f)
                        : withAlpha(0x281D0A, opacity * 0.75f);
                int btnBorder = hovered ? withAlpha(0xFFFFFF, opacity * 0.98f)
                        : withAlpha(0xFFCC55, opacity * 0.85f);
                sink.rect(CALL_BTN_X0, CALL_BTN_Y0, CALL_BTN_X1, CALL_BTN_Y1, btnBg);
                sink.rect(CALL_BTN_X0, CALL_BTN_Y0, CALL_BTN_X1, CALL_BTN_Y0 + 1.1f, btnBorder);
                sink.rect(CALL_BTN_X0, CALL_BTN_Y1 - 1.1f, CALL_BTN_X1, CALL_BTN_Y1, btnBorder);
                sink.rect(CALL_BTN_X0, CALL_BTN_Y0, CALL_BTN_X0 + 1.1f, CALL_BTN_Y1, btnBorder);
                sink.rect(CALL_BTN_X1 - 1.1f, CALL_BTN_Y0, CALL_BTN_X1, CALL_BTN_Y1, btnBorder);
                if (pingSlot == HoloState.AFK_CALL_BUTTON_SLOT) {
                    drawPingRipple(sink, new Rect(CALL_BTN_X0, CALL_BTN_Y0, CALL_BTN_X1, CALL_BTN_Y1),
                            pingProgress, opacity);
                }
                break;
            }
            default: {
                sink.rect(-44f, -18f, -12f, 22f, withAlpha(accent, opacity * 0.25f));
                sink.rect(-6f, -18f, 44f, -2f, withAlpha(accent, opacity * 0.30f));
                sink.rect(-6f, 4f, 44f, 22f, withAlpha(accent, opacity * 0.22f));
                break;
            }
        }
    }

    private static void drawCursorAndPulse(StatusIcon.RectSink sink, HoloState.Projection projection,
                                           int accent, float opacity, long nowMs, long pulseAtMs) {
        float cx = Math.max(-54f, Math.min(52f, cursorPixelX(projection.cursorU())));
        float cy = Math.max(-36f, Math.min(35f, cursorPixelY(projection.cursorV())));

        long pulseAge = nowMs - pulseAtMs;
        if (pulseAtMs > 0L && pulseAge >= 0L && pulseAge <= CLICK_PULSE_MS) {
            float t = pulseAge / (float) CLICK_PULSE_MS;
            float r = 2.5f + t * 6.5f;
            float a = (1f - t) * 0.85f * opacity;
            int col = withAlpha(0xFFFFFF, a);
            sink.rect(cx - r, cy - r, cx + r, cy - r + 0.9f, col);
            sink.rect(cx - r, cy + r - 0.9f, cx + r, cy + r, col);
            sink.rect(cx - r, cy - r, cx - r + 0.9f, cy + r, col);
            sink.rect(cx + r - 0.9f, cy - r, cx + r, cy + r, col);
        }

        // Crisp 2-part holographic cursor arrow.
        sink.rect(cx - 0.6f, cy - 0.6f, cx + 3.2f, cy + 3.2f, withAlpha(0x0B1520, opacity * 0.7f));
        sink.rect(cx, cy, cx + 2.4f, cy + 2.4f, withAlpha(0xFFFFFF, opacity * 0.96f));
        sink.rect(cx + 1.8f, cy + 1.8f, cx + 3.6f, cy + 3.6f, withAlpha(accent, opacity * 0.92f));
    }

    private static void drawPingRipple(StatusIcon.RectSink sink, Rect r, float progress, float opacity) {
        float pulse = 0.55f + 0.45f * (float) Math.sin(progress * Math.PI * 8.0);
        float expand = 1.6f + (progress * 3.0f) % 2.5f;
        int col = withAlpha(0xFFDD44, opacity * pulse * (1f - progress * 0.35f));
        sink.rect(r.x0 - expand, r.y0 - expand, r.x1 + expand, r.y0 - expand + 1.2f, col);
        sink.rect(r.x0 - expand, r.y1 + expand - 1.2f, r.x1 + expand, r.y1 + expand, col);
        sink.rect(r.x0 - expand, r.y0 - expand, r.x0 - expand + 1.2f, r.y1 + expand, col);
        sink.rect(r.x1 + expand - 1.2f, r.y0 - expand, r.x1 + expand, r.y1 + expand, col);
    }

    private static int mixAccent(int a, int b, float ratioB) {
        float ratioA = 1f - ratioB;
        int r = (int) (((a >> 16) & 0xFF) * ratioA + ((b >> 16) & 0xFF) * ratioB);
        int g = (int) (((a >> 8) & 0xFF) * ratioA + ((b >> 8) & 0xFF) * ratioB);
        int bl = (int) ((a & 0xFF) * ratioA + (b & 0xFF) * ratioB);
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (bl & 0xFF);
    }

    private static int argb(int accentRgb, float opacity, float tintWeight, int darkRgb) {
        int mixed = mixAccent(darkRgb, accentRgb, tintWeight);
        return withAlpha(mixed, opacity);
    }

    private static int withAlpha(int rgb, float alpha01) {
        int a = Math.max(0, Math.min(255, Math.round(alpha01 * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
