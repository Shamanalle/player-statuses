package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.Category;
import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.SettingsFile;
import com.kasper.playerstatuses.SettingsLayout;
import com.kasper.playerstatuses.StatusIcon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * In-game settings screen for Player Statuses (Minecraft 1.20.2–1.21.11).
 */
public class SettingsScreen extends Screen {

    private final Screen parent;
    private int currentTab = SettingsLayout.TAB_GENERAL;
    private int previewIndex = 0;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.playerstatuses.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientSettings settings = ClientSide.core().settings();
        SettingsLayout.Metrics m = new SettingsLayout.Metrics(this.width, this.height);

        addRenderableOnly((graphics, mouseX, mouseY, partialTick) ->
                renderChrome(graphics, m, settings, mouseX, mouseY));

        Button cycleBtn = Button.builder(Component.literal(SettingsLayout.CYCLE_SYMBOL), b -> {
            this.previewIndex = (this.previewIndex + 1) % SettingsLayout.PREVIEW_COUNT;
        }).bounds(m.previewBtnX, m.previewBtnY, m.previewBtnW, m.previewBtnH)
                .tooltip(Tooltip.create(Component.translatable("gui.playerstatuses.settings.preview.hint")))
                .build();
        addRenderableWidget(cycleBtn);

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
        } else {
            boolean sendTab = currentTab == SettingsLayout.TAB_SEND;
            addRenderableWidget(Button.builder(Component.translatable("gui.playerstatuses.settings.all_on"), b -> {
                SettingsLayout.setAllCategories(settings, sendTab, true);
                rebuildScreen();
            }).bounds(m.leftX, m.bulkY, m.colW, m.subBtnH).build());

            addRenderableWidget(Button.builder(Component.translatable("gui.playerstatuses.settings.all_off"), b -> {
                SettingsLayout.setAllCategories(settings, sendTab, false);
                rebuildScreen();
            }).bounds(m.rightX, m.bulkY, m.colW, m.subBtnH).build());

            Category[] categories = Category.values();
            for (int i = 0; i < categories.length; i++) {
                Category c = categories[i];
                int x = (i % 2 == 0) ? m.leftX : m.rightX;
                int y = m.gridStartY + (i / 2) * m.rowStep;
                SettingsFile.Key<Boolean> key = sendTab ? ClientSettings.send(c) : ClientSettings.show(c);
                addToggle(x, y, m.colW, m.btnH, settings, key, Component.translatable(c.translationKey()), null);
            }
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.playerstatuses.settings.reset"), b -> {
            settings.resetDefaults();
            ClientSide.core().save();
            rebuildScreen();
        }).bounds(m.panelX, m.footerY, m.resetW, m.footerH)
                .tooltip(Tooltip.create(Component.translatable("gui.playerstatuses.settings.reset.tooltip")))
                .build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(m.doneX, m.footerY, m.doneW, m.footerH)
                .build());
    }

    private void renderChrome(
            GuiGraphics graphics,
            SettingsLayout.Metrics m,
            ClientSettings settings,
            int mouseX,
            int mouseY
    ) {
        graphics.drawCenteredString(this.font, this.title, this.width / 2, m.titleY, 0xFFFFFFFF);

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
                graphics::fill, m, currentTab, hovered, preview,
                badgeTextW, opacity, tabEntryW, System.currentTimeMillis(), origin);

        graphics.enableScissor(m.panelX + 1, m.previewY + 1, m.panelX + m.previewCardW - 1, m.previewY + m.previewH - 1);
        graphics.drawString(this.font, badgeLabel, origin[0], origin[1], StatusIcon.textColor(opacity), false);
        if (!m.compact) {
            int nameW = this.font.width(SettingsLayout.PREVIEW_NAME);
            graphics.drawString(this.font, SettingsLayout.PREVIEW_NAME, origin[2] - nameW / 2, origin[3], 0xFFE6EDF3, false);
        }
        graphics.drawString(this.font, tabEntry, origin[4] - tabEntryW / 2, origin[5], 0xFFFFFFFF, true);
        graphics.disableScissor();

        if (currentTab != SettingsLayout.TAB_GENERAL) {
            Component hint = Component.translatable(currentTab == SettingsLayout.TAB_SEND
                    ? "gui.playerstatuses.settings.send.hint"
                    : "gui.playerstatuses.settings.show.hint");
            graphics.drawCenteredString(this.font, hint, this.width / 2, m.hintY, 0xFFB0B8C8);
        }
    }

    private void rebuildScreen() {
        clearWidgets();
        init();
    }

    private void addTabButton(int x, int y, int width, int height, int tab, String translationKey) {
        Button btn = Button.builder(Component.translatable(translationKey), b -> {
            this.currentTab = tab;
            rebuildScreen();
        }).bounds(x, y, width, height).build();
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
        IntSlider slider = new IntSlider(x, y, width, height, settings, key, min, max, translationKey);
        if (tooltipKey != null) {
            slider.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }
        addRenderableWidget(slider);
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
        Button.Builder builder = Button.builder(toggleMessage(label, settings.get(key)), btn -> {
            boolean next = !settings.get(key);
            settings.set(key, next);
            btn.setMessage(toggleMessage(label, next));
        }).bounds(x, y, width, height);
        if (tooltipKey != null) {
            builder.tooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }
        addRenderableWidget(builder.build());
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
