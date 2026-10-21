package com.kasper.playerstatuses;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * The entrypoints of the universal Fabric jar. The jar holds the glue of every Minecraft band, each
 * under {@code com.kasper.playerstatuses.b.<band>}; this adapter picks the band of the running Minecraft
 * (playerstatuses-bands.properties, written from each band's fabric.mod.json) and creates that band's
 * entrypoint. The rest of the band's requirements (Fabric Loader, Fabric API, Simple Voice Chat, Java)
 * are checked here too, since fabric.mod.json can only state those of the whole jar.
 */
public final class BandAdapter implements LanguageAdapter {

    private static final String PACKAGE = "com.kasper.playerstatuses.b.";

    private static String band;

    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        String name = PACKAGE + band() + "." + value;
        try {
            Class<?> cls = Class.forName(name, true, BandAdapter.class.getClassLoader());
            return type.cast(cls.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            throw new LanguageAdapterException("Player Statuses: cannot create " + name + " as " + type.getName(), e);
        }
    }

    private static synchronized String band() throws LanguageAdapterException {
        if (band == null) {
            band = pick(load());
        }
        return band;
    }

    private static Properties load() throws LanguageAdapterException {
        Properties table = new Properties();
        try (InputStream in = BandAdapter.class.getResourceAsStream("/playerstatuses-bands.properties")) {
            if (in == null) {
                throw new LanguageAdapterException("Player Statuses: playerstatuses-bands.properties is missing from the jar");
            }
            table.load(in);
        } catch (IOException e) {
            throw new LanguageAdapterException("Player Statuses: cannot read playerstatuses-bands.properties", e);
        }
        return table;
    }

    private static String pick(Properties table) throws LanguageAdapterException {
        Version minecraft = version("minecraft");
        if (minecraft == null) {
            throw new LanguageAdapterException("Player Statuses: Minecraft's version is unknown");
        }
        for (String id : table.getProperty("bands", "").split(",")) {
            if (id.isEmpty() || !matches(table.getProperty(id + ".minecraft"), minecraft)) {
                continue;
            }
            List<String> missing = new ArrayList<>();
            String[] depends = table.getProperty(id + ".depends", "").split(",");
            for (String dep : depends) {
                if (dep.isEmpty()) {
                    continue;
                }
                String wanted = table.getProperty(id + ".depends." + dep);
                Version have = version(dep);
                if (have == null && (dep.equals("fabric") || dep.equals("fabric-api"))) {
                    // Fabric API is "fabric" up to Minecraft 1.17 and "fabric-api" after
                    have = version(dep.equals("fabric") ? "fabric-api" : "fabric");
                }
                if (have == null || !matches(wanted, have)) {
                    missing.add(dep + " " + wanted + " (found " + (have == null ? "none" : have.getFriendlyString()) + ")");
                }
            }
            if (!missing.isEmpty()) {
                throw new LanguageAdapterException("Player Statuses on Minecraft " + minecraft.getFriendlyString()
                        + " needs " + String.join(", ", missing));
            }
            return id;
        }
        throw new LanguageAdapterException("Player Statuses does not support Minecraft " + minecraft.getFriendlyString());
    }

    private static Version version(String modId) {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId);
        return mod.map(m -> m.getMetadata().getVersion()).orElse(null);
    }

    /** A fabric.mod.json version range; "|" separates the alternatives of an array. */
    private static boolean matches(String ranges, Version version) throws LanguageAdapterException {
        if (ranges == null) {
            return false;
        }
        for (String range : ranges.split("\\|")) {
            try {
                if (VersionPredicate.parse(range.trim()).test(version)) {
                    return true;
                }
            } catch (VersionParsingException e) {
                throw new LanguageAdapterException("Player Statuses: bad version range " + range, e);
            }
        }
        return false;
    }
}
