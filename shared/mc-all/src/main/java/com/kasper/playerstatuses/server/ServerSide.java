package com.kasper.playerstatuses.server;

import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * The server half on Fabric, Forge and NeoForge: one {@link StatusServer} for the running server and
 * the loader-independent part of the hooks. Each loader's entrypoint calls these.
 */
public final class ServerSide {

    private static final int RELOAD_CHECK_TICKS = 40;

    private static final StatusServer CORE = new StatusServer();
    private static Path configDir;
    private static boolean started;
    private static int ticks;

    private ServerSide() {
    }

    /** Where the settings file goes; called by the entrypoint as the mod loads. */
    public static void init(Path config) {
        configDir = config;
    }

    /**
     * The server core, with its settings read. Commands are registered before the server starts, and
     * {@code /afk} is registered only when it is on, so the settings are read on first use.
     */
    public static synchronized StatusServer core() {
        if (!started) {
            started = true;
            CORE.start(configDir.resolve(ServerSettings.FILE));
        }
        return CORE;
    }

    public static void tick(MinecraftServer server) {
        StatusServer core = core();
        core.relay().tick(System.currentTimeMillis());
        if (++ticks % RELOAD_CHECK_TICKS == 0) {
            core.reloadIfChanged();
        }
    }

    public static void left(ServerPlayer player) {
        core().relay().remove(player.getUUID());
    }
}
