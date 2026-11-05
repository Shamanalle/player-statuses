package com.kasper.playerstatuses.bukkit;

import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusCommands;
import com.kasper.playerstatuses.StatusServer;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns and updates non-persistent {@link TextDisplay} entities above players on Bukkit/Spigot/Paper/Folia
 * 1.19.4+. Loaded only when {@link TextDisplay} exists at runtime.
 */
final class BukkitTextDisplays implements OverheadBridge {

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

    BukkitTextDisplays(JavaPlugin plugin, Scheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        this.folia = Scheduling.isFolia();
    }

    @Override
    public void tick(Collection<? extends Player> players, StatusServer core, boolean enabled, boolean hasVanillaViewer, long now) {
        if (!enabled || !hasVanillaViewer) {
            clearAll();
            return;
        }

        Set<UUID> onlineIds = new HashSet<>();
        for (Player target : players) {
            onlineIds.add(target.getUniqueId());
            scheduling.runOnEntity(target, () -> updateTarget(target, players, core, now));
        }

        for (Iterator<UUID> it = entries.keySet().iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (!onlineIds.contains(id)) {
                remove(id);
            }
        }
    }

    private void updateTarget(Player target, Collection<? extends Player> players, StatusServer core, long now) {
        UUID id = target.getUniqueId();
        PlayerStatus status = core.relay().current(id, now);
        boolean show = !status.isNone()
                && target.isOnline()
                && !target.isDead()
                && !target.isSneaking()
                && !target.isInsideVehicle()
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
        if (entry == null || entry.display == null || !entry.display.isValid() || entry.display.getWorld() != loc.getWorld()) {
            if (entry != null && entry.display != null) {
                TextDisplay old = entry.display;
                scheduling.runOnEntity(old, old::remove);
            }
            TextDisplay spawned = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(false);
                d.setShadowed(true);
                d.setText(text);
                d.addScoreboardTag("playerstatuses.overhead");
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
        for (Player viewer : players) {
            UUID viewerId = viewer.getUniqueId();
            if (!viewerId.equals(id) && core.relay().hasMod(viewerId) && entry.hiddenFrom.add(viewerId)) {
                scheduling.runOnEntity(viewer, () -> {
                    if (viewer.isOnline() && display.isValid()) {
                        viewer.hideEntity(plugin, display);
                    }
                });
            }
        }
    }

    @Override
    public void remove(UUID playerId) {
        Entry entry = entries.remove(playerId);
        if (entry != null && entry.display != null) {
            TextDisplay d = entry.display;
            scheduling.runOnEntity(d, () -> {
                if (d.isValid()) {
                    d.remove();
                }
            });
        }
    }

    @Override
    public void clearAll() {
        for (UUID id : new HashSet<>(entries.keySet())) {
            remove(id);
        }
    }
}
