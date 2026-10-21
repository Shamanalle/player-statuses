package com.kasper.playerstatuses;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The NeoForge mod of the universal Forge / NeoForge jar: creates the NeoForge entrypoint of the
 * running Minecraft's band (see BandTable) with the same arguments NeoForge gave this one.
 */
@Mod(ForgeDispatcher.MOD_ID)
public final class NeoForgeDispatcher {

    private final Object band;

    public NeoForgeDispatcher(IEventBus modBus, ModContainer container, Dist dist) {
        band = BandTable.create("neoforge", "net.neoforged.fml", modBus, container, dist);
    }
}
