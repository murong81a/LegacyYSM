package com.elfmcys.yesstevemodel.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * 1.10.2 的 fullscreen 切换在部分环境（虚拟显示/远程桌面/混合显卡）会失败，
 * 失败后 LWJGL 的 Display/Keyboard 上下文被破坏，下一帧直接
 * "Keyboard must be created before you can read events" 崩溃。
 * <p>
 * 这个 coremod 把 {@code Minecraft#toggleFullscreen} 换成空实现，
 * 让 F11 不再触发底层显示模式切换。需要恢复原版行为时加 JVM 参数
 * {@code -Dysm.blockFullscreen=false}。
 */
@IFMLLoadingPlugin.Name("YSMFullscreenGuard")
@IFMLLoadingPlugin.MCVersion("1.10.2")
public class YsmCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{FullscreenGuardTransformer.class.getName()};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
