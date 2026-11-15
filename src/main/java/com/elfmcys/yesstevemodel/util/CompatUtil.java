package com.elfmcys.yesstevemodel.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;
import com.elfmcys.yesstevemodel.util.CompatUtil;

/**
 * 1.10.2 缺失 API 的兼容层。
 * <p>
 * 1.12.2 里很多便利方法在 1.10.2 并不存在，这里统一提供等价实现，避免各处重复写。
 */
public final class CompatUtil {
    private CompatUtil() {
    }

    /**
     * 等价于 1.12.2 的 {@code ItemStack.isEmpty()}：1.10.2 用 {@code null} 表示空槽。
     */
    public static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.stackSize <= 0;
    }

    /**
     * 等价于 1.12.2 的 {@code PacketBuffer#writeResourceLocation}。
     */
    public static void writeResourceLocation(PacketBuffer buf, @Nullable ResourceLocation location) {
        buf.writeBoolean(location != null);
        if (location != null) {
            buf.writeString(location.getResourceDomain());
            buf.writeString(location.getResourcePath());
        }
    }

    /**
     * 等价于 1.12.2 的 {@code PacketBuffer#readResourceLocation}。
     */
    @Nullable
    public static ResourceLocation readResourceLocation(PacketBuffer buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        String domain = buf.readString(Short.MAX_VALUE);
        String path = buf.readString(Short.MAX_VALUE);
        return new ResourceLocation(domain, path);
    }

    /**
     * 等价于 1.12.2 的 {@code CompatUtil.getEntityId(Entity)}。
     * <p>
     * 1.10.2 的 {@code EntityList} 没有注册名表，{@code getEntityString} 返回的是内部名字
     * （如 {@code Horse}、{@code Pig}），这里转成 {@code minecraft:horse} 这样的资源位置。
     */
    @Nullable
    public static ResourceLocation getEntityId(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        return toResourceLocation(EntityList.getEntityString(entity));
    }

    /**
     * 等价于 1.12.2 的 {@code CompatUtil.getEntityId(Class)}。
     */
    @Nullable
    public static ResourceLocation getEntityId(@Nullable Class<? extends Entity> clazz) {
        if (clazz == null) {
            return null;
        }
        return toResourceLocation(EntityList.getEntityStringFromClass(clazz));
    }

    @Nullable
    private static ResourceLocation toResourceLocation(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return new ResourceLocation(name.toLowerCase(Locale.ENGLISH));
    }
}
