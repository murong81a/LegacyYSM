package com.elfmcys.yesstevemodel.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 1.10.2 的 fullscreen 切换在部分环境（虚拟显示/远程桌面/混合显卡）会失败，
 * 失败后 LWJGL 的 Display/Keyboard 上下文被破坏，下一帧直接
 * "Keyboard must be created before you can read events" 崩溃。
 * <p>
 * 这个 coremod 把 {@code Minecraft#toggleFullscreen} 换成空实现，
 * 让 F11 不再触发底层显示模式切换。需要恢复原版行为时加 JVM 参数
 * {@code -Dysm.blockFullscreen=false}。
 * <p>
 * 同时它实现 {@link IEarlyMixinLoader}，让 MixinBooter 11.18 在 PREINIT 之前
 * 收集本 mod 的 Mixin 配置。生产环境需要把 !mixinbooter-11.18.jar 一起放进
 * mods 目录（前缀 '!' 保证它的 coremod 比我们先加载）。
 */
@IFMLLoadingPlugin.Name("YSMFullscreenGuard")
@IFMLLoadingPlugin.MCVersion("1.10.2")
public class YsmCorePlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    /**
     * 早期 Mixin 配置列表。移植的 ClickEvent / GuiScreen / EntityArrow 注入都在
     * 这个配置里，编译期已生成对应的 SRG refmap。
     */
    private static final List<String> MIXIN_CONFIGS = Collections.singletonList(
            "mixins.yes_steve_model.json"
    );

    @Override
    public List<String> getMixinConfigs() {
        return MIXIN_CONFIGS;
    }

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
