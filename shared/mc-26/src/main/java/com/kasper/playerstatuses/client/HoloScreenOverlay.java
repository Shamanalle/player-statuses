package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.HoloState;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusClient;
import com.kasper.playerstatuses.StatusIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Attaches a non-intrusive 2D ping highlight overlay onto open vanilla or modded containers.
 */
public final class HoloScreenOverlay {

    private static final Field RENDERABLES_FIELD = findListField();
    private static final Method ADD_RENDERABLE_ONLY = findAddMethod();
    private static WeakReference<Screen> lastAttached = new WeakReference<>(null);

    private HoloScreenOverlay() {
    }

    public static void attachIfNeeded(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) {
            return;
        }
        try {
            if (RENDERABLES_FIELD != null) {
                Object raw = RENDERABLES_FIELD.get(screen);
                if (raw instanceof List<?> list) {
                    for (Object entry : list) {
                        if (entry instanceof OverlayRenderable) {
                            return;
                        }
                    }
                    OverlayRenderable overlay = new OverlayRenderable(acs);
                    if (ADD_RENDERABLE_ONLY != null) {
                        ADD_RENDERABLE_ONLY.invoke(screen, overlay);
                    } else {
                        @SuppressWarnings("unchecked")
                        List<Object> mutable = (List<Object>) list;
                        mutable.add(overlay);
                    }
                    lastAttached = new WeakReference<>(screen);
                    return;
                }
            }
            if (lastAttached.get() == screen) {
                return;
            }
            if (ADD_RENDERABLE_ONLY != null) {
                ADD_RENDERABLE_ONLY.invoke(screen, new OverlayRenderable(acs));
                lastAttached = new WeakReference<>(screen);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static int accentFor(Minecraft mc, StatusClient client, java.util.UUID sender, long now) {
        PlayerStatus status = client.statusOf(sender, now);
        if (status != null && !status.isNone()) {
            return StatusIcon.accentRgb(status);
        }
        return 0x45E6FF;
    }

    private static Field findListField() {
        for (Field f : Screen.class.getDeclaredFields()) {
            if (f.getType() != List.class) {
                continue;
            }
            Type generic = f.getGenericType();
            if (generic instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 1 && args[0] == Renderable.class) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        try {
            Field fallback = Screen.class.getDeclaredField("renderables");
            fallback.setAccessible(true);
            return fallback;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findAddMethod() {
        Method byName = null;
        Method byType = null;
        for (Method m : Screen.class.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 1 && params[0] == Renderable.class) {
                m.setAccessible(true);
                if ("addRenderableOnly".equals(m.getName())) {
                    byName = m;
                    break;
                }
                if (m.getReturnType() == Renderable.class && byType == null) {
                    byType = m;
                }
            }
        }
        return byName != null ? byName : byType;
    }

    private static final class OverlayRenderable implements Renderable {
        private final AbstractContainerScreen<?> screen;

        OverlayRenderable(AbstractContainerScreen<?> screen) {
            this.screen = screen;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            StatusClient client = ClientSide.core();
            if (!Boolean.TRUE.equals(client.settings().get(ClientSettings.ALLOW_HOLO_PINGS))) {
                return;
            }
            long now = System.currentTimeMillis();
            List<HoloState.Event> pings = client.activeLocalScreenPings(now);
            if (pings.isEmpty()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            HoloCapture.RectIntSink sink = graphics::fill;
            for (HoloState.Event ev : pings) {
                if (ev.action() != HoloState.Action.PING_SLOT) {
                    continue;
                }
                int[] coords = HoloCapture.slotScreenCoords(screen, ev.slotIndex());
                if (coords == null) {
                    continue;
                }
                long age = Math.max(0L, now - ev.createdAtMs());
                int accent = accentFor(mc, client, ev.sender(), now);
                HoloCapture.drawSlotPingRects(sink, coords[0], coords[1], accent, age);
            }

            HoloState.Event latest = pings.get(pings.size() - 1);
            Component banner = HoloCapture.pingBannerComponent(mc, latest);
            int[] box = HoloCapture.bannerGeometry(screen, mc.font.width(banner));
            long age = Math.max(0L, now - latest.createdAtMs());
            int accent = accentFor(mc, client, latest.sender(), now);
            HoloCapture.drawBannerBox(sink, box[0], box[1], box[2], box[3], accent, age);
            graphics.centeredText(
                mc.font,
                banner,
                box[0] + box[2] / 2,
                box[1] + 4,
                HoloCapture.fadeTextColor(0xF6F8FC, age)
            );
        }
    }
}
