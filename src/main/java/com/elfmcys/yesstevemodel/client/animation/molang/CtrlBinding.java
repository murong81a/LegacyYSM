package com.elfmcys.yesstevemodel.client.animation.molang;

import com.elfmcys.yesstevemodel.client.compat.SwimmingCompat;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.binding.ContextBinding;
import com.elfmcys.yesstevemodel.geckolib3.util.Interpolations;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * {@code ctrl.*} 命名空间：模型 controller 与动画表达式使用的“玩家控制状态”布尔量。
 * <p>
 * V3 模型（以及部分 V2 模型）会在动画表达式里写 {@code ctrl.walk} / {@code ctrl.fly} 之类，
 * 在没有这个绑定时整条表达式解析失败（日志里是 {@code Failed to get property: ctrl}），
 * 对应的骨骼通道就永远拿不到值。这里按 {@code AnimationRegister} 里硬编码状态机的判定口径实现，
 * 保证 molang 与内置状态机看到的状态一致。
 */
public class CtrlBinding extends ContextBinding {
    public static final CtrlBinding INSTANCE = new CtrlBinding();

    private static final double MIN_SPEED = 0.05;

    private CtrlBinding() {
        this.entityVar("walk", ctx -> isMoving(ctx.entity()) && ctx.entity().onGround && !ctx.entity().isSneaking());
        this.entityVar("sneak", ctx -> ctx.entity().onGround && ctx.entity().isSneaking() && isMoving(ctx.entity()));
        this.entityVar("sneaking", ctx -> ctx.entity().isSneaking() && !isMoving(ctx.entity()));
        this.entityVar("run", ctx -> isMoving(ctx.entity()) && ctx.entity().onGround
                && !ctx.entity().isSneaking() && ctx.entity().isSprinting());
        this.entityVar("jump", ctx -> !ctx.entity().onGround && !ctx.entity().isInWater());
        this.entityVar("fly", ctx -> isFlying(ctx.entity()));
        this.entityVar("elytra_fly", ctx -> isElytraFlying(ctx.entity()));
        this.entityVar("swim", ctx -> ctx.entity().isInWater());
        this.entityVar("swim_stand", ctx -> ctx.entity().isInWater());
        this.entityVar("climb", ctx -> isSwimmingPose(ctx.entity()) && isMoving(ctx.entity()));
        this.entityVar("climbing", ctx -> isSwimmingPose(ctx.entity()));
        this.entityVar("ride", ctx -> ctx.entity().isRiding());
        this.entityVar("sit", ctx -> ctx.entity().isRiding());
        this.playerEntityVar("sleep", ctx -> ctx.entity().isPlayerSleeping());
        this.livingEntityVar("attacked", ctx -> ctx.entity().hurtTime > 0);
        this.livingEntityVar("death", ctx -> !ctx.entity().isEntityAlive());
        this.entityVar("on_ground", ctx -> ctx.entity().onGround);
        this.entityVar("is_moving", ctx -> isMoving(ctx.entity()));
        this.entityVar("is_sneaking", ctx -> ctx.entity().isSneaking());
        this.entityVar("is_sprinting", ctx -> ctx.entity().isSprinting());
        this.entityVar("is_flying", ctx -> isFlying(ctx.entity()));
    }

    private static boolean isMoving(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) return false;
        EntityLivingBase living = (EntityLivingBase) entity;
        float partialTick = Minecraft.getMinecraft().getRenderPartialTicks();
        float speed = Interpolations.lerp(living.prevLimbSwingAmount, living.limbSwingAmount, partialTick);
        return Math.abs(speed) > MIN_SPEED;
    }

    private static boolean isSprinting(Entity entity) {
        return entity.isSprinting();
    }

    private static boolean isSwimmingPose(Entity entity) {
        return entity instanceof EntityPlayer && SwimmingCompat.isSwimmingPose((EntityPlayer) entity);
    }

    private static boolean isFlying(Entity entity) {
        return entity instanceof EntityPlayer && ((EntityPlayer) entity).capabilities.isFlying;
    }

    private static boolean isElytraFlying(Entity entity) {
        return entity instanceof EntityLivingBase && ((EntityLivingBase) entity).isElytraFlying();
    }
}
