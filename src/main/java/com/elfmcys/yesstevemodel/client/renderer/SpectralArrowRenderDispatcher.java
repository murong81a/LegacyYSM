package com.elfmcys.yesstevemodel.client.renderer;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSpectralArrow;
import net.minecraft.entity.projectile.EntitySpectralArrow;

import javax.annotation.Nonnull;

/**
 * 同 {@link TippedArrowRenderDispatcher}：保证 spectral arrow 也能使用 YSM 模型，
 * 未命中时回落到原版 spectral arrow 渲染。
 */
public class SpectralArrowRenderDispatcher extends RenderSpectralArrow {

    public SpectralArrowRenderDispatcher(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(@Nonnull EntitySpectralArrow entity, double x, double y, double z, float entityYaw, float partialTick) {
        if (ArrowRenderDispatcher.tryRenderCustomModel(entity, x, y, z, entityYaw, partialTick)) {
            return;
        }
        super.doRender(entity, x, y, z, entityYaw, partialTick);
    }
}
