package org.spongepowered.asm.mixin.extensibility;

import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

/** Stub for compiling {@code BandMixinPlugin}; the real interface comes with Fabric Loader's Mixin. */
public interface IMixinConfigPlugin {
    void onLoad(String mixinPackage);

    String getRefMapperConfig();

    boolean shouldApplyMixin(String targetClassName, String mixinClassName);

    void acceptTargets(Set<String> myTargets, Set<String> otherTargets);

    List<String> getMixins();

    void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo);

    void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo);
}
