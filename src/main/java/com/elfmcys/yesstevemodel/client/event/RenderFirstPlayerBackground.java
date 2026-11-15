package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.ClientProxy;
import com.elfmcys.yesstevemodel.client.config.GeneralConfig;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.renderer.CustomPlayerRenderer;
import com.elfmcys.yesstevemodel.client.util.AnimatableCacheUtil;
import com.elfmcys.yesstevemodel.event.CapabilityEvent;
import com.elfmcys.yesstevemodel.event.api.SpecialPlayerRenderEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoBone;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.Interpolations;
import com.elfmcys.yesstevemodel.util.ModelIdUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.ExecutionException;

/**
 * 第一人称（手臂/背景）模型渲染。
 *
 * <p>RenderHandEvent 一帧内可能被触发多次（主手、副手以及其它模组的额外 pass），
 * 因此用 {@link #renderedThisFrame} 做「每帧只画一次」的去重。</p>
 *
 * <p>这个标志必须「每帧自愈」：旧实现只在 RenderWorldLastEvent 里复位，
 * 一旦该事件被其它模组取消，或渲染中途抛异常，标志就会永久为 true，
 * 导致第一人称模型在本次游戏进程内再也不显示。</p>
 */
@Mod.EventBusSubscriber(Side.CLIENT)
public class RenderFirstPlayerBackground {
    /**
     * 本帧是否已经渲染过第一人称模型。
     * 读写都发生在客户端渲染线程；加 volatile 是为了防止将来出现跨线程访问时读到旧值。
     */
    private static volatile boolean renderedThisFrame = false;

    /**
     * 每帧渲染开始（Phase.START）时复位标志。
     * RenderTickEvent 由 Minecraft 的渲染循环每帧必发，且早于 EntityRenderer 的实际渲染，
     * 不依赖可被取消的 RenderWorldLastEvent，因此上一帧无论正常结束还是抛异常，本帧都能重新绘制。
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            renderedThisFrame = false;
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (GeneralConfig.DISABLE_SELF_MODEL) {
            return;
        }
        if (GeneralConfig.DISABLE_SELF_HANDS) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || renderedThisFrame) {
            return;
        }
        renderedThisFrame = true;
        try {
            renderFirstPerson(player, event.getPartialTicks());
        } catch (Throwable t) {
            // 绝不把异常抛回 MC 渲染循环：否则本帧剩余渲染会被中断，渲染状态也可能一直坏下去。
            // 标志会在下一帧的 RenderTickEvent.START 复位，这里只记录日志，保证下一帧还能继续画。
            YesSteveModel.LOGGER.error("YSM: 第一人称模型渲染失败", t);
        }
    }

    /**
     * 原渲染逻辑整体搬入此方法，只负责绘制；异常由 {@link #onRenderHand(RenderHandEvent)} 统一兜底。
     */
    private static void renderFirstPerson(EntityPlayerSP player, float partialTicks) {
        CapabilityEvent.getModelInfoCap(player).ifPresent(cap -> {
            ResourceLocation modelId = cap.getModelId();
            CustomPlayerRenderer instance = ClientProxy.getInstance();
            Tessellator tess = Tessellator.getInstance();
            VertexBuffer buffer = tess.getBuffer();
            AnimatableEntity<?> animatable;

            try {
                animatable = AnimatableCacheUtil.ANIMATABLE_CACHE.get(modelId, () -> {
                    CustomPlayerEntity entity = new CustomPlayerEntity(null);
                    entity.setTextureLocation(cap.getSelectTexture());
                    return entity;
                });
            } catch (ExecutionException e) {
                // 缓存加载失败只影响本帧，下一帧会重试；不能再抛 RuntimeException 打断渲染循环。
                YesSteveModel.LOGGER.error("YSM: 加载第一人称动画缓存失败，modelId=" + modelId, e);
                return;
            }

            if (animatable instanceof CustomPlayerEntity) {
                CustomPlayerEntity customPlayer = (CustomPlayerEntity) animatable;
                customPlayer.setModelLocation(ModelIdUtil.getArmId(modelId));
                customPlayer.setTextureLocation(cap.getSelectTexture());
                if (!customPlayer.updateModel()) {
                    return;
                }
                AnimatedGeoModel geoModel = customPlayer.getCurrentModel();
                if (geoModel == null) {
                    return;
                }
                AnimatedGeoBone bone = geoModel.background();
                if (bone == null) {
                    return;
                }
                if (MinecraftForge.EVENT_BUS.post(new SpecialPlayerRenderEvent(player, customPlayer, modelId))) {
                    return;
                }
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
                if (instance != null) {
                    GlStateManager.pushMatrix();
                    try {
                        if (Minecraft.getMinecraft().gameSettings.viewBobbing) {
                            bobView(partialTicks, player);
                        }
                        GlStateManager.translate(0, -1.5, 0);
                        instance.renderRecursively(bone, tess, 1.0F, 1.0F, 1.0F, 1.0F);
                        tess.draw();
                    } finally {
                        // 异常时也要配对 pop，避免 GL 矩阵栈逐帧泄漏、进而破坏后续所有渲染
                        GlStateManager.popMatrix();
                    }
                }
            }
        });
    }

    private static void bobView(float pPartialTicks, EntityPlayerSP player) {
        float walk = player.distanceWalkedModified - player.prevDistanceWalkedModified;
        float walk2 = -(player.distanceWalkedModified + walk * pPartialTicks);
        float lerp = Interpolations.lerp(player.prevCameraYaw, player.cameraYaw, pPartialTicks);
        GlStateManager.translate(-MathHelper.sin(walk2 * (float) Math.PI) * lerp * 0.5F, Math.abs(MathHelper.cos(walk2 * (float) Math.PI) * lerp), 0.0D);
        GlStateManager.rotate(MathHelper.sin(walk2 * (float) Math.PI) * lerp * 3.0F, 0, 0, 1);
        GlStateManager.rotate(Math.abs(MathHelper.cos(walk2 * (float) Math.PI - 0.2F) * lerp) * 5.0F, 1, 0, 0);
    }
}
