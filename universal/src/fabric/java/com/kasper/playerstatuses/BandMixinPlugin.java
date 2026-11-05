package com.kasper.playerstatuses;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapterException;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Selects only the running Minecraft band's mixins from {@code com.kasper.playerstatuses.mixin.<band>},
 * so Mixin never parses class files of newer Java versions on older JVMs.
 */
public final class BandMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        try {
            Properties table = BandAdapter.load();
            String id = BandAdapter.band();
            List<String> out = new ArrayList<String>();
            addMixins(out, id, table.getProperty(id + ".mixins.common", ""));
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                addMixins(out, id, table.getProperty(id + ".mixins.client", ""));
            } else {
                addMixins(out, id, table.getProperty(id + ".mixins.server", ""));
            }
            return out;
        } catch (LanguageAdapterException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static void addMixins(List<String> out, String id, String csv) {
        if (csv == null || csv.isEmpty()) {
            return;
        }
        for (String item : csv.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                out.add(id + "." + trimmed);
            }
        }
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
