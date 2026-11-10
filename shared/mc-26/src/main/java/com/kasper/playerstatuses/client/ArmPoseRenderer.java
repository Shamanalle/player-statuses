package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusArmPose;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Applies {@link StatusArmPose} to {@link AvatarRenderState} on Minecraft 26.x
 * ({@code ArmedEntityRenderState.attackTime} in 26.1–26.1.2, renamed to
 * {@code swingAnimation} in 26.2+).
 */
public final class ArmPoseRenderer {

    private static final Field SWING_FIELD = findField(AvatarRenderState.class, "swingAnimation", "attackTime");

    private ArmPoseRenderer() {
    }

    private static Field findField(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field f = type.getField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static boolean isSwinging(AvatarRenderState state) {
        if (SWING_FIELD == null) {
            return false;
        }
        try {
            return SWING_FIELD.getFloat(state) > 0.0f;
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    public static void apply(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || state == null) {
            return;
        }
        if (state.isUsingItem || isSwinging(state) || state.isFallFlying
                || state.isVisuallySwimming || state.isAutoSpinAttack) {
            return;
        }
        if (state.pose != Pose.STANDING && state.pose != Pose.CROUCHING) {
            return;
        }
        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player)) {
            return;
        }
        PlayerStatus status = ClientSide.statusForArmPose((Player) entity);
        if (status.isNone()) {
            return;
        }
        boolean rightHanded = state.mainArm == HumanoidArm.RIGHT;
        StatusArmPose.Pair poses = StatusArmPose.forStatus(status, rightHanded, state.ageInTicks);
        state.rightArmPose = mapPose(state.rightArmPose, poses.right());
        state.leftArmPose = mapPose(state.leftArmPose, poses.left());
    }

    private static HumanoidModel.ArmPose mapPose(HumanoidModel.ArmPose current, StatusArmPose target) {
        if (current != HumanoidModel.ArmPose.EMPTY && current != HumanoidModel.ArmPose.ITEM) {
            return current;
        }
        if (target == StatusArmPose.HIGH) {
            return HumanoidModel.ArmPose.BLOCK;
        }
        if (target == StatusArmPose.LOW) {
            return HumanoidModel.ArmPose.ITEM;
        }
        return current;
    }
}
