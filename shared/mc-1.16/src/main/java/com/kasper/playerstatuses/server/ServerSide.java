package com.kasper.playerstatuses.server;

import com.kasper.playerstatuses.MenuActivities;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.ServerDetector;
import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusMessages;
import com.kasper.playerstatuses.StatusRelay;
import com.kasper.playerstatuses.StatusServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server half on Fabric and Forge (1.16.5).
 */
public final class ServerSide {

    public interface PacketSender {
        void send(ServerPlayer player, byte[] data);
    }

    private static final int RELOAD_CHECK_TICKS = 40;

    private static final StatusServer CORE = new StatusServer();
    private static final ServerDetector DETECTOR = new ServerDetector();
    private static final Set<UUID> LEFT_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Path configDir;
    private static PacketSender sender;
    private static boolean started;
    private static int ticks;

    private ServerSide() {
    }

    public static void init(Path config) {
        configDir = config;
    }

    public static void useSender(PacketSender s) {
        sender = s;
    }

    public static synchronized StatusServer core() {
        if (!started) {
            started = true;
            if (configDir != null) {
                CORE.start(configDir.resolve(ServerSettings.FILE));
            }
        }
        return CORE;
    }

    public static ServerDetector detector() {
        return DETECTOR;
    }

    public static void onClientMessage(ServerPlayer player, byte[] data) {
        if (player == null || data == null) {
            return;
        }
        long now = System.currentTimeMillis();
        StatusRelay relay = core().relay();
        UUID id = player.getUUID();
        boolean firstTime = !relay.hasMod(id);
        if (!relay.onClientMessage(id, data, now)) {
            return;
        }
        if (firstTime && sender != null) {
            List<StatusMessages.Entry> active = relay.activeEntries(now);
            for (byte[] packet : StatusMessages.statusesSplit(active)) {
                sender.send(player, packet);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        StatusServer core = core();
        StatusRelay relay = core.relay();
        long now = System.currentTimeMillis();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        for (ServerPlayer p : players) {
            UUID id = p.getUUID();
            DETECTOR.onPose(id, p.getX(), p.getY(), p.getZ(), p.yRot, p.xRot, relay, now);
            DETECTOR.setOpenContainer(id, MenuActivities.of(p.containerMenu, false), relay, now);
            DETECTOR.updatePlayer(id, core.settings(), relay, now);
        }

        relay.tick(now);

        List<StatusMessages.Entry> updates = new ArrayList<>(relay.takeChangedEntries(now));
        for (UUID leftId : LEFT_PLAYERS) {
            updates.add(new StatusMessages.Entry(leftId, PlayerStatus.NONE));
        }
        LEFT_PLAYERS.clear();

        if (!updates.isEmpty() && sender != null && !players.isEmpty()) {
            List<byte[]> packets = StatusMessages.statusesSplit(updates);
            for (ServerPlayer p : players) {
                if (relay.hasMod(p.getUUID())) {
                    for (byte[] packet : packets) {
                        sender.send(p, packet);
                    }
                }
            }
        }

        VanillaBridge.tick(server, core, now);

        if (++ticks % RELOAD_CHECK_TICKS == 0) {
            core.reloadIfChanged();
            relay.retainOnline(id -> server.getPlayerList().getPlayer(id) != null);
            DETECTOR.retainOnline(id -> server.getPlayerList().getPlayer(id) != null);
        }
    }

    public static void left(ServerPlayer player) {
        UUID id = player.getUUID();
        DETECTOR.remove(id);
        VanillaBridge.removePlayer(id);
        StatusRelay relay = core().relay();
        if (!relay.current(id, System.currentTimeMillis()).isNone()) {
            LEFT_PLAYERS.add(id);
        }
        relay.remove(id);
    }
}
