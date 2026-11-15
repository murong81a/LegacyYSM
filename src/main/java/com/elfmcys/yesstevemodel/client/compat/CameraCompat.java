package com.elfmcys.yesstevemodel.client.compat;

/**
 * Universal Tweaks 相机兼容。1.10.2 上没有该模组，暂时恒为 false。
 */
public class CameraCompat {
    public static boolean thirdPersonIgnoresNonSolidBlocks() {
        return false;
    }

    public static boolean cameraOrientation() {
        return false;
    }
}
