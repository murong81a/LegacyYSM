package com.elfmcys.yesstevemodel.client.compat;

import net.minecraft.item.ItemStack;

/**
 * 额外动作（长矛/弩/望远镜）。这些模组在 1.10.2 整合包里都不存在，暂时恒为 false。
 */
public enum ExtraAction {
    SPEAR {
        @Override
        public boolean isAction(ItemStack stack) {
            return false;
        }
    },
    CROSSBOW {
        @Override
        public boolean isAction(ItemStack stack) {
            return false;
        }
    },
    SPYGLASS {
        @Override
        public boolean isAction(ItemStack stack) {
            return false;
        }
    };

    public abstract boolean isAction(ItemStack stack);
}
