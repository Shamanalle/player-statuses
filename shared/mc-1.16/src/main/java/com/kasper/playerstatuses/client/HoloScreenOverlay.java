package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.HoloState;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusClient;
import com.kasper.playerstatuses.StatusIcon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Attaches a non-intrusive 2D ping highlight overlay onto open containers for MC 1.16.5.
 */
public final class HoloScreenOverlay {

    private static final Field BUTTONS_FIELD = findListField();
    private static final Method ADD_BUTTON = findAddMethod();
    private static WeakReference<Screen> lastAttached = new WeakReference<>(null);

    private HoloScreenOverlay() {
    }

    public static void attachIfNeeded(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return;
        }
        AbstractContainerScreen<?> acs = (AbstractContainerScreen<?>) screen;
        try {
            if (BUTTONS_FIELD != null) {
                Object raw = BUTTONS_FIELD.get(screen);
                if (raw instanceof List<?>) {
                    List<?> list = (List<?>) raw;
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i) instanceof OverlayButton) {
                            return;
                        }
                    }
                    OverlayButton overlay = new OverlayButton(acs);
                    @SuppressWarnings("unchecked")
                    List<Object> mutable = (List<Object>) list;
                    mutable.add(overlay);
                    lastAttached = new WeakReference<>(screen);
                    return;
                }
            }
            if (lastAttached.get() == screen) {
                return;
            }
            if (ADD_BUTTON != null) {
                ADD_BUTTON.invoke(screen, new OverlayButton(acs));
                lastAttached = new WeakReference<>(screen);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static int accentFor(StatusClient client, java.util.UUID sender, long now) {
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
            if (generic instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) generic;
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 1 && args[0] == AbstractWidget.class) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        try {
            Field fallback = Screen.class.getDeclaredField("buttons");
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
            if (params.length == 1 && params[0] == AbstractWidget.class) {
                m.setAccessible(true);
                if ("addButton".equals(m.getName())) {
                    byName = m;
                    break;
                }
                if (m.getReturnType() == AbstractWidget.class && byType == null) {
                    byType = m;
                }
            }
        }
        return byName != null ? byName : byType;
    }

    private static final class OverlayButton extends AbstractWidget {
        private final AbstractContainerScreen<?> screen;

        OverlayButton(AbstractContainerScreen<?> screen) {
            super(0, 0, 0, 0, new TextComponent(""));
            this.screen = screen;
            this.active = false;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return false;
        }

        @Override
        public boolean changeFocus(boolean focus) {
            return false;
        }

        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) {
                return;
            }
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
            HoloCapture.RectIntSink sink = new HoloCapture.RectIntSink() {
                @Override
                public void fill(int x0, int y0, int x1, int y1, int argb) {
                    GuiComponent.fill(poseStack, x0, y0, x1, y1, argb);
                }
            };
            for (int i = 0; i < pings.size(); i++) {
                HoloState.Event ev = pings.get(i);
                if (ev.action() != HoloState.Action.PING_SLOT) {
                    continue;
                }
                int[] coords = HoloCapture.slotScreenCoords(screen, ev.slotIndex());
                if (coords == null) {
                    continue;
                }
                long age = Math.max(0L, now - ev.createdAtMs());
                int accent = accentFor(client, ev.sender(), now);
                HoloCapture.drawSlotPingRects(sink, coords[0], coords[1], accent, age);
            }

            HoloState.Event latest = pings.get(pings.size() - 1);
            Component banner = HoloCapture.pingBannerComponent(mc, latest);
            int[] box = HoloCapture.bannerGeometry(screen, mc.font.width(banner));
            long age = Math.max(0L, now - latest.createdAtMs());
            int accent = accentFor(client, latest.sender(), now);
            HoloCapture.drawBannerBox(sink, box[0], box[1], box[2], box[3], accent, age);
            GuiComponent.drawCenteredString(
                poseStack,
                mc.font,
                banner,
                box[0] + box[2] / 2,
                box[1] + 4,
                HoloCapture.fadeTextColor(0xF6F8FC, age)
            );
        }
    }
}
