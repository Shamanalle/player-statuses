package com.kasper.playerstatuses;

/** How long someone has been AFK, as the icons and the player list show it: "4m", "1h 05m", "2d 3h". */
public final class IdleText {

    private IdleText() {
    }

    /** Empty below a minute (the icon alone says AFK). */
    public static String duration(int seconds) {
        if (seconds < 60) {
            return "";
        }
        int minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        int hours = minutes / 60;
        if (hours < 24) {
            int m = minutes % 60;
            return hours + "h " + (m < 10 ? "0" : "") + m + "m";
        }
        return hours / 24 + "d " + hours % 24 + "h";
    }
}
