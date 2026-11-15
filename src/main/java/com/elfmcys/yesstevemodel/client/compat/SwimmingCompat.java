package com.elfmcys.yesstevemodel.client.compat;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Aqua Acrobatics 游泳姿势兼容。1.10.2 上没有该模组，暂时恒为 false。
 */
public class SwimmingCompat {
    public static boolean hasSwimming() {
        return false;
    }

    public static boolean isSwimming(EntityPlayer player) {
        return false;
    }

    public static boolean isSwimmingPose(EntityPlayer player) {
        return false;
    }
}
