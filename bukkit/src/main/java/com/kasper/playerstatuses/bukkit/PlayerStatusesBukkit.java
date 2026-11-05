package com.kasper.playerstatuses.bukkit;

import com.kasper.playerstatuses.Activity;
import com.kasper.playerstatuses.CommandReply;
import com.kasper.playerstatuses.ModLog;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.ServerDetector;
import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusCommands;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The Paper / Purpur / Folia / Spigot / Bukkit plugin: the server half of the mod, without a client.
 * Uses the Bukkit API only, so one jar runs on every one of them from 1.16.5 on.
 */
public final class PlayerStatusesBukkit extends JavaPlugin implements Listener, PluginMessageListener {

    private static final int RELOAD_CHECK_TICKS = 40;

    private static final class TabState {
        final String baseListName;
        String lastDesc;

        TabState(String baseListName, String lastDesc) {
            this.baseListName = baseListName;
            this.lastDesc = lastDesc;
        }
    }

    private final StatusServer core = new StatusServer();
    private final ServerDetector detector = new ServerDetector();
    private final Set<UUID> leftPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, TabState> tabStates = new ConcurrentHashMap<>();
    private Scheduling scheduling;
    private OverheadBridge overheadBridge;
    private int ticks;

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
        if (core.settings().get(ServerSettings.AFK_COMMAND)) {
            registerAfk();
        }
        scheduling.everyTick(this::tick);
    }

    @Override
    public void onDisable() {
        if (overheadBridge != null) {
            overheadBridge.clearAll();
        }
        if (scheduling != null) {
            scheduling.cancelAll();
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!StatusMessages.CHANNEL.equals(channel) || player == null || message == null) {
            return;
        }
        long now = System.currentTimeMillis();
        StatusRelay relay = core.relay();
        UUID id = player.getUniqueId();
        boolean firstTime = !relay.hasMod(id);
        if (!relay.onClientMessage(id, message, now)) {
            return;
        }
        if (firstTime) {
            List<StatusMessages.Entry> active = relay.activeEntries(now);
            for (byte[] packet : StatusMessages.statusesSplit(active)) {
                player.sendPluginMessage(this, StatusMessages.CHANNEL, packet);
            }
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        StatusRelay relay = core.relay();
        Collection<? extends Player> players = getServer().getOnlinePlayers();

        boolean hasVanillaViewer = false;
        for (Player p : players) {
            UUID id = p.getUniqueId();
            detector.updatePlayer(id, core.settings(), relay, now);
            if (!relay.hasMod(id)) {
                hasVanillaViewer = true;
            }
        }

        relay.tick(now);

        List<StatusMessages.Entry> updates = new ArrayList<>(relay.takeChangedEntries(now));
        for (UUID leftId : leftPlayers) {
            updates.add(new StatusMessages.Entry(leftId, PlayerStatus.NONE));
        }
        leftPlayers.clear();

        if (!updates.isEmpty()) {
            List<byte[]> packets = StatusMessages.statusesSplit(updates);
            for (Player p : players) {
                if (relay.hasMod(p.getUniqueId())) {
                    for (byte[] packet : packets) {
                        p.sendPluginMessage(this, StatusMessages.CHANNEL, packet);
                    }
                }
            }
        }

        if (overheadBridge != null) {
            overheadBridge.tick(players, core, core.settings().get(ServerSettings.VANILLA_OVERHEAD), hasVanillaViewer, now);
        }
        tickVanillaTabList(players, relay, hasVanillaViewer, now);

        if (++ticks % RELOAD_CHECK_TICKS == 0) {
            core.reloadIfChanged();
            relay.retainOnline(id -> getServer().getPlayer(id) != null);
            detector.retainOnline(id -> getServer().getPlayer(id) != null);
        }
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
                String base = state != null ? state.baseListName : p.getPlayerListName();
                if (base == null || base.isEmpty()) {
                    base = p.getName();
                }
                tabStates.put(id, new TabState(base, desc));
                p.setPlayerListName(base + " " + ChatColor.GRAY + "[" + desc + "]" + ChatColor.RESET);
            });
        }

        tabStates.keySet().removeIf(id -> !onlineIds.contains(id));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        detector.onPose(
                event.getPlayer().getUniqueId(),
                to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch(),
                core.relay(),
                System.currentTimeMillis()
        );
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        detector.onActed(event.getPlayer().getUniqueId(), core.relay(), System.currentTimeMillis());
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!ServerDetector.isAfkCommand(event.getMessage())) {
            detector.onActed(event.getPlayer().getUniqueId(), core.relay(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        detector.onActed(event.getPlayer().getUniqueId(), core.relay(), System.currentTimeMillis());
    }

    @EventHandler
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            detector.onActed(event.getWhoClicked().getUniqueId(), core.relay(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        detector.remove(id);
        tabStates.remove(id);
        if (overheadBridge != null) {
            overheadBridge.remove(id);
        }
        StatusRelay relay = core.relay();
        if (!relay.current(id, System.currentTimeMillis()).isNone()) {
            leftPlayers.add(id);
        }
        relay.remove(id);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        reply(sender, StatusCommands.run(String.join(" ", args), source(sender), core,
                getServer().getOnlinePlayers().size(), System.currentTimeMillis()));
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
     * plugin; when it is on it is added here (as {@code /playerstatuses:afk} too).
     */
    private void registerAfk() {
        try {
            CommandMap map = (CommandMap) getServer().getClass().getMethod("getCommandMap").invoke(getServer());
            Command afk = new Command(StatusCommands.AFK, "Mark yourself AFK until you move", "/afk", Collections.emptyList()) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    reply(sender, StatusCommands.afk(source(sender), core, System.currentTimeMillis()));
                    return true;
                }
            };
            afk.setPermission("playerstatuses.afk");
            map.register(getName().toLowerCase(Locale.ROOT), afk);
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().warning("Could not add /afk: " + e);
        }
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
