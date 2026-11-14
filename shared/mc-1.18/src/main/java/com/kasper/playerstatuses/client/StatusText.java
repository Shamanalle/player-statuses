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
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * Formats localized status labels for overhead cards and the Tab player list (1.16.5–1.18.2).
 */
public final class StatusText {

    private StatusText() {
    }

    public static Component overheadLabel(PlayerStatus status) {
        if (status == null || status.isNone()) {
            return TextComponent.EMPTY;
        }
        String dur = status.has(StatusFlag.AFK) ? IdleText.duration(status.idleSeconds()) : "";
        if (status.activity() != Activity.NONE) {
            MutableComponent label = new TranslatableComponent(status.activity().translationKey());
            if (status.has(StatusFlag.AFK)) {
                String afkPart = dur.isEmpty() ? "AFK" : dur;
                label.append(new TextComponent(" \u00b7 " + afkPart).withStyle(ChatFormatting.GOLD));
            }
            return label;
        }
        StatusFlag flag = StatusIcon.primaryFlag(status);
        if (flag == StatusFlag.AFK) {
            if (!dur.isEmpty()) {
                return new TranslatableComponent("gui.playerstatuses.afk_time", dur);
            }
            return new TranslatableComponent(StatusFlag.AFK.translationKey());
        }
        if (flag != null) {
            return new TranslatableComponent(flag.translationKey());
        }
        return TextComponent.EMPTY;
    }

    public static Component tabSuffix(PlayerStatus status) {
        if (status == null || status.isNone()) {
            return TextComponent.EMPTY;
        }
        Style accent = Style.EMPTY.withColor(TextColor.fromRgb(StatusIcon.accentRgb(status)));
        MutableComponent out = new TextComponent(" [").withStyle(ChatFormatting.GRAY);
        out.append(new TextComponent(StatusIcon.symbol(status) + " ").setStyle(accent));
        out.append(overheadLabel(status).copy().setStyle(accent));
        for (StatusFlag extra : StatusIcon.secondaryFlags(status)) {
            if (extra == StatusFlag.AFK && status.activity() != Activity.NONE) {
                continue;
            }
            Style extraStyle = Style.EMPTY.withColor(TextColor.fromRgb(StatusIcon.sprite(extra).accentRgb()));
            out.append(new TextComponent(" " + StatusIcon.symbol(PlayerStatus.of(Activity.NONE, extra))).setStyle(extraStyle));
        }
        out.append(new TextComponent("]").withStyle(ChatFormatting.GRAY));
        return out;
    }

    public static Component holoCallLabel() {
        return new TranslatableComponent("gui.playerstatuses.holo.call");
    }

    public static Component holoPageLabel(int page, int totalPages) {
        return new TranslatableComponent(
            "gui.playerstatuses.holo.page",
            Math.max(1, page),
            Math.max(1, totalPages)
        );
    }

    public static Component holoPingBanner(String senderName, String hintDescId) {
        String safeSender = senderName == null || senderName.isEmpty() ? "?" : senderName;
        String cleanHint = com.kasper.playerstatuses.HoloState.sanitizeDescId(hintDescId);
        if (!cleanHint.isEmpty()) {
            return new TranslatableComponent(
                "gui.playerstatuses.holo.ping_banner_item",
                safeSender,
                new TranslatableComponent(cleanHint)
            );
        }
        return new TranslatableComponent("gui.playerstatuses.holo.ping_banner", safeSender);
    }

    public static Component holoPingSent(String targetName) {
        String safeTarget = targetName == null || targetName.isEmpty() ? "?" : targetName;
        return new TranslatableComponent("gui.playerstatuses.holo.ping_sent", safeTarget);
    }

    public static Component holoKnockReceived(String senderName) {
        String safeSender = senderName == null || senderName.isEmpty() ? "?" : senderName;
        return new TranslatableComponent("gui.playerstatuses.holo.knock_received", safeSender);
    }

    public static Component holoKnockSent(String targetName) {
        String safeTarget = targetName == null || targetName.isEmpty() ? "?" : targetName;
        return new TranslatableComponent("gui.playerstatuses.holo.knock_sent", safeTarget);
    }
}
