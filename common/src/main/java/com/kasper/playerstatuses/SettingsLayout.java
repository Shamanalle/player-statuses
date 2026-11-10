package com.kasper.playerstatuses;

/**
 * Pure-Java responsive layout metrics, live preview state cycling and 2D card chrome for
 * {@code SettingsScreen} across Minecraft 1.16.5–26.x.
 */
public final class SettingsLayout {

    public static final int TAB_GENERAL = 0;
    public static final int TAB_SEND = 1;
    public static final int TAB_SHOW = 2;

    public static final int PREVIEW_COUNT = 5;
    public static final String PREVIEW_NAME = "Alex";
    public static final String CYCLE_SYMBOL = ">>";

    private SettingsLayout() {
    }

    /** Returns one of {@link #PREVIEW_COUNT} representative sample statuses for the live preview header. */
    public static PlayerStatus previewStatus(int index, ClientSettings settings) {
        int afkSeconds = Math.max(60, settings.get(ClientSettings.AFK_MINUTES) * 60);
        switch (Math.floorMod(index, PREVIEW_COUNT)) {
            case 0:
                return PlayerStatus.of(Activity.TYPING);
            case 1:
                return PlayerStatus.of(Activity.CRAFTING);
            case 2:
                return PlayerStatus.of(Activity.CONTAINER);
            case 3:
                return PlayerStatus.of(Activity.NONE, StatusFlag.AFK, StatusFlag.UNFOCUSED)
                        .withIdleSeconds(afkSeconds);
            default:
                return PlayerStatus.of(Activity.INVENTORY, StatusFlag.MIC_OFF, StatusFlag.SOUND_OFF);
        }
    }

    /** Sets all {@link Category} switches on either the Share or Visibility tab at once. */
    public static void setAllCategories(ClientSettings settings, boolean sendTab, boolean enabled) {
        for (Category c : Category.values()) {
            settings.set(sendTab ? ClientSettings.send(c) : ClientSettings.show(c), enabled);
        }
    }

    /** Responsive screen coordinates that fit cleanly from GUI Scale 4 (240px tall) up to 4K. */
    public static final class Metrics {
        public final boolean compact;
        public final int panelW;
        public final int panelX;
        public final int titleY;
        public final int previewY;
        public final int previewH;
        public final int previewCardW;
        public final int previewBtnX;
        public final int previewBtnY;
        public final int previewBtnW;
        public final int previewBtnH;
        public final int tabsY;
        public final int tabH;
        public final int tabGap;
        public final int tabW;
        public final int hintY;
        public final int bulkY;
        public final int subBtnH;
        public final int gridStartY;
        public final int generalStartY;
        public final int colGap;
        public final int colW;
        public final int leftX;
        public final int rightX;
        public final int btnH;
        public final int rowStep;
        public final int footerY;
        public final int footerH;
        public final int resetW;
        public final int doneX;
        public final int doneW;

        public Metrics(int screenWidth, int screenHeight) {
            this.compact = screenHeight < 262;
            boolean medium = !compact && screenHeight < 304;

            this.panelW = Math.max(260, Math.min(340, screenWidth - 16));
            this.panelX = (screenWidth - panelW) / 2;

            this.titleY = compact ? 4 : (medium ? 5 : 6);
            this.previewY = compact ? 14 : (medium ? 15 : 18);
            this.previewH = compact ? 22 : (medium ? 30 : 34);

            this.btnH = compact ? 15 : (medium ? 18 : 20);
            this.previewBtnW = compact ? 18 : 20;
            this.previewBtnH = this.btnH;
            this.previewCardW = this.panelW - this.previewBtnW - 4;
            this.previewBtnX = this.panelX + this.panelW - this.previewBtnW;
            this.previewBtnY = this.previewY + (this.previewH - this.previewBtnH) / 2;

            this.tabsY = previewY + previewH + (compact ? 3 : 4);
            this.tabH = compact ? 16 : (medium ? 18 : 20);
            this.tabGap = 4;
            this.tabW = (panelW - tabGap * 2) / 3;

            this.hintY = tabsY + tabH + (compact ? 5 : (medium ? 6 : 7));
            this.bulkY = hintY + (compact ? 10 : (medium ? 11 : 12));
            this.subBtnH = compact ? 15 : (medium ? 16 : 20);

            this.rowStep = compact ? 17 : (medium ? 20 : 22);
            this.generalStartY = tabsY + tabH + (compact ? 6 : (medium ? 8 : 10));
            this.gridStartY = bulkY + subBtnH + (compact ? 3 : 4);

            this.colGap = 8;
            this.colW = (panelW - colGap) / 2;
            this.leftX = panelX;
            this.rightX = panelX + colW + colGap;

            this.footerH = compact ? 18 : 20;
            this.footerY = screenHeight - footerH - (compact ? 3 : (medium ? 4 : 6));
            this.resetW = Math.min(116, (panelW - colGap) * 2 / 5);
            this.doneX = panelX + resetW + colGap;
            this.doneW = panelW - resetW - colGap;
        }
    }

    /** Integer rectangle callback for 2D GUI drawing across all Minecraft eras. */
    public interface IntRectSink {
        void fill(int x0, int y0, int x1, int y1, int argb);
    }

    /**
     * Draws the dark-slate preview card frame, vertical split, Tab-list sample background and
     * active-tab underline bar, plus the live {@link StatusIcon#drawCard} overhead pill.
     *
     * @param outOrigin 6-int array receiving {@code [badgeTextX, badgeTextY, nameplateCenterX, nameplateTextY, tabCenterX, tabTextY]}
     * @return the {@link StatusIcon.CardLayout} in local coordinates
     */
    public static StatusIcon.CardLayout drawHeaderChrome(
            IntRectSink rect,
            Metrics m,
            int activeTab,
            boolean previewHovered,
            PlayerStatus previewStatus,
            int badgeTextWidth,
            int opacityPct,
            int tabEntryWidth,
            long nowMillis,
            int[] outOrigin
    ) {
        int x0 = m.panelX;
        int y0 = m.previewY;
        int x1 = m.panelX + m.previewCardW;
        int y1 = y0 + m.previewH;
        int midX = x0 + m.previewCardW / 2;

        // Subtle outer card + border
        int cardBg = previewHovered ? 0xDC151A26 : 0xCC10131C;
        int border = previewHovered ? 0xFF3E4C6E : 0xFF283044;
        rect.fill(x0, y0, x1, y1, cardBg);
        rect.fill(x0, y0, x1, y0 + 1, border);
        rect.fill(x0, y1 - 1, x1, y1, border);
        rect.fill(x0, y0 + 1, x0 + 1, y1 - 1, border);
        rect.fill(x1 - 1, y0 + 1, x1, y1 - 1, border);
        rect.fill(midX, y0 + 4, midX + 1, y1 - 4, 0x553B4664);

        // Active tab 2px accent indicator bar
        int activeTabX = m.panelX + activeTab * (m.tabW + m.tabGap);
        int activeTabW = (activeTab == 2) ? (m.panelW - 2 * (m.tabW + m.tabGap)) : m.tabW;
        rect.fill(activeTabX + 2, m.tabsY + m.tabH, activeTabX + activeTabW - 2, m.tabsY + m.tabH + 2, 0xFF58A6FF);

        // Live overhead StatusIcon pill centered in left half of preview card
        StatusIcon.CardLayout probe = StatusIcon.layout(previewStatus, badgeTextWidth, opacityPct);
        int pillWidth = Math.round(probe.x1() - probe.x0());
        int leftCenterX = x0 + (midX - x0) / 2;
        int pillLeft = leftCenterX - pillWidth / 2;
        int pillTop = m.compact ? (y0 + (m.previewH - StatusIcon.CARD_HEIGHT) / 2) : (m.previewH >= 34 ? y0 + 3 : y0 + 2);

        StatusIcon.CardLayout layout = StatusIcon.drawCard((rx0, ry0, rx1, ry1, argb) -> {
            int ix0 = pillLeft + Math.round(rx0 - probe.x0());
            int iy0 = pillTop + Math.round(ry0 - probe.y0());
            int ix1 = Math.max(ix0 + 1, pillLeft + Math.round(rx1 - probe.x0()));
            int iy1 = Math.max(iy0 + 1, pillTop + Math.round(ry1 - probe.y0()));
            rect.fill(ix0, iy0, ix1, iy1, argb);
        }, previewStatus, badgeTextWidth, opacityPct, nowMillis);

        // Mini vanilla nameplate box under the overhead pill in non-compact mode
        int npY0 = pillTop + StatusIcon.CARD_HEIGHT + (m.previewH >= 34 ? 2 : 1);
        if (!m.compact) {
            int npHalf = 15;
            rect.fill(leftCenterX - npHalf, npY0, leftCenterX + npHalf, npY0 + 9, 0x55000000);
        }

        // Authentic Tab-list row backdrop in right half of preview card
        int rightCenterX = midX + (x1 - midX) / 2;
        int tabBoxW = Math.min((x1 - midX) - 10, Math.max(44, tabEntryWidth + 10));
        int tabBoxX0 = rightCenterX - tabBoxW / 2;
        int tabBoxY0 = y0 + (m.previewH - 11) / 2;
        rect.fill(tabBoxX0, tabBoxY0, tabBoxX0 + tabBoxW, tabBoxY0 + 11, 0x33FFFFFF);

        outOrigin[0] = pillLeft + Math.round(layout.textX() - layout.x0());
        outOrigin[1] = pillTop + Math.round(layout.textY() - layout.y0());
        outOrigin[2] = leftCenterX;
        outOrigin[3] = npY0 + 1;
        outOrigin[4] = rightCenterX;
        outOrigin[5] = tabBoxY0 + 2;
        return layout;
    }
}
