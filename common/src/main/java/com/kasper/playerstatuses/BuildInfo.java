package com.kasper.playerstatuses;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The mod's version, written into the jar by the build. */
public final class BuildInfo {

    public static final String NAME = "Player Statuses";
    public static final String MOD_ID = "playerstatuses";

    private static final String VERSION = read();

    private BuildInfo() {
    }

    public static String version() {
        return VERSION;
    }

    private static String read() {
        try (InputStream in = BuildInfo.class.getResourceAsStream("/playerstatuses-build.properties")) {
            if (in == null) {
                return "dev";
            }
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("version", "dev");
            return v.startsWith("$") ? "dev" : v;
        } catch (IOException e) {
            return "dev";
        }
    }
}
