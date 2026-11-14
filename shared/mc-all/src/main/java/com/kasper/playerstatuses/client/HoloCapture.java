package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.Activity;
import com.kasper.playerstatuses.HoloGeometry;
import com.kasper.playerstatuses.HoloState;
import com.kasper.playerstatuses.PlayerStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures anonymous menu state, container geometry, held item hints,
 * and 2D screen ping overlay visuals without touching any obfuscated private fields directly.
 */
public final class HoloCapture {

    public interface RectIntSink {
        void fill(int x0, int y0, int x1, int y1, int argb);
    }

    private static final Field FURNACE_DATA_FIELD = findFurnaceDataField();
    private static final BoundsFields BOUNDS_FIELDS = resolveBoundsFields();

    private static boolean lastMouseDown;
    private static int localPulseSeq;

    private HoloCapture() {
    }

    public static void resetPulse() {
        lastMouseDown = false;
        localPulseSeq = 0;
    }

    public static String heldGhostHint(LocalPlayer player) {
        if (player == null) {
            return "";
        }
        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            return "";
        }
        return HoloState.sanitizeDescId(held.getItem().getDescriptionId());
    }

    public static HoloState.Projection capture(Minecraft mc, PlayerStatus own, boolean includeSlots) {
        if (mc == null || mc.player == null || own == null) {
            return HoloState.Projection.EMPTY;
        }
        Screen screen = ClientAccess.screen(mc);
        boolean mouseDown = screen != null
            && mc.mouseHandler != null
            && (mc.mouseHandler.isLeftPressed() || mc.mouseHandler.isRightPressed());
        if (mouseDown && !lastMouseDown) {
            localPulseSeq = (localPulseSeq + 1) & 0xFFFF;
        }
        lastMouseDown = mouseDown;

        if (screen instanceof AbstractContainerScreen<?>) {
            AbstractContainerScreen<?> acs = (AbstractContainerScreen<?>) screen;
            AbstractContainerMenu menu = acs.getMenu();
            if (menu == null) {
                return HoloState.Projection.EMPTY;
            }
            HoloState.LayoutKind layout = detectContainerLayout(menu, own);
            if (layout.isNone()) {
                return HoloState.Projection.EMPTY;
            }

            int[] bounds = containerBounds(acs);
            int maxSlot = HoloGeometry.maxInteractiveSlot(layout);
            int slotLimit = Math.min(Math.max(0, maxSlot + 1), Math.min(HoloState.MAX_SLOTS, menu.slots.size()));

            byte[] slots;
            if (includeSlots && maxSlot >= 0 && slotLimit > 0) {
                slots = new byte[slotLimit];
                for (int i = 0; i < slotLimit; i++) {
                    Slot slot = menu.slots.get(i);
                    ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
                    slots[i] = (byte) encodeSlotState(slot, stack);
                }
            } else {
                slots = new byte[0];
            }

            float[] uv = computeContainerCursorUv(mc, acs, menu, bounds, maxSlot);
            int progress = 0;
            int aux = 0;
            if (menu instanceof AbstractFurnaceMenu) {
                int[] furnace = readFurnaceData((AbstractFurnaceMenu) menu);
                progress = furnace[0];
                aux = furnace[1];
            } else if (layout == HoloState.LayoutKind.CRAFTING) {
                aux = hasItem(menu, 0) ? 1 : 0;
            } else if (layout == HoloState.LayoutKind.ANVIL || layout == HoloState.LayoutKind.TRADING) {
                aux = hasItem(menu, 2) ? 1 : 0;
            }

            return new HoloState.Projection(layout, slots, uv[0], uv[1], localPulseSeq, progress, aux);
        }

        HoloState.LayoutKind fallback = HoloState.LayoutKind.defaultFor(own);
        if (fallback.isNone()) {
            return HoloState.Projection.EMPTY;
        }
        float u = 0.5f;
        float v = 0.5f;
        if (screen != null && mc.mouseHandler != null && mc.getWindow() != null) {
            double mx = mc.mouseHandler.xpos() * (double) Math.max(1, screen.width)
                / Math.max(1, mc.getWindow().getScreenWidth());
            double my = mc.mouseHandler.ypos() * (double) Math.max(1, screen.height)
                / Math.max(1, mc.getWindow().getScreenHeight());
            u = HoloState.clamp01((float) (mx / Math.max(1, screen.width)));
            v = HoloState.clamp01((float) (my / Math.max(1, screen.height)));
        }
        int aux = (fallback == HoloState.LayoutKind.TYPING && own.activity() == Activity.COMMAND) ? 1 : 0;
        return new HoloState.Projection(fallback, new byte[0], u, v, localPulseSeq, 0, aux);
    }

    private static HoloState.LayoutKind detectContainerLayout(AbstractContainerMenu menu, PlayerStatus own) {
        if (menu instanceof InventoryMenu) {
            return HoloState.LayoutKind.INVENTORY;
        }
        if (menu instanceof ChestMenu) {
            return menu.slots.size() >= 80
                ? HoloState.LayoutKind.CONTAINER_54
                : HoloState.LayoutKind.CONTAINER_27;
        }
        if (menu instanceof ShulkerBoxMenu) {
            return HoloState.LayoutKind.CONTAINER_27;
        }
        if (menu instanceof CraftingMenu) {
            return HoloState.LayoutKind.CRAFTING;
        }
        if (menu instanceof AbstractFurnaceMenu) {
            return HoloState.LayoutKind.FURNACE;
        }
        if (menu instanceof EnchantmentMenu) {
            return HoloState.LayoutKind.ENCHANTING;
        }
        if (menu instanceof AnvilMenu) {
            return HoloState.LayoutKind.ANVIL;
        }
        if (menu instanceof MerchantMenu) {
            return HoloState.LayoutKind.TRADING;
        }
        HoloState.LayoutKind fromStatus = HoloState.LayoutKind.defaultFor(own);
        return fromStatus.isNone() ? HoloState.LayoutKind.GENERIC_PANEL : fromStatus;
    }

    private static boolean hasItem(AbstractContainerMenu menu, int index) {
        if (index < 0 || index >= menu.slots.size()) {
            return false;
        }
        Slot s = menu.slots.get(index);
        return s != null && !s.getItem().isEmpty();
    }

    private static int encodeSlotState(Slot slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return HoloState.SLOT_EMPTY;
        }
        if (stack.isEnchanted()) {
            return HoloState.SLOT_ENCHANTED;
        }
        int maxStack = slot == null ? 64 : slot.getMaxStackSize(stack);
        if (stack.isStackable() && maxStack > 1 && stack.getCount() >= maxStack) {
            return HoloState.SLOT_FULL_STACK;
        }
        if (stack.isDamageableItem() || stack.isDamaged() || !stack.isStackable()) {
            return HoloState.SLOT_UNCOMMON;
        }
        return HoloState.SLOT_COMMON;
    }

    private static float[] computeContainerCursorUv(
        Minecraft mc,
        AbstractContainerScreen<?> screen,
        AbstractContainerMenu menu,
        int[] bounds,
        int maxInteractive
    ) {
        if (mc.mouseHandler == null || mc.getWindow() == null) {
            return new float[]{0.5f, 0.5f};
        }
        double mx = mc.mouseHandler.xpos() * (double) Math.max(1, screen.width)
            / Math.max(1, mc.getWindow().getScreenWidth());
        double my = mc.mouseHandler.ypos() * (double) Math.max(1, screen.height)
            / Math.max(1, mc.getWindow().getScreenHeight());

        double x0 = bounds[0];
        double y0 = bounds[1];
        double x1 = x0 + Math.max(1, bounds[2]);
        double y1 = y0 + Math.max(1, bounds[3]);

        if (maxInteractive >= 1 && !menu.slots.isEmpty()) {
            int limit = Math.min(maxInteractive + 1, menu.slots.size());
            int minSx = Integer.MAX_VALUE;
            int minSy = Integer.MAX_VALUE;
            int maxSx = Integer.MIN_VALUE;
            int maxSy = Integer.MIN_VALUE;
            int count = 0;
            for (int i = 0; i < limit; i++) {
                Slot slot = menu.slots.get(i);
                if (slot == null) {
                    continue;
                }
                minSx = Math.min(minSx, slot.x);
                minSy = Math.min(minSy, slot.y);
                maxSx = Math.max(maxSx, slot.x + 16);
                maxSy = Math.max(maxSy, slot.y + 16);
                count++;
            }
            if (count >= 2 && maxSx > minSx && maxSy > minSy) {
                x0 = bounds[0] + minSx - 8.0;
                y0 = bounds[1] + minSy - 8.0;
                x1 = bounds[0] + maxSx + 8.0;
                y1 = bounds[1] + maxSy + 8.0;
            }
        }

        float u = HoloState.clamp01((float) ((mx - x0) / Math.max(1.0, x1 - x0)));
        float v = HoloState.clamp01((float) ((my - y0) / Math.max(1.0, y1 - y0)));
        return new float[]{u, v};
    }

    public static int[] containerBounds(AbstractContainerScreen<?> screen) {
        if (screen == null) {
            return new int[]{0, 0, 176, 166};
        }
        int width = readIntField(BOUNDS_FIELDS.imageWidth, screen);
        int height = readIntField(BOUNDS_FIELDS.imageHeight, screen);
        if (width <= 0 || height <= 0) {
            int[] slotExtent = estimateSizeFromSlots(screen.getMenu());
            if (width <= 0) {
                width = slotExtent[0];
            }
            if (height <= 0) {
                height = slotExtent[1];
            }
        }
        int left = readIntField(BOUNDS_FIELDS.leftPos, screen);
        int top = readIntField(BOUNDS_FIELDS.topPos, screen);
        if (left == Integer.MIN_VALUE) {
            left = (screen.width - width) / 2;
        }
        if (top == Integer.MIN_VALUE) {
            top = (screen.height - height) / 2;
        }
        return new int[]{left, top, width, height};
    }

    public static int[] slotScreenCoords(AbstractContainerScreen<?> screen, int holoSlotIndex) {
        if (screen == null || holoSlotIndex < 0) {
            return null;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null || holoSlotIndex >= menu.slots.size()) {
            return null;
        }
        HoloState.LayoutKind layout = detectContainerLayout(menu, PlayerStatus.NONE);
        int limit = HoloGeometry.maxInteractiveSlot(layout);
        if (limit < 0 || holoSlotIndex > limit) {
            return null;
        }
        Slot slot = menu.slots.get(holoSlotIndex);
        if (slot == null) {
            return null;
        }
        int[] bounds = containerBounds(screen);
        return new int[]{bounds[0] + slot.x, bounds[1] + slot.y};
    }

    public static Component pingBannerComponent(Minecraft mc, HoloState.Event event) {
        String senderName = ClientSide.playerName(mc, event.sender());
        if (event.action() == HoloState.Action.KNOCK_AFK) {
            return StatusText.holoKnockReceived(senderName);
        }
        return StatusText.holoPingBanner(senderName, event.ghostItemDescId());
    }

    public static int[] bannerGeometry(AbstractContainerScreen<?> screen, int textWidth) {
        int[] bounds = containerBounds(screen);
        int w = Math.max(96, textWidth + 18);
        int h = 16;
        int centerX = bounds[0] + bounds[2] / 2;
        int x = Math.max(4, Math.min(screen.width - w - 4, centerX - w / 2));
        int y = Math.max(4, bounds[1] - h - 4);
        return new int[]{x, y, w, h};
    }

    public static void drawSlotPingRects(RectIntSink sink, int sx, int sy, int accentRgb, long ageMs) {
        if (sink == null || ageMs < 0L || ageMs >= HoloGeometry.PING_LIFETIME_MS) {
            return;
        }
        float fade = 1.0f;
        long remain = HoloGeometry.PING_LIFETIME_MS - ageMs;
        if (remain < 600L) {
            fade = Math.max(0f, remain / 600.0f);
        }
        float pulse = 0.65f + 0.35f * (float) Math.sin(ageMs * 0.011);

        int tintAlpha = HoloState.clampByte(Math.round(34f * fade * pulse));
        int ringAlpha = HoloState.clampByte(Math.round(230f * fade * pulse));
        int cornerAlpha = HoloState.clampByte(Math.round(255f * fade));

        int tint = (tintAlpha << 24) | (accentRgb & 0xFFFFFF);
        int ring = (ringAlpha << 24) | (accentRgb & 0xFFFFFF);
        int bright = (cornerAlpha << 24) | brightenRgb(accentRgb);

        // Soft inner wash plus outer 2px frame outside 16x16 item icons
        sink.fill(sx, sy, sx + 16, sy + 16, tint);

        // Top & bottom 2px rails
        sink.fill(sx - 2, sy - 2, sx + 18, sy, ring);
        sink.fill(sx - 2, sy + 16, sx + 18, sy + 18, ring);
        // Left & right 2px rails
        sink.fill(sx - 2, sy, sx, sy + 16, ring);
        sink.fill(sx + 16, sy, sx + 18, sy + 16, ring);

        // Bright 4px corner brackets
        sink.fill(sx - 3, sy - 3, sx + 2, sy - 2, bright);
        sink.fill(sx - 3, sy - 3, sx - 2, sy + 2, bright);
        sink.fill(sx + 14, sy - 3, sx + 19, sy - 2, bright);
        sink.fill(sx + 18, sy - 3, sx + 19, sy + 2, bright);
        sink.fill(sx - 3, sy + 18, sx + 2, sy + 19, bright);
        sink.fill(sx - 3, sy + 14, sx - 2, sy + 19, bright);
        sink.fill(sx + 14, sy + 18, sx + 19, sy + 19, bright);
        sink.fill(sx + 18, sy + 14, sx + 19, sy + 19, bright);

        // Expanding ripple during the first 650ms
        if (ageMs < 650L) {
            float t = ageMs / 650.0f;
            int expand = Math.round(t * 7.0f);
            int rAlpha = HoloState.clampByte(Math.round((1.0f - t) * 190f * fade));
            int ripple = (rAlpha << 24) | (bright & 0xFFFFFF);
            int x0 = sx - 2 - expand;
            int y0 = sy - 2 - expand;
            int x1 = sx + 18 + expand;
            int y1 = sy + 18 + expand;
            sink.fill(x0, y0, x1, y0 + 1, ripple);
            sink.fill(x0, y1 - 1, x1, y1, ripple);
            sink.fill(x0, y0 + 1, x0 + 1, y1 - 1, ripple);
            sink.fill(x1 - 1, y0 + 1, x1, y1 - 1, ripple);
        }
    }

    public static void drawBannerBox(
        RectIntSink sink,
        int x,
        int y,
        int w,
        int h,
        int accentRgb,
        long ageMs
    ) {
        if (sink == null || ageMs < 0L || ageMs >= HoloGeometry.PING_LIFETIME_MS) {
            return;
        }
        float fade = 1.0f;
        long remain = HoloGeometry.PING_LIFETIME_MS - ageMs;
        if (remain < 600L) {
            fade = Math.max(0f, remain / 600.0f);
        }
        int bgAlpha = HoloState.clampByte(Math.round(216f * fade));
        int borderAlpha = HoloState.clampByte(Math.round(235f * fade));
        int bg = (bgAlpha << 24) | 0x10141D;
        int accent = (borderAlpha << 24) | (accentRgb & 0xFFFFFF);
        int dimBorder = (HoloState.clampByte(Math.round(130f * fade)) << 24) | (accentRgb & 0xFFFFFF);

        sink.fill(x, y, x + w, y + h, bg);
        sink.fill(x, y, x + w, y + 2, accent);
        sink.fill(x, y + h - 1, x + w, y + h, dimBorder);
        sink.fill(x, y + 2, x + 1, y + h - 1, dimBorder);
        sink.fill(x + w - 1, y + 2, x + w, y + h - 1, dimBorder);
    }

    public static int fadeTextColor(int rgb, long ageMs) {
        long remain = HoloGeometry.PING_LIFETIME_MS - ageMs;
        float fade = remain < 600L ? Math.max(0f, remain / 600.0f) : 1.0f;
        int a = Math.max(8, HoloState.clampByte(Math.round(255f * fade)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int brightenRgb(int rgb) {
        int r = Math.min(255, ((rgb >>> 16) & 0xFF) + 55);
        int g = Math.min(255, ((rgb >>> 8) & 0xFF) + 55);
        int b = Math.min(255, (rgb & 0xFF) + 55);
        return (r << 16) | (g << 8) | b;
    }

    private static int[] estimateSizeFromSlots(AbstractContainerMenu menu) {
        if (menu == null || menu.slots.isEmpty()) {
            return new int[]{176, 166};
        }
        int maxX = 0;
        int maxY = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s != null) {
                maxX = Math.max(maxX, s.x);
                maxY = Math.max(maxY, s.y);
            }
        }
        int w = Math.max(176, maxX + 18 + 7);
        int h = Math.max(166, maxY + 18 + 7);
        return new int[]{w, h};
    }

    private static int readIntField(Field field, Object target) {
        if (field == null || target == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return field.getInt(target);
        } catch (ReflectiveOperationException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static int[] readFurnaceData(AbstractFurnaceMenu menu) {
        if (FURNACE_DATA_FIELD == null || menu == null) {
            return new int[]{0, 0};
        }
        try {
            Object obj = FURNACE_DATA_FIELD.get(menu);
            if (!(obj instanceof ContainerData)) {
                return new int[]{0, 0};
            }
            ContainerData data = (ContainerData) obj;
            if (data.getCount() < 4) {
                return new int[]{0, 0};
            }
            int litTime = Math.max(0, data.get(0));
            int litDuration = Math.max(0, data.get(1));
            int cookProgress = Math.max(0, data.get(2));
            int cookTotal = Math.max(0, data.get(3));
            int progress = cookTotal > 0 ? HoloState.clampByte(Math.round(cookProgress * 255f / cookTotal)) : 0;
            int burn = litDuration > 0
                ? HoloState.clampByte(Math.round(litTime * 255f / litDuration))
                : (litTime > 0 ? 180 : 0);
            return new int[]{progress, burn};
        } catch (ReflectiveOperationException ignored) {
            return new int[]{0, 0};
        }
    }

    private static Field findFurnaceDataField() {
        for (Field f : AbstractFurnaceMenu.class.getDeclaredFields()) {
            if (ContainerData.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    private static final class BoundsFields {
        final Field leftPos;
        final Field topPos;
        final Field imageWidth;
        final Field imageHeight;

        BoundsFields(Field leftPos, Field topPos, Field imageWidth, Field imageHeight) {
            this.leftPos = leftPos;
            this.topPos = topPos;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
        }
    }

    private static BoundsFields resolveBoundsFields() {
        Field left = null;
        Field top = null;
        Field w = null;
        Field h = null;

        // Tier 1: Mojang / unobfuscated field names walking superclasses too
        for (Class<?> cls = AbstractContainerScreen.class; cls != null && cls != Screen.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() != int.class || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                String name = f.getName();
                if ("leftPos".equals(name) && left == null) {
                    f.setAccessible(true);
                    left = f;
                } else if ("topPos".equals(name) && top == null) {
                    f.setAccessible(true);
                    top = f;
                } else if ("imageWidth".equals(name) && w == null) {
                    f.setAccessible(true);
                    w = f;
                } else if ("imageHeight".equals(name) && h == null) {
                    f.setAccessible(true);
                    h = f;
                }
            }
        }

        // Tier 2: Positional fallback over non-private, non-static, non-final int fields
        // on AbstractContainerScreen for Intermediary (1.16.5-1.21.11) and 26.x.
        if (left == null || top == null || w == null || h == null) {
            List<Field> ints = new ArrayList<>();
            for (Field f : AbstractContainerScreen.class.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (f.getType() == int.class
                    && !Modifier.isStatic(mod)
                    && (Modifier.isProtected(mod) || Modifier.isPublic(mod))
                    && !Modifier.isFinal(mod)) {
                    f.setAccessible(true);
                    ints.add(f);
                }
            }
            if (ints.size() >= 8) {
                if (w == null) {
                    w = ints.get(0);
                }
                if (h == null) {
                    h = ints.get(1);
                }
                if (left == null) {
                    left = ints.get(6);
                }
                if (top == null) {
                    top = ints.get(7);
                }
            } else if (ints.size() == 6) {
                if (left == null) {
                    left = ints.get(4);
                }
                if (top == null) {
                    top = ints.get(5);
                }
            }
        }

        return new BoundsFields(left, top, w, h);
    }
}
