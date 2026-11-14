package com.kasper.playerstatuses.bukkit;

import com.kasper.playerstatuses.Activity;
import com.kasper.playerstatuses.CommandReply;
import com.kasper.playerstatuses.HoloState;
import com.kasper.playerstatuses.ModLog;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.ServerDetector;
import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusCommands;
import com.kasper.playerstatuses.StatusFlag;
import com.kasper.playerstatuses.StatusMessages;
import com.kasper.playerstatuses.StatusRelay;
import com.kasper.playerstatuses.StatusServer;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * The Paper / Purpur / Folia / Spigot / Bukkit plugin: the server half of the mod, without a client.
 * Uses the Bukkit API only, so one jar runs on every one of them from 1.16.5 on.
 */
public final class PlayerStatusesBukkit extends JavaPlugin implements Listener, PluginMessageListener {

    private static final int RELOAD_CHECK_TICKS = 40;
    private static final long JOIN_GRACE_MS = 1_000L;

    private static final class TabState {
        final String baseListName;
        String lastDesc;

        TabState(String baseListName, String lastDesc) {
            this.baseListName = baseListName;
            this.lastDesc = lastDesc;
        }
    }

    private static final class PosSnapshot {
        final String worldName;
        final double x;
        final double y;
        final double z;
        final boolean spectator;

        PosSnapshot(String worldName, double x, double y, double z, boolean spectator) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.spectator = spectator;
        }
    }

    private final StatusServer core = new StatusServer();
    private final ServerDetector detector = new ServerDetector();
    private final Set<UUID> leftPlayers = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> leftProjections = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> vanishedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private final Map<UUID, TabState> tabStates = new ConcurrentHashMap<>();
    private final Map<UUID, PosSnapshot> positions = new ConcurrentHashMap<>();
    private final Queue<HoloState.Event> pendingEvents = new ConcurrentLinkedQueue<HoloState.Event>();
    private Scheduling scheduling;
    private OverheadBridge overheadBridge;
    private Command registeredAfkCommand;
    private int ticks;

    static boolean isVanished(Player player) {
        if (player == null) {
            return false;
        }
        try {
            for (MetadataValue meta : player.getMetadata("vanished")) {
                if (meta != null && meta.asBoolean()) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    @Override
    public void onEnable() {
        ModLog.use((level, text) -> getLogger().log(level == ModLog.Level.INFO ? Level.INFO
                : level == ModLog.Level.WARN ? Level.WARNING : Level.SEVERE, text));
        core.start(getDataFolder().toPath().resolve(ServerSettings.FILE));
        scheduling = Scheduling.create(this);
        overheadBridge = OverheadBridge.create(this, scheduling);
        getServer().getMessenger().registerIncomingPluginChannel(this, StatusMessages.CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, StatusMessages.CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        syncAfkCommand(core.settings().get(ServerSettings.AFK_COMMAND));
        for (Player p : getServer().getOnlinePlayers()) {
            scheduling.runOnEntity(p, () -> {
                if (p.isOnline()) {
                    recordPos(p, p.getLocation());
                }
            });
        }
        scheduling.everyTick(this::tick);
    }

    @Override
    public void onDisable() {
        try {
            if (overheadBridge != null) {
                overheadBridge.clearAll();
            }
        } catch (RuntimeException ignored) {
        }
        try {
            restoreAllTabNames();
        } catch (RuntimeException ignored) {
        }
        try {
            if (scheduling != null) {
                scheduling.cancelAll();
            }
        } catch (RuntimeException ignored) {
        }
        try {
            syncAfkCommand(false);
        } catch (RuntimeException ignored) {
        }
        try {
            getServer().getMessenger().unregisterIncomingPluginChannel(this);
            getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        } catch (RuntimeException ignored) {
        }
        leftPlayers.clear();
        leftProjections.clear();
        vanishedPlayers.clear();
        joinedAt.clear();
        tabStates.clear();
        positions.clear();
        pendingEvents.clear();
    }

    private void restoreAllTabNames() {
        if (!getServer().isPrimaryThread()) {
            return;
        }
        for (Map.Entry<UUID, TabState> e : tabStates.entrySet()) {
            Player p = getServer().getPlayer(e.getKey());
            if (p != null && p.isOnline()) {
                p.setPlayerListName(e.getValue().baseListName);
            }
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!StatusMessages.CHANNEL.equals(channel) || player == null || message == null) {
            return;
        }
        long now = System.currentTimeMillis();
        StatusRelay relay = core.relay();
        UUID id = player.getUniqueId();
        if (StatusMessages.peekKind(message) == StatusMessages.HOLO_INTERACT) {
            HoloState.Event ev = relay.validateInteract(id, message, now);
            if (ev != null) {
                pendingEvents.add(ev);
            }
            return;
        }
        boolean firstTime = !relay.hasMod(id);
        if (!relay.onClientMessage(id, message, now)) {
            return;
        }
        if (firstTime) {
            List<StatusMessages.Entry> visible = filterVisibleForJoin(
                    player, id, relay.activeEntries(now), StatusMessages.Entry::player);
            for (byte[] packet : StatusMessages.statusesSplit(visible)) {
                player.sendPluginMessage(this, StatusMessages.CHANNEL, packet);
            }
            List<StatusMessages.ProjectionEntry> visibleProj = filterVisibleForJoin(
                    player, id, relay.activeProjections(), StatusMessages.ProjectionEntry::player);
            for (byte[] packet : StatusMessages.projectionBroadcastSplit(visibleProj)) {
                player.sendPluginMessage(this, StatusMessages.CHANNEL, packet);
            }
        }
    }

    private <T> List<T> filterVisibleForJoin(
            Player viewer,
            UUID viewerId,
            List<T> entries,
            Function<T, UUID> targetFn
    ) {
        List<T> visible = new ArrayList<T>(entries.size());
        for (T entry : entries) {
            UUID targetId = targetFn.apply(entry);
            if (targetId.equals(viewerId)) {
                visible.add(entry);
                continue;
            }
            if (vanishedPlayers.contains(targetId)) {
                continue;
            }
            Player target = getServer().getPlayer(targetId);
            if (target != null && !isVanished(target) && viewer.canSee(target)) {
                visible.add(entry);
            }
        }
        return visible;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        StatusRelay relay = core.relay();
        Collection<? extends Player> players = getServer().getOnlinePlayers();

        boolean hasVanillaViewer = false;
        List<StatusMessages.Entry> visibilityTransitions = new ArrayList<>();
        List<StatusMessages.ProjectionEntry> projVisibilityTransitions = new ArrayList<StatusMessages.ProjectionEntry>();
        for (Player p : players) {
            UUID id = p.getUniqueId();
            detector.updatePlayer(id, core.settings(), relay, now);
            boolean vanished = isVanished(p);
            if (vanished) {
                if (vanishedPlayers.add(id)) {
                    visibilityTransitions.add(new StatusMessages.Entry(id, PlayerStatus.NONE));
                    if (!relay.currentProjection(id).isNone()) {
                        projVisibilityTransitions.add(new StatusMessages.ProjectionEntry(id, HoloState.Projection.EMPTY));
                    }
                }
            } else {
                if (vanishedPlayers.remove(id)) {
                    PlayerStatus cur = relay.current(id, now);
                    if (!cur.isNone()) {
                        visibilityTransitions.add(new StatusMessages.Entry(id, cur));
                    }
                    HoloState.Projection curProj = relay.currentProjection(id);
                    if (!curProj.isNone()) {
                        projVisibilityTransitions.add(new StatusMessages.ProjectionEntry(id, curProj));
                    }
                }
                if (!relay.hasMod(id) && (now - joinedAt.getOrDefault(id, 0L)) >= JOIN_GRACE_MS) {
                    hasVanillaViewer = true;
                }
            }
        }

        relay.tick(now);

        List<StatusMessages.Entry> rawUpdates = relay.takeChangedEntries(now);
        List<StatusMessages.Entry> updates = new ArrayList<>(rawUpdates.size() + visibilityTransitions.size());
        for (StatusMessages.Entry entry : rawUpdates) {
            if (!vanishedPlayers.contains(entry.player()) || entry.status().isNone()) {
                updates.add(entry);
            }
        }
        updates.addAll(visibilityTransitions);
        for (Iterator<UUID> it = leftPlayers.iterator(); it.hasNext(); ) {
            updates.add(new StatusMessages.Entry(it.next(), PlayerStatus.NONE));
            it.remove();
        }

        if (!updates.isEmpty()) {
            sendEntriesToModdedViewers(
                    players,
                    relay,
                    updates,
                    StatusMessages.Entry::player,
                    entry -> entry.status().isNone(),
                    StatusMessages::statusesSplit
            );
        }

        List<StatusMessages.ProjectionEntry> rawProjUpdates = relay.takeChangedProjectionEntries();
        List<StatusMessages.ProjectionEntry> projUpdates = new ArrayList<StatusMessages.ProjectionEntry>(
                rawProjUpdates.size() + projVisibilityTransitions.size());
        for (StatusMessages.ProjectionEntry entry : rawProjUpdates) {
            if (!vanishedPlayers.contains(entry.player()) || entry.projection().isNone()) {
                projUpdates.add(entry);
            }
        }
        projUpdates.addAll(projVisibilityTransitions);
        for (Iterator<UUID> it = leftProjections.iterator(); it.hasNext(); ) {
            UUID leftId = it.next();
            if (relay.currentProjection(leftId).isNone()) {
                projUpdates.add(new StatusMessages.ProjectionEntry(leftId, HoloState.Projection.EMPTY));
            }
            it.remove();
        }
        if (!projUpdates.isEmpty()) {
            sendEntriesToModdedViewers(
                    players,
                    relay,
                    projUpdates,
                    StatusMessages.ProjectionEntry::player,
                    entry -> entry.projection().isNone(),
                    StatusMessages::projectionBroadcastSplit
            );
        }

        relayPendingHoloEvents(players, relay, now);

        if (overheadBridge != null) {
            overheadBridge.tick(players, core, core.settings().get(ServerSettings.VANILLA_OVERHEAD), hasVanillaViewer, now);
        }
        tickVanillaTabList(players, relay, hasVanillaViewer, now);

        if (++ticks % RELOAD_CHECK_TICKS == 0) {
            if (core.reloadIfChanged()) {
                syncAfkCommand(core.settings().get(ServerSettings.AFK_COMMAND));
            }
            relay.retainOnline(id -> getServer().getPlayer(id) != null);
            detector.retainOnline(id -> getServer().getPlayer(id) != null);
            vanishedPlayers.removeIf(id -> getServer().getPlayer(id) == null);
            joinedAt.keySet().removeIf(id -> getServer().getPlayer(id) == null);
            positions.keySet().removeIf(id -> getServer().getPlayer(id) == null);
        }
    }

    private <T> void sendEntriesToModdedViewers(
            Collection<? extends Player> players,
            StatusRelay relay,
            List<T> entries,
            Function<T, UUID> targetFn,
            Predicate<T> isClearFn,
            Function<List<T>, List<byte[]>> splitFn
    ) {
        List<byte[]> sharedPackets = null;
        for (Player viewer : players) {
            UUID viewerId = viewer.getUniqueId();
            if (!relay.hasMod(viewerId)) {
                continue;
            }
            boolean needsCustomFilter = false;
            for (T entry : entries) {
                UUID targetId = targetFn.apply(entry);
                if (!targetId.equals(viewerId) && !isClearFn.test(entry)) {
                    Player target = getServer().getPlayer(targetId);
                    if (target == null || !viewer.canSee(target)) {
                        needsCustomFilter = true;
                        break;
                    }
                }
            }
            List<byte[]> packets;
            if (!needsCustomFilter) {
                if (sharedPackets == null) {
                    sharedPackets = splitFn.apply(entries);
                }
                packets = sharedPackets;
            } else {
                List<T> filtered = new ArrayList<T>(entries.size());
                for (T entry : entries) {
                    UUID targetId = targetFn.apply(entry);
                    if (targetId.equals(viewerId) || isClearFn.test(entry)) {
                        filtered.add(entry);
                    } else {
                        Player target = getServer().getPlayer(targetId);
                        if (target != null && viewer.canSee(target)) {
                            filtered.add(entry);
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    continue;
                }
                packets = splitFn.apply(filtered);
            }
            for (byte[] packet : packets) {
                viewer.sendPluginMessage(this, StatusMessages.CHANNEL, packet);
            }
        }
    }

    private void relayPendingHoloEvents(Collection<? extends Player> players, StatusRelay relay, long now) {
        for (HoloState.Event ev; (ev = pendingEvents.poll()) != null; ) {
            Player actor = getServer().getPlayer(ev.sender());
            Player target = getServer().getPlayer(ev.target());
            if (actor == null || target == null || !actor.isOnline() || !target.isOnline()
                    || vanishedPlayers.contains(ev.sender()) || vanishedPlayers.contains(ev.target())
                    || isVanished(actor) || isVanished(target) || !actor.canSee(target)) {
                continue;
            }
            PosSnapshot actorPos = positions.get(ev.sender());
            PosSnapshot targetPos = positions.get(ev.target());
            if (actorPos == null || targetPos == null || actorPos.spectator || targetPos.spectator
                    || targetPos.worldName == null || !targetPos.worldName.equals(actorPos.worldName)) {
                continue;
            }
            double adx = actorPos.x - targetPos.x;
            double ady = actorPos.y - targetPos.y;
            double adz = actorPos.z - targetPos.z;
            if (adx * adx + ady * ady + adz * adz > 36.0) {
                continue;
            }
            if (ev.action() == HoloState.Action.KNOCK_AFK
                    && !relay.current(ev.target(), now).has(StatusFlag.AFK)
                    && relay.currentProjection(ev.target()).layout() != HoloState.LayoutKind.IDLE_CARD) {
                continue;
            }
            byte[] packet = StatusMessages.holoEvent(ev);
            for (Player viewer : players) {
                UUID vid = viewer.getUniqueId();
                if (!relay.hasMod(vid)) {
                    continue;
                }
                if (vid.equals(ev.target()) || vid.equals(ev.sender())) {
                    viewer.sendPluginMessage(this, StatusMessages.CHANNEL, packet);
                    continue;
                }
                PosSnapshot viewerPos = positions.get(vid);
                if (viewerPos == null || !targetPos.worldName.equals(viewerPos.worldName)) {
                    continue;
                }
                double vdx = viewerPos.x - targetPos.x;
                double vdy = viewerPos.y - targetPos.y;
                double vdz = viewerPos.z - targetPos.z;
                if (vdx * vdx + vdy * vdy + vdz * vdz <= 576.0 && viewer.canSee(target) && viewer.canSee(actor)) {
                    viewer.sendPluginMessage(this, StatusMessages.CHANNEL, packet);
                }
            }
        }
    }

    private void recordPos(Player player, Location loc) {
        if (player != null) {
            recordPos(player, loc, player.getGameMode() == GameMode.SPECTATOR);
        }
    }

    private void recordPos(Player player, Location loc, boolean spectator) {
        if (player == null || loc == null) {
            return;
        }
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : null;
        positions.put(player.getUniqueId(), new PosSnapshot(worldName, loc.getX(), loc.getY(), loc.getZ(), spectator));
    }

    private void tickVanillaTabList(Collection<? extends Player> players, StatusRelay relay, boolean hasVanillaViewer, long now) {
        boolean enabled = core.settings().get(ServerSettings.VANILLA_TAB_LIST) && hasVanillaViewer;
        String lang = core.settings().get(ServerSettings.LANGUAGE);
        Set<UUID> onlineIds = new HashSet<>();

        for (Player p : players) {
            UUID id = p.getUniqueId();
            onlineIds.add(id);
            scheduling.runOnEntity(p, () -> {
                if (!p.isOnline()) {
                    return;
                }
                boolean hidden = !enabled
                        || isVanished(p)
                        || p.getGameMode() == GameMode.SPECTATOR
                        || p.hasPotionEffect(PotionEffectType.INVISIBILITY);
                PlayerStatus status = hidden ? PlayerStatus.NONE : relay.current(id, now);
                String desc = status.isNone() ? "" : StatusCommands.describe(lang, status);
                TabState state = tabStates.get(id);
                if (desc.isEmpty()) {
                    if (state != null) {
                        tabStates.remove(id);
                        p.setPlayerListName(state.baseListName);
                    }
                    return;
                }
                if (state != null && desc.equals(state.lastDesc)) {
                    return;
                }
                String base = state != null ? state.baseListName : ServerDetector.stripTabMarker(p.getPlayerListName());
                if (base == null || base.isEmpty()) {
                    base = p.getName();
                }
                tabStates.put(id, new TabState(base, desc));
                p.setPlayerListName(base + ServerDetector.TAB_MARKER + " " + ChatColor.GRAY + "[" + desc + "]" + ChatColor.RESET);
            });
        }

        tabStates.keySet().removeIf(id -> !onlineIds.contains(id));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        joinedAt.put(player.getUniqueId(), System.currentTimeMillis());
        recordPos(player, player.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Player player = event.getPlayer();
        recordPos(player, to);
        long now = System.currentTimeMillis();
        if (player.isInsideVehicle()) {
            detector.onHeadPose(player.getUniqueId(), to.getYaw(), to.getPitch(), core.relay(), now);
        } else {
            detector.onPose(
                    player.getUniqueId(),
                    to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch(),
                    core.relay(),
                    now
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to != null) {
            recordPos(event.getPlayer(), to);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        recordPos(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        recordPos(player, player.getLocation(), event.getNewGameMode() == GameMode.SPECTATOR);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        detector.onActed(event.getPlayer().getUniqueId(), core.relay(), System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!ServerDetector.isAfkCommand(event.getMessage())) {
            detector.onActed(event.getPlayer().getUniqueId(), core.relay(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            detector.onActed(event.getPlayer().getUniqueId(), core.relay(), System.currentTimeMillis());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            String type = event.getInventory() != null && event.getInventory().getType() != null
                    ? event.getInventory().getType().name() : "";
            detector.setOpenContainer(
                    player.getUniqueId(),
                    ServerDetector.fromBukkitInventoryType(type),
                    core.relay(),
                    System.currentTimeMillis()
            );
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            detector.setOpenContainer(
                    event.getPlayer().getUniqueId(),
                    Activity.NONE,
                    core.relay(),
                    System.currentTimeMillis()
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            detector.onActed(event.getWhoClicked().getUniqueId(), core.relay(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean wasVanished = vanishedPlayers.remove(id) || isVanished(player);
        joinedAt.remove(id);
        positions.remove(id);
        detector.remove(id);
        tabStates.remove(id);
        if (overheadBridge != null) {
            overheadBridge.remove(id);
        }
        StatusRelay relay = core.relay();
        if (!wasVanished && !relay.current(id, System.currentTimeMillis()).isNone()) {
            leftPlayers.add(id);
        }
        if (!wasVanished && !relay.currentProjection(id).isNone()) {
            leftProjections.add(id);
        }
        relay.remove(id);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CommandReply out = StatusCommands.run(String.join(" ", args), source(sender), core,
                getServer().getOnlinePlayers().size(), System.currentTimeMillis());
        syncAfkCommand(core.settings().get(ServerSettings.AFK_COMMAND));
        reply(sender, out);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String s : Arrays.asList("status", "reload", "help")) {
            if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * {@code /afk} is not in plugin.yml, so a server that turns it off keeps the {@code /afk} of another
     * plugin; when toggled via {@code server.json} or {@code /playerstatuses reload}, it registers or
     * unregisters dynamically.
     */
    private void syncAfkCommand(boolean shouldRegister) {
        if (shouldRegister) {
            if (registeredAfkCommand != null) {
                return;
            }
            try {
                CommandMap map = findCommandMap();
                if (map == null) {
                    return;
                }
                Command afk = new Command(StatusCommands.AFK, "Mark yourself AFK until you move", "/afk", Collections.emptyList()) {
                    @Override
                    public boolean execute(CommandSender sender, String label, String[] args) {
                        reply(sender, StatusCommands.afk(source(sender), core, System.currentTimeMillis()));
                        return true;
                    }
                };
                afk.setPermission("playerstatuses.afk");
                map.register(getName().toLowerCase(Locale.ROOT), afk);
                registeredAfkCommand = afk;
            } catch (ReflectiveOperationException | RuntimeException e) {
                getLogger().warning("Could not add /afk: " + e);
            }
        } else if (registeredAfkCommand != null) {
            Command toRemove = registeredAfkCommand;
            registeredAfkCommand = null;
            try {
                CommandMap map = findCommandMap();
                if (map != null) {
                    toRemove.unregister(map);
                    Map<?, ?> known = findKnownCommands(map);
                    if (known != null) {
                        for (Iterator<? extends Map.Entry<?, ?>> it = known.entrySet().iterator(); it.hasNext(); ) {
                            if (it.next().getValue() == toRemove) {
                                it.remove();
                            }
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
    }

    private CommandMap findCommandMap() throws ReflectiveOperationException {
        Method m = getServer().getClass().getMethod("getCommandMap");
        return (CommandMap) m.invoke(getServer());
    }

    private static Map<?, ?> findKnownCommands(CommandMap map) {
        try {
            Method m = map.getClass().getMethod("getKnownCommands");
            Object v = m.invoke(map);
            if (v instanceof Map) {
                return (Map<?, ?>) v;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        Class<?> cls = map.getClass();
        while (cls != null) {
            for (Field f : cls.getDeclaredFields()) {
                if ("knownCommands".equals(f.getName()) && Map.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (Map<?, ?>) f.get(map);
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private StatusCommands.Source source(CommandSender sender) {
        Player player = sender instanceof Player ? (Player) sender : null;
        return new StatusCommands.Source() {
            @Override
            public UUID player() {
                return player == null ? null : player.getUniqueId();
            }

            @Override
            public String language() {
                return player == null ? "" : player.getLocale();
            }

            @Override
            public boolean isAdmin() {
                return player == null || sender.hasPermission("playerstatuses.admin");
            }
        };
    }

    private static void reply(CommandSender sender, CommandReply reply) {
        for (CommandReply.Line line : reply.lines()) {
            StringBuilder b = new StringBuilder();
            for (CommandReply.Span span : line.spans()) {
                b.append(color(span.style())).append(span.text());
            }
            sender.sendMessage(b.toString());
        }
    }

    private static ChatColor color(CommandReply.Style style) {
        switch (style) {
            case TITLE:
                return ChatColor.GOLD;
            case LABEL:
                return ChatColor.GRAY;
            case OK:
                return ChatColor.GREEN;
            case WARN:
                return ChatColor.YELLOW;
            case ERROR:
                return ChatColor.RED;
            case MUTED:
                return ChatColor.DARK_GRAY;
            default:
                return ChatColor.WHITE;
        }
    }
}
