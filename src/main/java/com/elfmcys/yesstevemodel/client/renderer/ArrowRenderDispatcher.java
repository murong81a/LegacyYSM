package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.ArrowModelCapability;
import com.elfmcys.yesstevemodel.client.ClientProxy;
import com.elfmcys.yesstevemodel.client.config.GeneralConfig;
import com.elfmcys.yesstevemodel.event.CapabilityEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.geckolib3.resource.GeckoLibCache;
import com.elfmcys.yesstevemodel.util.ModelIdUtil;
import net.minecraft.client.renderer.entity.RenderArrow;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderTippedArrow;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * 1.12.2 版本通过 {@code RenderArrowMixin} 在 {@code RenderArrow#doRender} 头部拦截，
 * 命中自定义箭矢模型时改走 {@link CustomArrowRenderer} 并取消原版渲染。
 * <p>
 * 1.10.2 没有 mixin，这里改成“替换渲染器”的等价做法：
 * 在 {@code RenderManager#entityRenderMap} 里把 {@code EntityArrow} 的渲染器换成这个分发器，
 * 命中模型时委派给 {@link CustomArrowRenderer}，否则回落到原版 {@link RenderArrow} 逻辑。
 * <p>
 * 只接管 {@code EntityArrow} 本身，{@code EntityTippedArrow} / {@code EntitySpectralArrow}
 * 在注册表里各自有独立渲染器，不受影响。
 */
public class ArrowRenderDispatcher extends RenderArrow<EntityArrow> {

    public ArrowRenderDispatcher(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(@Nonnull EntityArrow entity, double x, double y, double z, float entityYaw, float partialTick) {
        if (tryRenderCustomModel(entity, x, y, z, entityYaw, partialTick)) {
            return;
        }
        super.doRender(entity, x, y, z, entityYaw, partialTick);
    }

    /**
     * 与 {@code RenderArrowMixin} 的判定保持一致：开关打开、有箭矢模型能力、
     * 模型 id 非空且模型确实加载成功时才接管渲染。
     */
    private static boolean tryRenderCustomModel(EntityArrow entity, double x, double y, double z, float entityYaw, float partialTick) {
        if (GeneralConfig.DISABLE_ARROWS_MODEL) {
            return false;
        }
        CustomArrowRenderer renderer = ClientProxy.getArrowInstance();
        if (renderer == null) {
            return false;
        }
        Optional<ArrowModelCapability> optional = CapabilityEvent.getArrowModelCap(entity);
        if (!optional.isPresent()) {
            return false;
        }
        String modelId = optional.get().getModelId();
        if (modelId == null || modelId.isEmpty() || ArrowModelCapability.EMPTY.equals(modelId)) {
            return false;
        }
        ResourceLocation arrowModelId;
        try {
            arrowModelId = ModelIdUtil.getArrowId(new ResourceLocation(modelId));
        } catch (RuntimeException e) {
            // 模型 id 非法（存档里残留的坏数据）时不要连累原版箭矢渲染
            return false;
        }
        GeoModel geoModel = GeckoLibCache.getInstance().getGeoModels().get(arrowModelId);
        if (geoModel == null) {
            return false;
        }
        renderer.doRender(entity, x, y, z, entityYaw, partialTick);
        return true;
    }

    @Nonnull
    @Override
    protected ResourceLocation getEntityTexture(@Nonnull EntityArrow entity) {
        return RenderTippedArrow.RES_ARROW;
    }
}
