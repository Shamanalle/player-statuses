package com.kasper.playerstatuses.bukkit;

import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusCommands;
import com.kasper.playerstatuses.StatusServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns and updates non-persistent {@link TextDisplay} entities above players on Bukkit/Spigot/Paper/Folia
 * 1.19.4+. Loaded only when {@link TextDisplay} exists at runtime.
 */
final class BukkitTextDisplays implements OverheadBridge {

    private static final String OVERHEAD_TAG = "playerstatuses.overhead";

    private static final class Entry {
        volatile TextDisplay display;
        volatile String lastText;
        final Set<UUID> hiddenFrom = ConcurrentHashMap.newKeySet();

        Entry(TextDisplay display, String lastText) {
            this.display = display;
            this.lastText = lastText;
        }
    }

    private final JavaPlugin plugin;
    private final Scheduling scheduling;
    private final boolean folia;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Set<UUID> purgedWorlds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private int tickCounter;

    BukkitTextDisplays(JavaPlugin plugin, Scheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        this.folia = Scheduling.isFolia();
    }

    @Override
    public void tick(Collection<? extends Player> players, StatusServer core, boolean enabled, boolean hasVanillaViewer, long now) {
        if (!folia) {
            purgeWorldsOnce();
        }
        if (!enabled || !hasVanillaViewer) {
            if (!entries.isEmpty()) {
                clearAll();
            }
            return;
        }

        Map<UUID, String> teamNames = new HashMap<>();
        Map<UUID, Team.OptionStatus> teamVisibility = new HashMap<>();
        snapshotTeams(players, teamNames, teamVisibility);

        Set<UUID> onlineIds = new HashSet<>();
        for (Player target : players) {
            UUID targetId = target.getUniqueId();
            onlineIds.add(targetId);
            scheduling.runOnEntity(target, () -> updateTarget(target, players, core, teamNames, teamVisibility, now));
        }

        for (Iterator<UUID> it = entries.keySet().iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (!onlineIds.contains(id)) {
                remove(id);
            }
        }

        if (++tickCounter % 20 == 0) {
            for (Entry e : entries.values()) {
                e.hiddenFrom.clear();
            }
        }
    }

    private void snapshotTeams(
            Collection<? extends Player> players,
            Map<UUID, String> teamNames,
            Map<UUID, Team.OptionStatus> teamVisibility
    ) {
        try {
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            Scoreboard sb = sm != null ? sm.getMainScoreboard() : null;
            if (sb == null) {
                return;
            }
            for (Player p : players) {
                Team t = sb.getEntryTeam(p.getName());
                if (t != null) {
                    UUID id = p.getUniqueId();
                    teamNames.put(id, t.getName());
                    teamVisibility.put(id, t.getOption(Team.Option.NAME_TAG_VISIBILITY));
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void purgeWorldsOnce() {
        try {
            for (World world : Bukkit.getWorlds()) {
                if (purgedWorlds.add(world.getUID())) {
                    Set<Entity> active = new HashSet<>();
                    for (Entry e : entries.values()) {
                        if (e.display != null) {
                            active.add(e.display);
                        }
                    }
                    List<TextDisplay> orphans = new ArrayList<>();
                    for (TextDisplay d : world.getEntitiesByClass(TextDisplay.class)) {
                        if (!active.contains(d) && d.getScoreboardTags().contains(OVERHEAD_TAG)) {
                            orphans.add(d);
                        }
                    }
                    for (TextDisplay orphan : orphans) {
                        orphan.remove();
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void updateTarget(
            Player target,
            Collection<? extends Player> players,
            StatusServer core,
            Map<UUID, String> teamNames,
            Map<UUID, Team.OptionStatus> teamVisibility,
            long now
    ) {
        UUID id = target.getUniqueId();
        PlayerStatus status = core.relay().current(id, now);
        Team.OptionStatus vis = teamVisibility.getOrDefault(id, Team.OptionStatus.ALWAYS);
        boolean show = !status.isNone()
                && vis != Team.OptionStatus.NEVER
                && target.isOnline()
                && !target.isDead()
                && !PlayerStatusesBukkit.isVanished(target)
                && !target.isSneaking()
                && target.getPassengers().isEmpty()
                && target.getGameMode() != GameMode.SPECTATOR
                && !target.hasPotionEffect(PotionEffectType.INVISIBILITY);

        if (!show) {
            remove(id);
            return;
        }

        String lang = core.settings().get(ServerSettings.LANGUAGE);
        String text = ChatColor.GRAY + StatusCommands.describe(lang, status);
        Location loc = target.getLocation().add(0.0, target.getHeight() + 0.55, 0.0);
        if (loc.getWorld() == null) {
            return;
        }

        Entry entry = entries.get(id);
        boolean owned = entry != null && entry.display != null && (!folia || isOwnedByCurrentRegion(entry.display));
        if (!owned || !entry.display.isValid() || entry.display.getWorld() != loc.getWorld()) {
            if (entry != null && entry.display != null) {
                TextDisplay old = entry.display;
                scheduling.runOnEntity(old, () -> {
                    if (old.isValid()) {
                        old.remove();
                    }
                });
            }
            TextDisplay spawned = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(false);
                d.setShadowed(true);
                d.setText(text);
                d.addScoreboardTag(OVERHEAD_TAG);
            });
            entry = new Entry(spawned, text);
            entries.put(id, entry);
        } else {
            if (!text.equals(entry.lastText)) {
                entry.lastText = text;
                entry.display.setText(text);
            }
            if (folia) {
                entry.display.teleportAsync(loc);
            } else {
                entry.display.teleport(loc);
            }
        }

        TextDisplay display = entry.display;
        if (entry.hiddenFrom.add(id)) {
            target.hideEntity(plugin, display);
        }
        String targetTeam = teamNames.get(id);
        for (Player viewer : players) {
            UUID viewerId = viewer.getUniqueId();
            if (viewerId.equals(id)) {
                continue;
            }
            boolean hide = core.relay().hasMod(viewerId)
                    || !canViewerSeeNameTag(vis, targetTeam, teamNames.get(viewerId));
            if (hide && entry.hiddenFrom.add(viewerId)) {
                scheduling.runOnEntity(viewer, () -> {
                    if (viewer.isOnline()) {
                        viewer.hideEntity(plugin, display);
                    }
                });
            }
        }
    }

    private static boolean canViewerSeeNameTag(Team.OptionStatus vis, String targetTeam, String viewerTeam) {
        if (targetTeam == null || vis == null || vis == Team.OptionStatus.ALWAYS) {
            return true;
        }
        if (vis == Team.OptionStatus.NEVER) {
            return false;
        }
        boolean sameTeam = targetTeam.equals(viewerTeam);
        if (vis == Team.OptionStatus.FOR_OTHER_TEAMS) {
            return sameTeam;
        }
        if (vis == Team.OptionStatus.FOR_OWN_TEAM) {
            return !sameTeam;
        }
        return true;
    }

    private static boolean isOwnedByCurrentRegion(Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    @Override
    public void remove(UUID playerId) {
        for (Entry e : entries.values()) {
            e.hiddenFrom.remove(playerId);
        }
        Entry entry = entries.remove(playerId);
        if (entry != null && entry.display != null) {
            TextDisplay d = entry.display;
            if (!folia && Bukkit.isPrimaryThread()) {
                if (d.isValid()) {
                    d.remove();
                }
            } else {
                scheduling.runOnEntity(d, () -> {
                    if (d.isValid()) {
                        d.remove();
                    }
                });
            }
        }
    }

    @Override
    public void clearAll() {
        purgedWorlds.clear();
        for (UUID id : new HashSet<>(entries.keySet())) {
            remove(id);
        }
    }
}
