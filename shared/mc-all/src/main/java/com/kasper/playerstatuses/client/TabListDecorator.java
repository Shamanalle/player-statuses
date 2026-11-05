package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.Profiles;
import com.kasper.playerstatuses.StatusClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decorates {@link PlayerInfo#setTabListDisplayName} with each player's status and AFK duration
 * when {@link ClientSettings#TAB_LIST} is enabled, while preserving any custom tab-list name the
 * server set and hiding statuses of invisible players.
 */
public final class TabListDecorator {

    private static final class Entry {
        Component serverBase;
        Component decorated;
        PlayerStatus lastStatus;
        int lastIdleBucket = -1;
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
            PlayerStatus status = (!enabled || invisible) ? PlayerStatus.NONE : client.statusOf(id, now);

            if (status.isNone()) {
                if (entry != null) {
                    info.setTabListDisplayName(entry.serverBase);
                    ENTRIES.remove(id);
                }
                continue;
            }

            int idleBucket = status.idleSeconds() / 60;
            if (entry != null && status.sameState(entry.lastStatus) && idleBucket == entry.lastIdleBucket && current == entry.decorated) {
                continue;
            }

            if (entry == null) {
                entry = new Entry();
                entry.serverBase = current;
                ENTRIES.put(id, entry);
            }
            Component base = cleanServerBase(entry.serverBase, info);
            Component decorated = base.copy().append(StatusText.tabSuffix(status));
            entry.decorated = decorated;
            entry.lastStatus = status;
            entry.lastIdleBucket = idleBucket;
            info.setTabListDisplayName(decorated);
        }
    }

    private static Component cleanServerBase(Component serverBase, PlayerInfo info) {
        Component fallback = PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(Profiles.name(info.getProfile())));
        if (serverBase == null) {
            return fallback;
        }
        String raw = serverBase.getString();
        String name = Profiles.name(info.getProfile());
        if (raw.endsWith("]") && raw.startsWith(name + " [")) {
            return fallback;
        }
        return serverBase;
    }
}
