package com.kasper.playerstatuses.mixin;

import com.kasper.playerstatuses.client.OverheadRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the overhead status badge in {@link EntityRenderer#render} so it still runs when
 * {@code state.nameTag} is {@code null} (first/third-person local player with {@code SHOW_OWN} or
 * {@code VIEW_DISTANCE > 64}) and after {@code PlayerRenderer} pops any {@code scoreText} translation.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL")
    )
    private void playerstatuses$renderOverhead(
            EntityRenderState state,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        if (state instanceof PlayerRenderState playerState) {
            OverheadRenderer.render(playerState, poseStack, bufferSource, packedLight);
        }
    }
}
