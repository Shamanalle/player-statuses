package com.kasper.playerstatuses;

import java.nio.file.Path;

/**
 * The server half of the mod, shared by every loader and the Bukkit plugin: settings and the relay.
 * The platform glue creates one per server, feeds it events and sends what it says.
 */
public final class StatusServer {

    private final ServerSettings settings = new ServerSettings();
    private final StatusRelay relay = new StatusRelay();

    /** Reads (or writes) the settings file. */
    public void start(Path settingsFile) {
        settings.load(settingsFile);
        logProblems();
        ModLog.info(BuildInfo.NAME + " " + BuildInfo.version() + ": server side ready, settings in " + settingsFile);
    }

    public ServerSettings settings() {
        return settings;
    }

    public StatusRelay relay() {
        return relay;
    }

    /** Reads the settings again if the file was saved since; for the platform's tick (every few seconds). */
    public boolean reloadIfChanged() {
        boolean changed = settings.reloadIfChanged();
        if (changed) {
            logProblems();
        }
        return changed;
    }

    /** {@code /playerstatuses reload}. */
    public void reload() {
        if (settings.path() != null) {
            settings.load(settings.path());
            logProblems();
        }
    }

    private void logProblems() {
        for (String p : settings.problems()) {
            ModLog.warn(p);
        }
    }
}
