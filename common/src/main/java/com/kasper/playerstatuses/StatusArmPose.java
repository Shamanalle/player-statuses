package com.kasper.playerstatuses;

/**
 * How a player's arms are posed while typing or looking at a screen ({@link ClientSettings#ARM_POSES}).
 */
public enum StatusArmPose {
    /** Leave the arm as Minecraft posed it. */
    DEFAULT,
    /** Raised slightly forward ({@code HumanoidModel.ArmPose.ITEM}). */
    LOW,
    /** Raised and angled inward in front of the chest ({@code HumanoidModel.ArmPose.BLOCK}). */
    HIGH;

    public static final class Pair {
        public static final Pair NONE = new Pair(DEFAULT, DEFAULT);

        private final StatusArmPose left;
        private final StatusArmPose right;

        public Pair(StatusArmPose left, StatusArmPose right) {
            this.left = left;
            this.right = right;
        }

        public StatusArmPose left() {
            return left;
        }

        public StatusArmPose right() {
            return right;
        }

        public boolean isDefault() {
            return left == DEFAULT && right == DEFAULT;
        }
    }

    /**
     * Computes the arm poses for {@code status}.
     *
     * @param status      the player's visible status
     * @param rightHanded {@code true} if the player's main arm is the right arm
     * @param ageInTicks  entity age in ticks (including partial tick) for typing animation
     */
    public static Pair forStatus(PlayerStatus status, boolean rightHanded, float ageInTicks) {
        if (status == null || status.isNone()) {
            return Pair.NONE;
        }
        Activity a = status.activity();
        StatusArmPose main;
        StatusArmPose off;
        if (a == Activity.TYPING || a == Activity.COMMAND) {
            if (status.has(StatusFlag.AFK) || status.has(StatusFlag.UNFOCUSED)) {
                main = HIGH;
                off = LOW;
            } else {
                float wrapped = ageInTicks % 4.0f;
                if (wrapped < 0.0f) {
                    wrapped += 4.0f;
                }
                boolean step = (((int) (wrapped * 0.5f)) & 1) == 0;
                main = step ? HIGH : LOW;
                off = step ? LOW : HIGH;
            }
        } else if (a == Activity.INVENTORY || a.category() == Category.READING) {
            main = HIGH;
            off = HIGH;
        } else if (a == Activity.CHAT || a.isScreen()) {
            main = HIGH;
            off = LOW;
        } else {
            return Pair.NONE;
        }
        return rightHanded ? new Pair(off, main) : new Pair(main, off);
    }
}
