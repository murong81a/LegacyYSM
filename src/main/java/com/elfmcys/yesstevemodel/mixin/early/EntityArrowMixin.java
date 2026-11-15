package com.elfmcys.yesstevemodel.mixin.early;

import com.elfmcys.yesstevemodel.event.CapabilityEvent;
import com.elfmcys.yesstevemodel.mixininterface.EntityArrowAccessor;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.10.2 的 {@link EntityArrow} 有三个构造器，其中
 * {@code (World, EntityLivingBase)} 会在 offset 29 写入 {@code shootingEntity}，
 * 在 offset 46 返回；注入 RETURN 可以拿到射手和射箭瞬间的主手物品。
 * <p>
 * 注意：1.10.2 的空 ItemStack 用 {@code null} 表示，没有 {@code ItemStack.EMPTY} /
 * {@code isEmpty()}，因此必须逐层判空。
 * <p>
 * 原 1.12.2 mixin 还在构造器里把射手模型 id 复制给箭的能力。移植版已经在
 * {@code CapabilityEvent.onEntityJoinWorld} 的 {@code EntityArrow} 分支里做了同样的事，
 * 这里不再重复，避免两条路径互相覆盖。
 */
@Mixin(EntityArrow.class)
public class EntityArrowMixin implements EntityArrowAccessor {
    @Unique
    private static final String MODEL_ID_TAG = "YsmArrowModelId";

    @Unique
    private String ysm$shootItemId;

    @Inject(
            method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;)V",
            at = @At("RETURN")
    )
    private void ysm$recordShootItem(World world, EntityLivingBase shooter, CallbackInfo ci) {
        // 记录射箭时所用的主手物品，供 Molang 的 shoot_item_id 使用。
        if (shooter == null) {
            return;
        }
        ItemStack held = shooter.getHeldItemMainhand();
        if (held == null) {
            return;
        }
        Item item = held.getItem();
        if (item == null) {
            return;
        }
        ResourceLocation itemId = item.getRegistryName();
        if (itemId != null) {
            this.ysm$shootItemId = itemId.toString();
        }
    }

    @Unique
    @Override
    public String ysm$getShootItemId() {
        // 某些构造器（例如发射器生成的箭）不会走上面的注入，字段保持 null；
        // 这里兜底为原版的空字符串，避免 Molang 侧拿到 null。
        return this.ysm$shootItemId == null ? "" : this.ysm$shootItemId;
    }

    /**
     * 旧存档兼容：1.12.2 版本曾把箭的模型 id 直接写在
     * {@code YsmArrowModelId} 这个 NBT 标签里。1.10.2 移植版改用能力存储，
     * 读取旧标签后转存到能力并删除标签。
     * <p>
     * 1.10.2 的 {@code Entity.readFromNBT} 先反序列化 ForgeCaps，再调用
     * {@code readEntityFromNBT}（offset 512 / 595），所以这里在 RETURN 写入能力
     * 会覆盖旧存档里可能同时存在的 ForgeCaps 值；旧存档通常只有旧标签，不受影响。
     */
    @Inject(method = "readEntityFromNBT", at = @At("RETURN"))
    private void ysm$readLegacyArrowModelId(NBTTagCompound compound, CallbackInfo ci) {
        if (compound == null || !compound.hasKey(MODEL_ID_TAG)) {
            return;
        }
        EntityArrow self = (EntityArrow) (Object) this;
        String modelId = compound.getString(MODEL_ID_TAG);
        CapabilityEvent.getArrowModelCap(self).ifPresent(cap -> cap.setModelId(modelId));
        compound.removeTag(MODEL_ID_TAG);
    }
}
