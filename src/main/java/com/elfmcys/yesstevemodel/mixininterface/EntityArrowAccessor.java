package com.elfmcys.yesstevemodel.mixininterface;

/**
 * 由 {@code EntityArrowMixin} 实现，供 Molang 的 {@code shoot_item_id} 读取
 * 射箭瞬间记录的主手物品 id。
 */
public interface EntityArrowAccessor {
    String ysm$getShootItemId();
}
