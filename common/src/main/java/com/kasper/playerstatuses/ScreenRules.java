package com.kasper.playerstatuses;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Screens of other mods, by class name. Vanilla screens are recognised by their type in the
 * Minecraft code; another mod's screen counts as {@link Activity#BUSY} unless it is a known map
 * ({@link Activity#MAP}) or on the player's ignore list (emote wheels and other menus people open
 * while playing).
 */
public final class ScreenRules {

    /** Ignored by default: radial menus that are part of playing, not a reason to look busy. */
    public static final List<String> DEFAULT_IGNORED = Collections.unmodifiableList(Arrays.asList(
            "io.github.kosmx.emotes",        // Emotecraft
            "com.tom.cpm",                   // Customizable Player Models (gestures)
            "com.elfmcys.yesstevemodel",     // Yes Steve Model
            "org.figuramc.figura",           // Figura (action wheel)
            "com.zigythebird.playeranim",    // emote wheels of PlayerAnimator addons
            "dev.isxander.controlify"        // Controlify's radial menu and keyboard
    ));

    /** Full-screen maps. */
    static final List<String> MAPS = Collections.unmodifiableList(Arrays.asList(
            "xaero.map",                     // Xaero's World Map
            "journeymap",                    // JourneyMap
            "dev.ftb.mods.ftbchunks",        // FTB Chunks
            "com.mamiyaotaru.voxelmap",      // VoxelMap
            "folk.sisby.antique_atlas",      // Antique Atlas
            "pepjebs.mapatlases",            // Map Atlases
            "net.pl3x.map"                   // Pl3xMap
    ));

    private ScreenRules() {
    }

    /** True if {@code className} is one of {@code prefixes} or in a package (or nested class) of one. */
    public static boolean matches(String className, List<String> prefixes) {
        if (className == null) {
            return false;
        }
        for (String p : prefixes) {
            if (!p.isEmpty() && className.startsWith(p)
                    && (className.length() == p.length() || ".$".indexOf(className.charAt(p.length())) >= 0 || p.endsWith("."))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The activity for a screen that is not one of Minecraft's.
     *
     * @return {@link Activity#NONE} for an ignored screen
     */
    public static Activity modScreen(String className, List<String> ignored) {
        if (matches(className, ignored)) {
            return Activity.NONE;
        }
        if (matches(className, MAPS)) {
            return Activity.MAP;
        }
        return Activity.BUSY;
    }
}
