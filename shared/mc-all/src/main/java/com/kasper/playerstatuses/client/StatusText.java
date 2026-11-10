package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.Activity;
import com.kasper.playerstatuses.IdleText;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusFlag;
import com.kasper.playerstatuses.StatusIcon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Formats localized status labels for overhead cards and the Tab player list.
 */
public final class StatusText {

    private StatusText() {
    }

    /** Short localized label shown inside the overhead card next to the pixel-art icon. */
    public static Component overheadLabel(PlayerStatus status) {
        if (status == null || status.isNone()) {
            return Component.empty();
        }
        String dur = status.has(StatusFlag.AFK) ? IdleText.duration(status.idleSeconds()) : "";
        if (status.activity() != Activity.NONE) {
            MutableComponent label = Component.translatable(status.activity().translationKey());
            if (status.has(StatusFlag.AFK)) {
                String afkPart = dur.isEmpty() ? "AFK" : dur;
                label.append(Component.literal(" \u00b7 " + afkPart).withStyle(ChatFormatting.GOLD));
            }
            return label;
        }
        StatusFlag flag = StatusIcon.primaryFlag(status);
        if (flag == StatusFlag.AFK) {
            if (!dur.isEmpty()) {
                return Component.translatable("gui.playerstatuses.afk_time", dur);
            }
            return Component.translatable(StatusFlag.AFK.translationKey());
        }
        if (flag != null) {
            return Component.translatable(flag.translationKey());
        }
        return Component.empty();
    }

    /** Coloured badge appended to a player's name in the Tab list. */
    public static Component tabSuffix(PlayerStatus status) {
        if (status == null || status.isNone()) {
            return Component.empty();
        }
        Style accent = Style.EMPTY.withColor(TextColor.fromRgb(StatusIcon.accentRgb(status)));
        MutableComponent out = Component.literal(" [").withStyle(ChatFormatting.GRAY);
        out.append(Component.literal(StatusIcon.symbol(status) + " ").setStyle(accent));
        out.append(overheadLabel(status).copy().setStyle(accent));
        for (StatusFlag extra : StatusIcon.secondaryFlags(status)) {
            if (extra == StatusFlag.AFK && status.activity() != Activity.NONE) {
                continue; // Already included in overheadLabel
            }
            Style extraStyle = Style.EMPTY.withColor(TextColor.fromRgb(StatusIcon.sprite(extra).accentRgb()));
            out.append(Component.literal(" " + StatusIcon.symbol(PlayerStatus.of(Activity.NONE, extra))).setStyle(extraStyle));
        }
        out.append(Component.literal("]").withStyle(ChatFormatting.GRAY));
        return out;
    }
}
