package com.elfmcys.yesstevemodel.client.compat;

/**
 * Cleanroom 的 {@code RenderArmEvent} 在 1.10.2 上不存在，这里退化为空实现。
 */
public class RenderArmCompat {
    public static void init() {
        // no-op
    }

    public static boolean foundRenderArm() {
        return false;
    }
}
