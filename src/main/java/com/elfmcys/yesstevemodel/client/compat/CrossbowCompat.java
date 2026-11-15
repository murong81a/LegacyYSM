package com.elfmcys.yesstevemodel.client.compat;

import net.minecraft.item.ItemStack;

/**
 * 弩兼容。1.10.2 上没有对应模组，暂时恒为 false。
 */
public class CrossbowCompat {
    public static boolean isCharged(ItemStack stack) {
        return false;
    }
}
