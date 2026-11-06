package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.Category;
import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.SettingsFile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * In-game settings screen for Player Statuses (1.19.3–1.19.4).
 */
public class SettingsScreen extends Screen {

    private static final int TAB_GENERAL = 0;
    private static final int TAB_SEND = 1;
    private static final int TAB_SHOW = 2;

    private final Screen parent;
    private int currentTab = TAB_GENERAL;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.playerstatuses.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientSettings settings = ClientSide.core().settings();
        int centerX = this.width / 2;

        int tabWidth = 100;
        int tabGap = 6;
        int tabsLeft = centerX - (tabWidth * 3 + tabGap * 2) / 2;
        addTabButton(tabsLeft, 24, tabWidth, TAB_GENERAL, "gui.playerstatuses.settings.tab.general");
        addTabButton(tabsLeft + tabWidth + tabGap, 24, tabWidth, TAB_SEND, "gui.playerstatuses.settings.tab.send");
        addTabButton(tabsLeft + (tabWidth + tabGap) * 2, 24, tabWidth, TAB_SHOW, "gui.playerstatuses.settings.tab.show");

        int colWidth = 150;
        int colGap = 10;
        int leftX = centerX - colWidth - colGap / 2;
        int rightX = centerX + colGap / 2;
        int startY = 56;
        int rowStep = 24;

        if (currentTab == TAB_GENERAL) {
            addRenderableWidget(new IntSlider(leftX, startY, colWidth, 20, settings,
                    ClientSettings.AFK_MINUTES, 1, 60, "gui.playerstatuses.settings.afk_minutes"));
            addRenderableWidget(new IntSlider(rightX, startY, colWidth, 20, settings,
                    ClientSettings.ICON_SCALE, 50, 200, "gui.playerstatuses.settings.icon_scale"));

            addRenderableWidget(new IntSlider(leftX, startY + rowStep, colWidth, 20, settings,
                    ClientSettings.VIEW_DISTANCE, 4, 128, "gui.playerstatuses.settings.view_distance"));
            addRenderableWidget(new IntSlider(rightX, startY + rowStep, colWidth, 20, settings,
                    ClientSettings.OPACITY, 20, 100, "gui.playerstatuses.settings.opacity"));

            addToggle(leftX, startY + rowStep * 2, colWidth, settings,
                    ClientSettings.HIDE_WHEN_SNEAKING, Component.translatable("gui.playerstatuses.settings.hide_when_sneaking"));
            addToggle(rightX, startY + rowStep * 2, colWidth, settings,
                    ClientSettings.SHOW_OWN, Component.translatable("gui.playerstatuses.settings.show_own"));

            addToggle(leftX, startY + rowStep * 3, colWidth, settings,
                    ClientSettings.TAB_LIST, Component.translatable("gui.playerstatuses.settings.tab_list"));
            addToggle(rightX, startY + rowStep * 3, colWidth, settings,
                    ClientSettings.ARM_POSES, Component.translatable("gui.playerstatuses.settings.arm_poses"));
        } else {
            boolean sendTab = currentTab == TAB_SEND;
            int catStartY = 62;
            Category[] categories = Category.values();
            for (int i = 0; i < categories.length; i++) {
                Category c = categories[i];
                int x = (i % 2 == 0) ? leftX : rightX;
                int y = catStartY + (i / 2) * rowStep;
                SettingsFile.Key<Boolean> key = sendTab ? ClientSettings.send(c) : ClientSettings.show(c);
                addToggle(x, y, colWidth, settings, key, Component.translatable(c.translationKey()));
            }
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(centerX - 100, this.height - 28, 200, 20)
                .build());
    }

    private void addTabButton(int x, int y, int width, int tab, String translationKey) {
        Button btn = Button.builder(Component.translatable(translationKey), b -> {
            this.currentTab = tab;
            clearWidgets();
            init();
        }).bounds(x, y, width, 20).build();
        btn.active = this.currentTab != tab;
        addRenderableWidget(btn);
    }

    private void addToggle(
            int x,
            int y,
            int width,
            ClientSettings settings,
            SettingsFile.Key<Boolean> key,
            Component label
    ) {
        addRenderableWidget(Button.builder(CommonComponents.optionStatus(label, settings.get(key)), btn -> {
            boolean next = !settings.get(key);
            settings.set(key, next);
            btn.setMessage(CommonComponents.optionStatus(label, next));
        }).bounds(x, y, width, 20).build());
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        if (currentTab != TAB_GENERAL) {
            Component hint = Component.translatable(currentTab == TAB_SEND
                    ? "gui.playerstatuses.settings.send.hint"
                    : "gui.playerstatuses.settings.show.hint");
            drawCenteredString(poseStack, this.font, hint, this.width / 2, 46, 0xA0A0A0);
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
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
