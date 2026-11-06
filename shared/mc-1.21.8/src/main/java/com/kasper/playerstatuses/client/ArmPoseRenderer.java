package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusArmPose;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Applies {@link StatusArmPose} to {@link PlayerRenderState} on 1.21.2–1.21.8.
 */
public final class ArmPoseRenderer {

    private static final Field RIGHT_ARM_POSE = findField(PlayerRenderState.class, "rightArmPose", "field_55304");
    private static final Field LEFT_ARM_POSE = findField(PlayerRenderState.class, "leftArmPose", "field_55306");

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

    public static void apply(PlayerRenderState state) {
        if (RIGHT_ARM_POSE == null || LEFT_ARM_POSE == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || state == null) {
            return;
        }
        if (state.isUsingItem || state.isFallFlying
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
        Player player = (Player) entity;
        PlayerStatus status = ClientSide.statusForArmPose(player);
        if (status.isNone()) {
            return;
        }
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        StatusArmPose.Pair poses = StatusArmPose.forStatus(status, rightHanded, state.ageInTicks);
        try {
            HumanoidModel.ArmPose right = (HumanoidModel.ArmPose) RIGHT_ARM_POSE.get(state);
            HumanoidModel.ArmPose left = (HumanoidModel.ArmPose) LEFT_ARM_POSE.get(state);
            RIGHT_ARM_POSE.set(state, mapPose(right, poses.right()));
            LEFT_ARM_POSE.set(state, mapPose(left, poses.left()));
        } catch (IllegalAccessException ignored) {
        }
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
