package com.elfmcys.yesstevemodel;

import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.FormatVersion;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.LocatorValue;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.PolysUnion;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.UvUnion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = YesSteveModel.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        dependencies = "after:universaltweaks",
        guiFactory = "com.elfmcys.yesstevemodel.client.config.ConfigGuiFactory",
        customProperties = {
                @Mod.CustomProperty(k = "license", v = "BSD-3-Clause"),
                @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/RuiXuqi/YesSteveModel/issues")
        }
)
public class YesSteveModel {
    public static final String MOD_ID = Tags.MOD_ID;
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    /**
     * 1.10.2 自带的 gson 是 2.2.4，没有 {@code @JsonAdapter}，所以这里手动注册类型适配器。
     */
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .registerTypeAdapter(FormatVersion.class, new FormatVersion.Serializer())
            .registerTypeAdapter(LocatorValue.class, new LocatorValue.Serializer())
            .registerTypeAdapter(PolysUnion.class, new PolysUnion.Serializer())
            .registerTypeAdapter(UvUnion.class, new UvUnion.Serializer())
            .create();

    @SidedProxy(clientSide = "com.elfmcys.yesstevemodel.client.ClientProxy", serverSide = "com.elfmcys.yesstevemodel.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
