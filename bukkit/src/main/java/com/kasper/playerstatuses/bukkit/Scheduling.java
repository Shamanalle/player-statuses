package com.kasper.playerstatuses.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Where the plugin's work runs. On Bukkit, Spigot and Paper everything runs on the main thread; on
 * Folia each region of the world ticks on its own thread, so the plugin's own bookkeeping runs on the
 * global region.
 */
interface Scheduling {

    /** Runs {@code tick} every server tick: on the main thread, or on Folia's global region. */
    void everyTick(Runnable tick);

    void cancelAll();

    static Scheduling create(JavaPlugin plugin) {
        if (isFolia()) {
            // FoliaScheduling is compiled against a newer API (source set folia), so main code reaches it by name
            try {
                return (Scheduling) Class.forName("com.kasper.playerstatuses.bukkit.FoliaScheduling")
                        .getDeclaredConstructor(JavaPlugin.class).newInstance(plugin);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Folia scheduling is unavailable", e);
            }
        }
        return new BukkitScheduling(plugin);
    }

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
