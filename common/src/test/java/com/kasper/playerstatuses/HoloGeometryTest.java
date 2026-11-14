package com.kasper.playerstatuses;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HoloGeometryTest {

    @Test
    void basisIsOrthonormalAcrossYawsAndHeights() {
        float[] yaws = {0f, 45f, 90f, 180f, -90f, -135f};
        for (float yaw : yaws) {
            HoloGeometry.Basis standing = HoloGeometry.basisFor(10.0, 64.0, -5.0, yaw, false);
            HoloGeometry.Basis sneaking = HoloGeometry.basisFor(10.0, 64.0, -5.0, yaw, true);
            assertEquals(HoloGeometry.HEIGHT_STANDING, standing.cy - 64.0, 1e-6);
            assertEquals(HoloGeometry.HEIGHT_SNEAKING, sneaking.cy - 64.0, 1e-6);

            double rLen = Math.hypot(standing.rx, Math.hypot(standing.ry, standing.rz));
            double uLen = Math.hypot(standing.ux, Math.hypot(standing.uy, standing.uz));
            double nLen = Math.hypot(standing.nx, Math.hypot(standing.ny, standing.nz));
            assertEquals(1.0, rLen, 1e-6);
            assertEquals(1.0, uLen, 1e-6);
            assertEquals(1.0, nLen, 1e-6);

            double ru = standing.rx * standing.ux + standing.ry * standing.uy + standing.rz * standing.uz;
            double rn = standing.rx * standing.nx + standing.ry * standing.ny + standing.rz * standing.nz;
            double un = standing.ux * standing.nx + standing.uy * standing.ny + standing.uz * standing.nz;
            assertEquals(0.0, ru, 1e-6);
            assertEquals(0.0, rn, 1e-6);
            assertEquals(0.0, un, 1e-6);
        }
    }

    @Test
    void raycastRoundTripsInteractiveSlotsAndCallButton() {
        HoloGeometry.Basis basis = HoloGeometry.basisFor(0.0, 64.0, 0.0, 0f, false);
        HoloState.Projection crafting = new HoloState.Projection(
                HoloState.LayoutKind.CRAFTING, new byte[10], 0.5f, 0.5f, 0, 0, 0);

        for (int slot = 0; slot <= 9; slot++) {
            HoloGeometry.Rect rect = HoloGeometry.slotBounds(HoloState.LayoutKind.CRAFTING, slot);
            assertNotNull(rect);
            float px = (rect.x0 + rect.x1) * 0.5f;
            float py = (rect.y0 + rect.y1) * 0.5f;
            HoloGeometry.Hit hit = castAtPixel(basis, px, py, crafting, 4.5);
            assertNotNull(hit, "Missing raycast hit on slot " + slot);
            assertEquals(slot, hit.hoveredSlot);
        }

        // Aiming from behind the player must never steal clicks
        HoloGeometry.Hit fromBehind = HoloGeometry.raycast(
                basis, 0.0, basis.cy, -2.5, 0.0, 0.0, 1.0, crafting, 4.5);
        assertNull(fromBehind);

        // Beyond maxDist must miss
        HoloGeometry.Hit tooFar = HoloGeometry.raycast(
                basis, 0.0, basis.cy, 6.0, 0.0, -0.2, -1.0, crafting, 4.5);
        assertNull(tooFar);

        // AFK Call button hit test
        HoloState.Projection afk = new HoloState.Projection(
                HoloState.LayoutKind.IDLE_CARD, new byte[0], 0.5f, 0.5f, 0, 0, 0);
        HoloGeometry.Hit callHit = castAtPixel(basis, HoloGeometry.CALL_TEXT_X, HoloGeometry.CALL_TEXT_Y + 3f, afk, 4.5);
        assertNotNull(callHit);
        assertEquals(HoloState.AFK_CALL_BUTTON_SLOT, callHit.hoveredSlot);
    }

    private static HoloGeometry.Hit castAtPixel(HoloGeometry.Basis b, float px, float py,
                                                HoloState.Projection proj, double maxDist) {
        double wx = b.cx + b.rx * (px * HoloGeometry.PIXEL_SCALE) + b.ux * (-py * HoloGeometry.PIXEL_SCALE);
        double wy = b.cy + b.ry * (px * HoloGeometry.PIXEL_SCALE) + b.uy * (-py * HoloGeometry.PIXEL_SCALE);
        double wz = b.cz + b.rz * (px * HoloGeometry.PIXEL_SCALE) + b.uz * (-py * HoloGeometry.PIXEL_SCALE);
        double eyeX = b.cx + b.nx * 2.2;
        double eyeY = b.cy + b.ny * 2.2;
        double eyeZ = b.cz + b.nz * 2.2;
        double dx = wx - eyeX;
        double dy = wy - eyeY;
        double dz = wz - eyeZ;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return HoloGeometry.raycast(b, eyeX, eyeY, eyeZ, dx / len, dy / len, dz / len, proj, maxDist);
    }

    @Test
    void everySlotLayoutAndDrawCallStaysWithinPanelBounds() {
        for (HoloState.LayoutKind kind : HoloState.LayoutKind.values()) {
            if (kind.isNone()) {
                continue;
            }
            int limit = HoloGeometry.maxInteractiveSlot(kind);
            if (kind != HoloState.LayoutKind.IDLE_CARD && limit >= 0) {
                for (int s = 0; s <= limit; s++) {
                    HoloGeometry.Rect r = HoloGeometry.slotBounds(kind, s);
                    assertNotNull(r, kind + " missing slot " + s);
                    assertTrue(r.x0 >= -HoloGeometry.HALF_W && r.x1 <= HoloGeometry.HALF_W);
                    assertTrue(r.y0 >= -HoloGeometry.HALF_H && r.y1 <= HoloGeometry.HALF_H);
                }
            }

            byte[] slots = new byte[Math.max(0, Math.min(limit + 1, HoloState.MAX_SLOTS))];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = (byte) (i % 5);
            }
            HoloState.Projection proj = new HoloState.Projection(kind, slots, 0.35f, 0.65f, 3, 128, 255);
            List<float[]> emitted = new ArrayList<>();
            HoloState.Event event = new HoloState.Event(
                    new UUID(1, 1), new UUID(2, 2),
                    kind == HoloState.LayoutKind.IDLE_CARD ? HoloState.Action.KNOCK_AFK : HoloState.Action.PING_SLOT,
                    kind == HoloState.LayoutKind.IDLE_CARD ? HoloState.AFK_CALL_BUTTON_SLOT : Math.min(0, limit),
                    "", 500L);
            HoloGeometry.drawHologram(
                    (x0, y0, x1, y1, argb) -> {
                        assertFalse(Float.isNaN(x0) || Float.isNaN(y0) || Float.isNaN(x1) || Float.isNaN(y1));
                        emitted.add(new float[]{x0, y0, x1, y1});
                    },
                    PlayerStatus.of(Activity.CRAFTING),
                    proj, 1f, 0, event, 1000L, 900L, 500L);
            assertFalse(emitted.isEmpty(), "No quads emitted for " + kind);
        }

        assertEquals(0f, HoloGeometry.unfoldFactor(0L), 1e-6f);
        assertEquals(1f, HoloGeometry.unfoldFactor(HoloGeometry.UNFOLD_MS), 1e-6f);
        assertEquals(0f, HoloGeometry.wobbleAngleRad(-1L), 1e-6f);
        assertNotEquals(0f, HoloGeometry.wobbleAngleRad(80L));
        assertEquals(0f, HoloGeometry.wobbleAngleRad(HoloGeometry.KNOCK_LIFETIME_MS + 10L), 1e-6f);
    }
}
