package com.kasper.playerstatuses.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Folia: the plugin's own bookkeeping on the global region. Loaded only on Folia. */
final class FoliaScheduling implements Scheduling {

    private final JavaPlugin plugin;

    FoliaScheduling(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void everyTick(Runnable tick) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> tick.run(), 1L, 1L);
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
