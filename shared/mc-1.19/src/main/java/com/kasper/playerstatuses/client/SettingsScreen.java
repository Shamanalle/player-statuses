package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.Category;
import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.SettingsFile;
import com.kasper.playerstatuses.SettingsLayout;
import com.kasper.playerstatuses.StatusIcon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game settings screen for Player Statuses (Minecraft 1.19–1.19.2).
 */
public class SettingsScreen extends Screen {

    private static final class TooltipZone {
        final int x;
        final int y;
        final int w;
        final int h;
        final Component text;

        TooltipZone(int x, int y, int w, int h, Component text) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.text = text;
        }

        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Screen parent;
    private int currentTab = SettingsLayout.TAB_GENERAL;
    private int previewIndex = 0;
    private final List<TooltipZone> tooltips = new ArrayList<>();

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.playerstatuses.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.tooltips.clear();
        ClientSettings settings = ClientSide.core().settings();
        SettingsLayout.Metrics m = new SettingsLayout.Metrics(this.width, this.height);

        addRenderableWidget(new Button(m.previewBtnX, m.previewBtnY, m.previewBtnW, m.previewBtnH,
                Component.literal(SettingsLayout.CYCLE_SYMBOL), b -> {
            this.previewIndex = (this.previewIndex + 1) % SettingsLayout.PREVIEW_COUNT;
        }));
        this.tooltips.add(new TooltipZone(m.previewBtnX, m.previewBtnY, m.previewBtnW, m.previewBtnH,
                Component.translatable("gui.playerstatuses.settings.preview.hint")));

        int t0W = m.tabW;
        int t1X = m.panelX + m.tabW + m.tabGap;
        int t2X = m.panelX + (m.tabW + m.tabGap) * 2;
        int t2W = m.panelW - (m.tabW + m.tabGap) * 2;
        addTabButton(m.panelX, m.tabsY, t0W, m.tabH, SettingsLayout.TAB_GENERAL, "gui.playerstatuses.settings.tab.general");
        addTabButton(t1X, m.tabsY, t0W, m.tabH, SettingsLayout.TAB_SEND, "gui.playerstatuses.settings.tab.send");
        addTabButton(t2X, m.tabsY, t2W, m.tabH, SettingsLayout.TAB_SHOW, "gui.playerstatuses.settings.tab.show");

        if (currentTab == SettingsLayout.TAB_GENERAL) {
            int y = m.generalStartY;
            addSlider(m.leftX, y, m.colW, m.btnH, settings, ClientSettings.AFK_MINUTES, 1, 60,
                    "gui.playerstatuses.settings.afk_minutes", "gui.playerstatuses.settings.afk_minutes.tooltip");
            addSlider(m.rightX, y, m.colW, m.btnH, settings, ClientSettings.ICON_SCALE, 50, 200,
                    "gui.playerstatuses.settings.icon_scale", "gui.playerstatuses.settings.icon_scale.tooltip");

            addSlider(m.leftX, y + m.rowStep, m.colW, m.btnH, settings, ClientSettings.VIEW_DISTANCE, 4, 128,
                    "gui.playerstatuses.settings.view_distance", "gui.playerstatuses.settings.view_distance.tooltip");
            addSlider(m.rightX, y + m.rowStep, m.colW, m.btnH, settings, ClientSettings.OPACITY, 20, 100,
                    "gui.playerstatuses.settings.opacity", "gui.playerstatuses.settings.opacity.tooltip");

            addToggle(m.leftX, y + m.rowStep * 2, m.colW, m.btnH, settings, ClientSettings.HIDE_WHEN_SNEAKING,
                    Component.translatable("gui.playerstatuses.settings.hide_when_sneaking"),
                    "gui.playerstatuses.settings.hide_when_sneaking.tooltip");
            addToggle(m.rightX, y + m.rowStep * 2, m.colW, m.btnH, settings, ClientSettings.SHOW_OWN,
                    Component.translatable("gui.playerstatuses.settings.show_own"),
                    "gui.playerstatuses.settings.show_own.tooltip");

            addToggle(m.leftX, y + m.rowStep * 3, m.colW, m.btnH, settings, ClientSettings.TAB_LIST,
                    Component.translatable("gui.playerstatuses.settings.tab_list"),
                    "gui.playerstatuses.settings.tab_list.tooltip");
            addToggle(m.rightX, y + m.rowStep * 3, m.colW, m.btnH, settings, ClientSettings.ARM_POSES,
                    Component.translatable("gui.playerstatuses.settings.arm_poses"),
                    "gui.playerstatuses.settings.arm_poses.tooltip");

            addToggle(m.leftX, y + m.rowStep * 4, m.colW, m.btnH, settings, ClientSettings.HOLO_PROJECTIONS,
                    Component.translatable("gui.playerstatuses.settings.holo_projections"),
                    "gui.playerstatuses.settings.holo_projections.tooltip");
            addSlider(m.rightX, y + m.rowStep * 4, m.colW, m.btnH, settings, ClientSettings.HOLO_DISTANCE, 4, 24,
                    "gui.playerstatuses.settings.holo_distance", "gui.playerstatuses.settings.holo_distance.tooltip");

            addToggle(m.leftX, y + m.rowStep * 5, m.colW, m.btnH, settings, ClientSettings.ALLOW_HOLO_PINGS,
                    Component.translatable("gui.playerstatuses.settings.allow_holo_pings"),
                    "gui.playerstatuses.settings.allow_holo_pings.tooltip");
            addToggle(m.rightX, y + m.rowStep * 5, m.colW, m.btnH, settings, ClientSettings.HOLO_SOUND,
                    Component.translatable("gui.playerstatuses.settings.holo_sound"),
                    "gui.playerstatuses.settings.holo_sound.tooltip");
        } else {
            boolean sendTab = currentTab == SettingsLayout.TAB_SEND;
            addRenderableWidget(new Button(m.leftX, m.bulkY, m.colW, m.subBtnH,
                    Component.translatable("gui.playerstatuses.settings.all_on"), b -> {
                SettingsLayout.setAllCategories(settings, sendTab, true);
                rebuildScreen();
            }));
            addRenderableWidget(new Button(m.rightX, m.bulkY, m.colW, m.subBtnH,
                    Component.translatable("gui.playerstatuses.settings.all_off"), b -> {
                SettingsLayout.setAllCategories(settings, sendTab, false);
                rebuildScreen();
            }));

            Category[] categories = Category.values();
            for (int i = 0; i < categories.length; i++) {
                Category c = categories[i];
                int x = (i % 2 == 0) ? m.leftX : m.rightX;
                int y = m.gridStartY + (i / 2) * m.rowStep;
                SettingsFile.Key<Boolean> key = sendTab ? ClientSettings.send(c) : ClientSettings.show(c);
                addToggle(x, y, m.colW, m.btnH, settings, key, Component.translatable(c.translationKey()), null);
            }
            if (sendTab) {
                int extraY = m.gridStartY + ((categories.length + 1) / 2) * m.rowStep;
                addToggle(m.leftX, extraY, m.colW, m.btnH, settings, ClientSettings.SEND_PROJECTIONS,
                        Component.translatable("gui.playerstatuses.settings.send_projections"),
                        "gui.playerstatuses.settings.send_projections.tooltip");
                addToggle(m.rightX, extraY, m.colW, m.btnH, settings, ClientSettings.SEND_PROJECTION_SLOTS,
                        Component.translatable("gui.playerstatuses.settings.send_projection_slots"),
                        "gui.playerstatuses.settings.send_projection_slots.tooltip");
            }
        }

        addRenderableWidget(new Button(m.panelX, m.footerY, m.resetW, m.footerH,
                Component.translatable("gui.playerstatuses.settings.reset"), b -> {
            settings.resetDefaults();
            ClientSide.core().save();
            rebuildScreen();
        }));
        this.tooltips.add(new TooltipZone(m.panelX, m.footerY, m.resetW, m.footerH,
                Component.translatable("gui.playerstatuses.settings.reset.tooltip")));

        addRenderableWidget(new Button(m.doneX, m.footerY, m.doneW, m.footerH, CommonComponents.GUI_DONE, b -> onClose()));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        SettingsLayout.Metrics m = new SettingsLayout.Metrics(this.width, this.height);
        ClientSettings settings = ClientSide.core().settings();

        GuiComponent.drawCenteredString(poseStack, this.font, this.title, this.width / 2, m.titleY, 0xFFFFFFFF);

        PlayerStatus preview = SettingsLayout.previewStatus(previewIndex, settings);
        Component badgeLabel = StatusText.overheadLabel(preview);
        int badgeTextW = this.font.width(badgeLabel);
        int opacity = settings.get(ClientSettings.OPACITY);

        MutableComponent tabEntry = Component.literal(SettingsLayout.PREVIEW_NAME);
        if (settings.get(ClientSettings.TAB_LIST)) {
            tabEntry.append(StatusText.tabSuffix(preview));
        }
        int maxTabW = m.previewCardW / 2 - 14;
        if (this.font.width(tabEntry) > maxTabW) {
            tabEntry = Component.literal(SettingsLayout.PREVIEW_NAME);
        }
        int tabEntryW = this.font.width(tabEntry);

        boolean hovered = mouseX >= m.panelX && mouseX < m.panelX + m.previewCardW
                && mouseY >= m.previewY && mouseY < m.previewY + m.previewH;
        int[] origin = new int[6];
        SettingsLayout.drawHeaderChrome(
                (x0, y0, x1, y1, argb) -> GuiComponent.fill(poseStack, x0, y0, x1, y1, argb),
                m, currentTab, hovered, preview,
                badgeTextW, opacity, tabEntryW, System.currentTimeMillis(), origin);

        this.font.draw(poseStack, badgeLabel, origin[0], origin[1], StatusIcon.textColor(opacity));
        if (!m.compact) {
            int nameW = this.font.width(SettingsLayout.PREVIEW_NAME);
            this.font.draw(poseStack, SettingsLayout.PREVIEW_NAME, origin[2] - nameW / 2.0f, origin[3], 0xFFE6EDF3);
        }
        this.font.drawShadow(poseStack, tabEntry, origin[4] - tabEntryW / 2.0f, origin[5], 0xFFFFFFFF);

        if (currentTab != SettingsLayout.TAB_GENERAL) {
            Component hint = Component.translatable(currentTab == SettingsLayout.TAB_SEND
                    ? "gui.playerstatuses.settings.send.hint"
                    : "gui.playerstatuses.settings.show.hint");
            GuiComponent.drawCenteredString(poseStack, this.font, hint, this.width / 2, m.hintY, 0xFFB0B8C8);
        }

        super.render(poseStack, mouseX, mouseY, partialTick);

        for (TooltipZone z : this.tooltips) {
            if (z.contains(mouseX, mouseY)) {
                this.renderTooltip(poseStack, this.font.split(z.text, 200), mouseX, mouseY);
                break;
            }
        }
    }

    private void rebuildScreen() {
        clearWidgets();
        init();
    }

    private void addTabButton(int x, int y, int width, int height, int tab, String translationKey) {
        Button btn = new Button(x, y, width, height, Component.translatable(translationKey), b -> {
            this.currentTab = tab;
            rebuildScreen();
        });
        btn.active = this.currentTab != tab;
        addRenderableWidget(btn);
    }

    private void addSlider(
            int x,
            int y,
            int width,
            int height,
            ClientSettings settings,
            SettingsFile.Key<Integer> key,
            int min,
            int max,
            String translationKey,
            String tooltipKey
    ) {
        addRenderableWidget(new IntSlider(x, y, width, height, settings, key, min, max, translationKey));
        if (tooltipKey != null) {
            this.tooltips.add(new TooltipZone(x, y, width, height, Component.translatable(tooltipKey)));
        }
    }

    private void addToggle(
            int x,
            int y,
            int width,
            int height,
            ClientSettings settings,
            SettingsFile.Key<Boolean> key,
            Component label,
            String tooltipKey
    ) {
        addRenderableWidget(new Button(x, y, width, height, toggleMessage(label, settings.get(key)), btn -> {
            boolean next = !settings.get(key);
            settings.set(key, next);
            btn.setMessage(toggleMessage(label, next));
        }));
        if (tooltipKey != null) {
            this.tooltips.add(new TooltipZone(x, y, width, height, Component.translatable(tooltipKey)));
        }
    }

    private static Component toggleMessage(Component label, boolean on) {
        Component state = Component.translatable(on ? "options.on" : "options.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
        return label.copy().append(": ").append(state);
    }

    @Override
    public void removed() {
        ClientSide.core().save();
    }

    @Override
    public void onClose() {
        ClientSide.core().save();
        ClientAccess.setScreen(this.minecraft, this.parent);
    }

    private static final class IntSlider extends AbstractSliderButton {

        private final ClientSettings settings;
        private final SettingsFile.Key<Integer> key;
        private final int min;
        private final int max;
        private final String translationKey;

        IntSlider(
                int x,
                int y,
                int width,
                int height,
                ClientSettings settings,
                SettingsFile.Key<Integer> key,
                int min,
                int max,
                String translationKey
        ) {
            super(x, y, width, height, Component.empty(), toSlider(settings.get(key), min, max));
            this.settings = settings;
            this.key = key;
            this.min = min;
            this.max = max;
            this.translationKey = translationKey;
            updateMessage();
        }

        private static double toSlider(int val, int min, int max) {
            if (max <= min) {
                return 0.0;
            }
            return (double) (val - min) / (double) (max - min);
        }

        private int currentInt() {
            int raw = (int) Math.round(min + value * (max - min));
            return Math.max(min, Math.min(max, raw));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(translationKey, Integer.toString(currentInt())));
        }

        @Override
        protected void applyValue() {
            settings.set(key, currentInt());
        }
    }
}
