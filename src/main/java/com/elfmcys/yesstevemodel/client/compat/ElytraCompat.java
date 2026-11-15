package com.elfmcys.yesstevemodel.client.compat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * 鞘翅兼容。1.10.2 上没有 Mekanism 的 HDPE 鞘翅，只处理原版鞘翅。
 */
public class ElytraCompat {
    public static boolean isWearingElytra(EntityLivingBase player) {
        ItemStack stack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        return stack != null && stack.getItem() instanceof ItemElytra;
    }

    private static final ResourceLocation WINGS_LOCATION = new ResourceLocation("textures/entity/elytra.png");

    @Nonnull
    public static ResourceLocation getDefaultTexture(Item elytra) {
        return WINGS_LOCATION;
    }
}
