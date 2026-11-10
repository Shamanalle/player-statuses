package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.Profiles;
import com.kasper.playerstatuses.ServerDetector;
import com.kasper.playerstatuses.StatusClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decorates {@link PlayerInfo#setTabListDisplayName} with each player's status and AFK duration (1.16.5–1.18.2).
 */
public final class TabListDecorator {

    public static final String TAB_INSERTION_TAG = "playerstatuses";

    private static final class Entry {
        Component serverBase;
        Component decorated;
        PlayerStatus lastStatus;
        int lastIdleBucket = -1;
        String lastBaseKey = "";
    }

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    private TabListDecorator() {
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static void tick(Minecraft mc, StatusClient client, long now) {
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || mc.level == null || mc.player == null) {
            ENTRIES.clear();
            return;
        }
        boolean enabled = client.settings().get(ClientSettings.TAB_LIST);
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            UUID id = Profiles.id(info.getProfile());
            if (id == null) {
                continue;
            }
            Component current = info.getTabListDisplayName();
            Entry entry = ENTRIES.get(id);
            if (entry != null && current != entry.decorated) {
                entry.serverBase = current;
                entry.lastStatus = null;
            }

            Player worldPlayer = mc.level.getPlayerByUUID(id);
            boolean invisible = worldPlayer != null && worldPlayer != mc.player && worldPlayer.isInvisibleTo(mc.player);
            boolean spectator = info.getGameMode() == GameType.SPECTATOR
                    || (worldPlayer != null && worldPlayer.isSpectator());
            PlayerStatus status = (!enabled || invisible || spectator) ? PlayerStatus.NONE : client.statusOf(id, now);

            if (status.isNone()) {
                if (entry != null) {
                    info.setTabListDisplayName(entry.serverBase);
                    ENTRIES.remove(id);
                }
                continue;
            }

            Component base = cleanServerBase(entry != null ? entry.serverBase : current, info);
            PlayerTeam team = info.getTeam();
            String baseKey = (team != null ? team.getName() : "") + "|" + base.getString();
            int idleBucket = status.idleSeconds() / 60;
            if (entry != null
                    && status.sameState(entry.lastStatus)
                    && idleBucket == entry.lastIdleBucket
                    && baseKey.equals(entry.lastBaseKey)
                    && current == entry.decorated) {
                continue;
            }

            if (entry == null) {
                entry = new Entry();
                entry.serverBase = current;
                ENTRIES.put(id, entry);
            }
            Component decorated = base.copy().append(StatusText.tabSuffix(status));
            entry.decorated = decorated;
            entry.lastStatus = status;
            entry.lastIdleBucket = idleBucket;
            entry.lastBaseKey = baseKey;
            info.setTabListDisplayName(decorated);
        }
    }

    private static Component cleanServerBase(Component serverBase, PlayerInfo info) {
        Component fallback = PlayerTeam.formatNameForTeam(info.getTeam(), new TextComponent(Profiles.name(info.getProfile())));
        if (serverBase == null) {
            return fallback;
        }
        List<Component> siblings = serverBase.getSiblings();
        if (!siblings.isEmpty()) {
            Component last = siblings.get(siblings.size() - 1);
            if (TAB_INSERTION_TAG.equals(last.getStyle().getInsertion())) {
                MutableComponent trimmed = serverBase.plainCopy().setStyle(serverBase.getStyle());
                for (int i = 0; i < siblings.size() - 1; i++) {
                    trimmed.append(siblings.get(i));
                }
                return trimmed.getString().isEmpty() ? fallback : trimmed;
            }
        }
        String raw = serverBase.getString();
        if (raw.contains(ServerDetector.TAB_MARKER)) {
            String stripped = ServerDetector.stripTabMarker(raw);
            return stripped.isEmpty() ? fallback : new TextComponent(stripped).setStyle(serverBase.getStyle());
        }
        return serverBase;
    }
}
