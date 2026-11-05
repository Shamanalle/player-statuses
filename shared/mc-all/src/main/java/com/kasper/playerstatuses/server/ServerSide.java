package com.kasper.playerstatuses.server;

import com.kasper.playerstatuses.PlayerStatus;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server half on Fabric, Forge and NeoForge: one {@link StatusServer} for the running server and
 * the loader-independent part of the hooks. Each loader's entrypoint calls these.
 */
public final class ServerSide {

    public interface PacketSender {
        void send(ServerPlayer player, byte[] data);
    }

    private static final int RELOAD_CHECK_TICKS = 40;

    private static final StatusServer CORE = new StatusServer();
    private static final Map<UUID, double[]> LAST_POS = new ConcurrentHashMap<>();
    private static final Set<UUID> LEFT_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Path configDir;
    private static PacketSender sender;
    private static boolean started;
    private static int ticks;

    private ServerSide() {
    }

    /** Where the settings file goes; called by the entrypoint as the mod loads. */
    public static void init(Path config) {
        configDir = config;
    }

    /** Registers the loader-specific packet sender for {@link StatusMessages#CHANNEL}. */
    public static void useSender(PacketSender s) {
        sender = s;
    }

    /**
     * The server core, with its settings read. Commands are registered before the server starts, and
     * {@code /afk} is registered only when it is on, so the settings are read on first use.
     */
    public static synchronized StatusServer core() {
        if (!started) {
            started = true;
            if (configDir != null) {
                CORE.start(configDir.resolve(ServerSettings.FILE));
            }
        }
        return CORE;
    }

    /** Handles an incoming {@link StatusMessages#UPDATE} payload from a modded client. */
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
            double[] prev = LAST_POS.put(id, new double[]{p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()});
            if (prev != null && relay.isManualAfk(id)) {
                if (Math.abs(p.getX() - prev[0]) > 0.02
                        || Math.abs(p.getY() - prev[1]) > 0.02
                        || Math.abs(p.getZ() - prev[2]) > 0.02
                        || Math.abs(p.getYRot() - prev[3]) > 0.5
                        || Math.abs(p.getXRot() - prev[4]) > 0.5) {
                    relay.onActed(id, now);
                }
            }
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

        if (++ticks % RELOAD_CHECK_TICKS == 0) {
            core.reloadIfChanged();
            relay.retainOnline(id -> server.getPlayerList().getPlayer(id) != null);
            LAST_POS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
    }

    public static void left(ServerPlayer player) {
        UUID id = player.getUUID();
        LAST_POS.remove(id);
        StatusRelay relay = core().relay();
        if (!relay.current(id, System.currentTimeMillis()).isNone()) {
            LEFT_PLAYERS.add(id);
        }
        relay.remove(id);
    }
}
