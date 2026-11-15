package com.elfmcys.yesstevemodel.client.config;

import com.elfmcys.yesstevemodel.Tags;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;

import java.util.Collections;
import java.util.Set;

/**
 * 1.10.2 的 {@link IModGuiFactory} 与 1.12 不同：没有 {@code hasConfigGui/createConfigGui}，
 * 改为 {@code mainConfigGuiClass()} 与 {@code getHandlerFor(...)}。
 */
@SuppressWarnings("unused")
public class ConfigGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ConfigGui.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    /**
     * Forge 的 Config Gui，更直观一点。
     */
    public static class ConfigGui extends GuiConfig {
        public ConfigGui(final GuiScreen parent) {
            super(parent, Config.getRootConfigElements(), YesSteveModel.MOD_ID, false, false, Tags.MOD_NAME);
        }
    }
}
