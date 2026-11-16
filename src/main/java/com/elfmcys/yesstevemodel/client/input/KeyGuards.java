package com.elfmcys.yesstevemodel.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.input.Keyboard;

/**
 * 1.10.2 的 {@link KeyBinding#isPressed()} 只看 pressTime 闩锁，既不检查当前界面，
 * 也不检查冲突上下文/修饰键，而且会消费闩锁（同一按键的其它监听者收不到）。
 * 结果就是在暂停、存档、聊天、容器界面里按 YSM 快捷键也会触发，
 * 例如打开轮盘界面会崩溃、Alt+Y 会误开模型界面。
 * <p>
 * 这里统一做一次严格判定，供所有 YSM 快捷键复用。
 */
public final class KeyGuards {
    private KeyGuards() {
    }

    /**
     * 仅在“真正按下且当前处于游戏内、无任何界面”时返回 true。
     * 不消费 {@link KeyBinding#pressTime}，不会影响其它监听者。
     */
    public static boolean canTrigger(KeyBinding key) {
        // KeyInputEvent 按下和松开各触发一次，先过滤掉松开事件
        if (key == null || !Keyboard.getEventKeyState()) {
            return false;
        }
        // isActiveAndMatches 已包含 keyCode == getKeyCode() 与 KeyConflictContext.isActive() 检查
        if (!key.isActiveAndMatches(Keyboard.getEventKey())) {
            return false;
        }
        // isActiveAndMatches 对 NONE 修饰键恒为 true，必须再显式比较当前按下的修饰键，
        // 否则 Shift+Z / Ctrl+Z / Alt+Z 也会触发本应无修饰键的绑定
        if (key.getKeyModifier() != KeyModifier.getActiveModifier()) {
            return false;
        }
        // 1.10.2 的 KeyInputEvent 在 allowUserInput=true 的界面（容器、聊天等）里也会触发，
        // 所以这里要求当前没有任何界面，同时保证玩家和世界已就绪
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.currentScreen == null && mc.player != null && mc.world != null;
    }
}
