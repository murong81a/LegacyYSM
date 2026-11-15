package com.elfmcys.yesstevemodel.client.renderer;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderTippedArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;

import javax.annotation.Nonnull;

/**
 * 1.12.2 的 {@code RenderArrowMixin} 注入的是 {@code RenderArrow#doRender}，
 * 因此 tipped arrow 也会走 YSM 自定义模型。1.10.2 没有 mixin，只能按
 * RenderArrow 的每个子类各注册一个分发器；未命中自定义模型时回落到原版渲染。
 */
public class TippedArrowRenderDispatcher extends RenderTippedArrow {

    public TippedArrowRenderDispatcher(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(@Nonnull EntityTippedArrow entity, double x, double y, double z, float entityYaw, float partialTick) {
        if (ArrowRenderDispatcher.tryRenderCustomModel(entity, x, y, z, entityYaw, partialTick)) {
            return;
        }
        super.doRender(entity, x, y, z, entityYaw, partialTick);
    }
}
