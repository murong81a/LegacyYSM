package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.event.ReplacePlayerHandRenderEvent;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.util.Map;

/**
 * 1.12.2 版本通过 {@code RenderPlayerMixin} 注入 {@link RenderPlayer#renderRightArm(AbstractClientPlayer)} /
 * {@link RenderPlayer#renderLeftArm(AbstractClientPlayer)} 的 HEAD，在需要时改画 YSM 的自定义手臂并取消原版手臂。
 * <p>
 * 1.10.2 没有 Mixin，这里用「子类 + 替换 {@link RenderManager} 里的 skinMap」达到同样效果：
 * 1.10.2 的 {@code ItemRenderer#renderArmFirstPerson} 会从
 * {@code RenderManager#getEntityRenderObject(player)} 拿到渲染器并强转 {@link RenderPlayer} 后调用这两个方法，
 * 所以只要玩家实际用的是本类实例，第一人称手臂就会走
 * {@link ReplacePlayerHandRenderEvent#renderArm(EnumHandSide)}。
 * <p>
 * 注意 1.10.2 的玩家渲染器<b>不在</b> {@code entityRenderMap} 里：
 * {@code getEntityRenderObject} 对 {@code AbstractClientPlayer} 特判，走私有的 {@code skinMap}（默认 + 细手臂两套）。
 * {@code getSkinMap()} 返回的是 {@code Collections.unmodifiableMap}，所以只能反射拿到原始 map 再替换。
 */
public class CustomRenderPlayer extends RenderPlayer {

    public CustomRenderPlayer(RenderManager renderManager) {
        super(renderManager);
    }

    public CustomRenderPlayer(RenderManager renderManager, boolean smallArms) {
        super(renderManager, smallArms);
    }

    /**
     * 把 {@link RenderManager} 里默认/细手臂两套玩家渲染器换成本类的实例。
     * 失败时只打日志并保持原版渲染器（宁可没有自定义手臂，也不能让游戏崩）。
     */
    @SuppressWarnings("unchecked")
    public static void register(RenderManager renderManager) {
        Map<String, RenderPlayer> skinMap;
        try {
            // "skinMap" 是 dev 环境的 MCP 名，field_178636_l 是生产环境的 SRG 名
            skinMap = ObfuscationReflectionHelper.getPrivateValue(RenderManager.class, renderManager, "skinMap", "field_178636_l");
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("Failed to access RenderManager#skinMap, custom first person arms disabled", t);
            return;
        }
        if (skinMap == null) {
            YesSteveModel.LOGGER.warn("RenderManager#skinMap is null, custom first person arms disabled");
            return;
        }
        skinMap.put("default", new CustomRenderPlayer(renderManager));
        skinMap.put("slim", new CustomRenderPlayer(renderManager, true));
    }

    @Override
    public void renderRightArm(AbstractClientPlayer clientPlayer) {
        if (ReplacePlayerHandRenderEvent.shouldRenderArm()) {
            ReplacePlayerHandRenderEvent.renderArm(EnumHandSide.RIGHT);
        } else {
            super.renderRightArm(clientPlayer);
        }
    }

    @Override
    public void renderLeftArm(AbstractClientPlayer clientPlayer) {
        if (ReplacePlayerHandRenderEvent.shouldRenderArm()) {
            ReplacePlayerHandRenderEvent.renderArm(EnumHandSide.LEFT);
        } else {
            super.renderLeftArm(clientPlayer);
        }
    }
}
