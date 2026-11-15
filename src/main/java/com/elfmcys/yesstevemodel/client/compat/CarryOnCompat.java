package com.elfmcys.yesstevemodel.client.compat;

import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;

/**
 * Carry On 兼容。1.10.2 上暂时不直接引用该模组（整合包内也没有），
 * 只保留接口，避免编译期硬依赖。
 */
public class CarryOnCompat {
    public static void init() {
        // Carry On 兼容在 1.10.2 上未启用
    }

    public static boolean isInstalled() {
        return false;
    }

    @Nullable
    public static String getCarryOnString(EntityLivingBase player) {
        return null;
    }

    public enum CarryOnType {
        ENTITY,
        BLOCK,
        NONE
    }

    public static void renderCarryOn(EntityPlayer player, RenderPlayer renderer, float tick, double x, double y, double z) {
        // no-op
    }
}
