package com.kasper.playerstatuses.bukkit;

import com.kasper.playerstatuses.StatusServer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.UUID;

/**
 * Manages overhead {@code TextDisplay} entities on servers running Minecraft 1.19.4+. On older
 * servers {@link #create} returns {@code null} and statuses are shown in the Tab list instead.
 */
interface OverheadBridge {

    void tick(Collection<? extends Player> players, StatusServer core, boolean enabled, boolean hasVanillaViewer, long now);

    void remove(UUID playerId);

    void clearAll();

    static OverheadBridge create(JavaPlugin plugin, Scheduling scheduling) {
        try {
            Class.forName("org.bukkit.entity.TextDisplay");
            return (OverheadBridge) Class.forName("com.kasper.playerstatuses.bukkit.BukkitTextDisplays")
                    .getDeclaredConstructor(JavaPlugin.class, Scheduling.class)
                    .newInstance(plugin, scheduling);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }
}
