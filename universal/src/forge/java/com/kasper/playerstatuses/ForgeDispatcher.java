package com.kasper.playerstatuses;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * The Forge mod of the universal Forge / NeoForge jar: creates the Forge entrypoint of the running
 * Minecraft's band (see BandTable). Newer Forge passes its loading context to the constructor, older
 * Forge calls the one without arguments; either is handed on to the band, which asks for what it needs.
 */
@Mod(ForgeDispatcher.MOD_ID)
public final class ForgeDispatcher {

    static final String MOD_ID = "playerstatuses";

    private final Object band;

    public ForgeDispatcher(FMLJavaModLoadingContext context) {
        band = BandTable.create("forge", "net.minecraftforge.fml", context);
    }

    public ForgeDispatcher() {
        band = BandTable.create("forge", "net.minecraftforge.fml", staticContext());
    }

    /** FMLJavaModLoadingContext.get(), which the Forge versions without constructor arguments have. */
    private static Object staticContext() {
        try {
            return Class.forName("net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext").getMethod("get").invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return new Object();
        }
    }
}
