package com.kasper.playerstatuses.server;

import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusCommands;
import com.kasper.playerstatuses.StatusRelay;
import com.kasper.playerstatuses.StatusServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Displays statuses to vanilla clients on a 1.19–1.19.2 server via localized status suffixes in the Tab list.
 * (TextDisplay entities were introduced in 1.19.4.)
 */
public final class VanillaBridge {

    public static final String OVERHEAD_TAG = "playerstatuses.overhead";
    private static final String TAB_INSERTION_TAG = "playerstatuses";
    private static final int JOIN_GRACE_TICKS = 20;

    private static final Field PACKET_ENTRIES = findEntriesField();
    private static Field entryDisplayNameField;

    private static final Map<UUID, Map<UUID, String>> SENT_TAB_NAMES = new HashMap<>();

    private VanillaBridge() {
    }

    public static void tick(MinecraftServer server, StatusServer core, long now) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        StatusRelay relay = core.relay();
        ServerSettings settings = core.settings();
        tickTabList(players, relay, settings, now);
    }

    public static void removePlayer(UUID playerId) {
        SENT_TAB_NAMES.remove(playerId);
        for (Map<UUID, String> map : SENT_TAB_NAMES.values()) {
            map.remove(playerId);
        }
    }

    public static void clearAll() {
        SENT_TAB_NAMES.clear();
    }

    public static int activeOverheadCount() {
        return 0;
    }

    private static void tickTabList(
            List<ServerPlayer> players,
            StatusRelay relay,
            ServerSettings settings,
            long now
    ) {
        boolean enabled = settings.get(ServerSettings.VANILLA_TAB_LIST);
        String fallbackLang = settings.get(ServerSettings.LANGUAGE);

        for (ServerPlayer viewer : players) {
            UUID viewerId = viewer.getUUID();
            if (!enabled || relay.hasMod(viewerId) || viewer.tickCount < JOIN_GRACE_TICKS) {
                Map<UUID, String> prevMap = SENT_TAB_NAMES.remove(viewerId);
                if (prevMap != null && !prevMap.isEmpty()) {
                    for (ServerPlayer target : players) {
                        if (prevMap.containsKey(target.getUUID())) {
                            sendTabDisplayName(viewer, target, null);
                        }
                    }
                }
                continue;
            }

            Map<UUID, String> viewerMap = SENT_TAB_NAMES.computeIfAbsent(viewerId, k -> new HashMap<>());
            String viewerLang = PlayerLanguage.of(viewer);
            if (viewerLang == null || viewerLang.isEmpty()) {
                viewerLang = fallbackLang;
            }

            for (ServerPlayer target : players) {
                UUID targetId = target.getUUID();
                boolean hidden = (target != viewer && target.isInvisibleTo(viewer)) || target.isSpectator();
                PlayerStatus status = hidden ? PlayerStatus.NONE : relay.current(targetId, now);
                String desc = status.isNone() ? "" : StatusCommands.describe(viewerLang, status);
                String prev = viewerMap.getOrDefault(targetId, "");
                if (desc.equals(prev)) {
                    continue;
                }
                if (desc.isEmpty()) {
                    viewerMap.remove(targetId);
                    sendTabDisplayName(viewer, target, null);
                } else {
                    viewerMap.put(targetId, desc);
                    Component base = target.getTabListDisplayName();
                    if (base == null) {
                        base = PlayerTeam.formatNameForTeam(target.getTeam(), target.getName());
                    }
                    Component decorated = base.copy().append(
                            Component.literal(" [" + desc + "]")
                                    .withStyle(s -> s.withColor(ChatFormatting.GRAY).withInsertion(TAB_INSERTION_TAG)));
                    sendTabDisplayName(viewer, target, decorated);
                }
            }
        }
    }

    private static void sendTabDisplayName(ServerPlayer viewer, ServerPlayer target, Component customName) {
        try {
            ClientboundPlayerInfoPacket pkt = new ClientboundPlayerInfoPacket(
                    ClientboundPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME, target);
            if (customName != null && PACKET_ENTRIES != null) {
                List<?> entries = (List<?>) PACKET_ENTRIES.get(pkt);
                if (entries != null && !entries.isEmpty()) {
                    Object entry = entries.get(0);
                    Field f = getDisplayNameField(entry.getClass());
                    if (f != null) {
                        f.set(entry, customName);
                    }
                }
            }
            viewer.connection.send(pkt);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Field findEntriesField() {
        for (Field f : ClientboundPlayerInfoPacket.class.getDeclaredFields()) {
            if (f.getType() == List.class) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    private static Field getDisplayNameField(Class<?> entryClass) {
        if (entryDisplayNameField != null) {
            return entryDisplayNameField;
        }
        for (Field f : entryClass.getDeclaredFields()) {
            if (f.getType() == Component.class) {
                f.setAccessible(true);
                entryDisplayNameField = f;
                return f;
            }
        }
        return null;
    }
}
