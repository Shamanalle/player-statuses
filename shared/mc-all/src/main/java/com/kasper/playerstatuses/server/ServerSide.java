package com.kasper.playerstatuses.server;

import com.kasper.playerstatuses.HoloState;
import com.kasper.playerstatuses.MenuActivities;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.ServerDetector;
import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusFlag;
import com.kasper.playerstatuses.StatusMessages;
import com.kasper.playerstatuses.StatusRelay;
import com.kasper.playerstatuses.StatusServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    private static final ServerDetector DETECTOR = new ServerDetector();
    private static final Set<UUID> LEFT_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID> LEFT_PROJECTIONS = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private static final Queue<HoloState.Event> PENDING_EVENTS = new ConcurrentLinkedQueue<HoloState.Event>();
    private static volatile Field entityLevelField;
    private static volatile boolean entityLevelResolved;
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

    public static ServerDetector detector() {
        return DETECTOR;
    }

    /** Handles an incoming {@link StatusMessages} payload from a modded client. */
    public static void onClientMessage(ServerPlayer player, byte[] data) {
        if (player == null || data == null) {
            return;
        }
        long now = System.currentTimeMillis();
        StatusRelay relay = core().relay();
        UUID id = player.getUUID();
        if (StatusMessages.peekKind(data) == StatusMessages.HOLO_INTERACT) {
            HoloState.Event ev = relay.validateInteract(id, data, now);
            if (ev != null) {
                PENDING_EVENTS.add(ev);
            }
            return;
        }
        boolean firstTime = !relay.hasMod(id);
        if (!relay.onClientMessage(id, data, now)) {
            return;
        }
        if (firstTime && sender != null) {
            List<StatusMessages.Entry> active = relay.activeEntries(now);
            for (byte[] packet : StatusMessages.statusesSplit(active)) {
                sender.send(player, packet);
            }
            List<StatusMessages.ProjectionEntry> activeProj = relay.activeProjections();
            for (byte[] packet : StatusMessages.projectionBroadcastSplit(activeProj)) {
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
            if (p.isPassenger()) {
                DETECTOR.onHeadPose(id, p.getYRot(), p.getXRot(), relay, now);
            } else {
                DETECTOR.onPose(id, p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(), relay, now);
            }
            DETECTOR.setOpenContainer(id, MenuActivities.of(p.containerMenu, false), relay, now);
            DETECTOR.updatePlayer(id, core.settings(), relay, now);
        }

        relay.tick(now);

        List<StatusMessages.Entry> updates = new ArrayList<>(relay.takeChangedEntries(now));
        for (Iterator<UUID> it = LEFT_PLAYERS.iterator(); it.hasNext(); ) {
            updates.add(new StatusMessages.Entry(it.next(), PlayerStatus.NONE));
            it.remove();
        }

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

        List<StatusMessages.ProjectionEntry> projUpdates = new ArrayList<StatusMessages.ProjectionEntry>(relay.takeChangedProjectionEntries());
        for (Iterator<UUID> it = LEFT_PROJECTIONS.iterator(); it.hasNext(); ) {
            UUID leftId = it.next();
            if (relay.currentProjection(leftId).isNone()) {
                projUpdates.add(new StatusMessages.ProjectionEntry(leftId, HoloState.Projection.EMPTY));
            }
            it.remove();
        }
        if (!projUpdates.isEmpty() && sender != null && !players.isEmpty()) {
            List<byte[]> projPackets = StatusMessages.projectionBroadcastSplit(projUpdates);
            for (ServerPlayer p : players) {
                if (relay.hasMod(p.getUUID())) {
                    for (byte[] packet : projPackets) {
                        sender.send(p, packet);
                    }
                }
            }
        }

        for (HoloState.Event ev; (ev = PENDING_EVENTS.poll()) != null; ) {
            if (sender == null) {
                continue;
            }
            ServerPlayer actor = server.getPlayerList().getPlayer(ev.sender());
            ServerPlayer target = server.getPlayerList().getPlayer(ev.target());
            if (actor == null || target == null || actor.isSpectator() || target.isSpectator() || target.isInvisible()) {
                continue;
            }
            Level targetLevel = entityLevel(target);
            if (targetLevel == null || targetLevel != entityLevel(actor)) {
                continue;
            }
            double adx = actor.getX() - target.getX();
            double ady = actor.getY() - target.getY();
            double adz = actor.getZ() - target.getZ();
            if (adx * adx + ady * ady + adz * adz > 36.0) {
                continue;
            }
            if (ev.action() == HoloState.Action.KNOCK_AFK
                    && !relay.current(ev.target(), now).has(StatusFlag.AFK)
                    && relay.currentProjection(ev.target()).layout() != HoloState.LayoutKind.IDLE_CARD) {
                continue;
            }
            byte[] packet = StatusMessages.holoEvent(ev);
            for (ServerPlayer viewer : players) {
                UUID vid = viewer.getUUID();
                if (!relay.hasMod(vid)) {
                    continue;
                }
                if (vid.equals(ev.target()) || vid.equals(ev.sender())) {
                    sender.send(viewer, packet);
                    continue;
                }
                if (entityLevel(viewer) != targetLevel) {
                    continue;
                }
                double vdx = viewer.getX() - target.getX();
                double vdy = viewer.getY() - target.getY();
                double vdz = viewer.getZ() - target.getZ();
                if (vdx * vdx + vdy * vdy + vdz * vdz <= 576.0) {
                    sender.send(viewer, packet);
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
        if (!relay.currentProjection(id).isNone()) {
            LEFT_PROJECTIONS.add(id);
        }
        relay.remove(id);
    }

    /** Discards overhead entities and clears runtime state before the server saves chunks and stops. */
    public static void stop() {
        LEFT_PLAYERS.clear();
        LEFT_PROJECTIONS.clear();
        PENDING_EVENTS.clear();
        DETECTOR.retainOnline(id -> false);
        core().relay().retainOnline(id -> false);
        VanillaBridge.clearAll();
    }

    private static Level entityLevel(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        if (!entityLevelResolved) {
            for (Field f : Entity.class.getDeclaredFields()) {
                if (f.getType() == Level.class) {
                    f.setAccessible(true);
                    entityLevelField = f;
                    break;
                }
            }
            entityLevelResolved = true;
        }
        Field f = entityLevelField;
        if (f != null) {
            try {
                return (Level) f.get(player);
            } catch (IllegalAccessException ignored) {
            }
        }
        return null;
    }
}
