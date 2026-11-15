package com.elfmcys.yesstevemodel.client.compat;

import net.minecraft.entity.EntityLivingBase;

/**
 * 三叉戟/激流兼容。1.10.2 上没有对应模组，暂时恒为 false。
 */
public class TridentCompat {
    public static boolean hasRiptide() {
        return false;
    }

    public static boolean isAutoSpinAttack(EntityLivingBase living) {
        return false;
    }
}
