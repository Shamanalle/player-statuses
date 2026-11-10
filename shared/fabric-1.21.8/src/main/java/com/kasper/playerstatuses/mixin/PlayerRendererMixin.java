package com.kasper.playerstatuses.mixin;

import com.kasper.playerstatuses.client.ArmPoseRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL")
    )
    private void playerstatuses$extractArmPoses(
            AbstractClientPlayer entity,
            PlayerRenderState state,
            float partialTick,
            CallbackInfo ci
    ) {
        ArmPoseRenderer.apply(state);
    }
}
