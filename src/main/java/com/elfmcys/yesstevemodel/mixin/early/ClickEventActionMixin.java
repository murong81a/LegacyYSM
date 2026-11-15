package com.elfmcys.yesstevemodel.mixin.early;

import com.elfmcys.yesstevemodel.util.ComponentUtils;
import net.minecraft.util.text.event.ClickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 1.10.2 的 {@link ClickEvent.Action} 与 1.12.2 结构一致：
 * 只有 5 个原版常量，私有静态 {@code NAME_MAPPING} 在 {@code <clinit>} 里由
 * {@code Maps.newHashMap()} 创建，然后遍历 {@code values()} 填入 canonical name。
 * <p>
 * {@code ComponentUtils.COPY_TO_CLIPBOARD} 通过 Forge 的 {@code EnumHelper.addEnum}
 * 反射扩展 {@code $VALUES}；在 {@code <clinit>} 的 RETURN 处，{@code $VALUES} 与
 * {@code NAME_MAPPING} 都已初始化完成，因此这里可以安全地把自定义 action 注册进
 * {@code NAME_MAPPING}，让 {@code Style.Serializer} 的 JSON 反序列化能够识别
 * {@code "copy_to_clipboard"}。
 * <p>
 * 额外兜底：如果 {@code ComponentUtils} 先于 {@code ClickEvent.Action} 初始化，则
 * {@code <clinit>} 回调执行时 {@code ComponentUtils.COPY_TO_CLIPBOARD} 仍为 {@code null}，
 * 会跳过注册。{@link #ysm$ensureCopyActionRegistered} 在首次反序列化查询时再尝试一次，
 * 覆盖这种类初始化顺序。
 * <p>
 * 1.10.2 的 {@code ClickEvent.Action} 没有 1.12.2 之后新增的 action，但本注入只依赖
 * 字段名与 canonical name，不依赖枚举常量数量。
 */
@Mixin(ClickEvent.Action.class)
public class ClickEventActionMixin {
    @Shadow
    @Final
    private static Map<String, ClickEvent.Action> NAME_MAPPING;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void ysm$registerCopyAction(CallbackInfo ci) {
        // EnumHelper.addEnum 失败时 COPY_TO_CLIPBOARD 为 null，此时不能注册。
        if (ComponentUtils.COPY_TO_CLIPBOARD != null) {
            // putIfAbsent：如果另一个 mod 已经注册了同名 action，不要覆盖它。
            NAME_MAPPING.putIfAbsent(
                    ComponentUtils.COPY_TO_CLIPBOARD.getCanonicalName(),
                    ComponentUtils.COPY_TO_CLIPBOARD
            );
        }
    }

    /**
     * 反序列化兜底：{@code Style.Serializer} 在读取 clickEvent 的 action 时会调用
     * {@code getValueByCanonicalName}。如果 {@code <clinit>} 回调因类初始化顺序错过了
     * 注册，这里在真正查询前补一次；正常顺序下 map 已包含目标 key，直接返回。
     */
    @Inject(method = "getValueByCanonicalName", at = @At("HEAD"))
    private static void ysm$ensureCopyActionRegistered(
            String canonicalName,
            CallbackInfoReturnable<ClickEvent.Action> cir
    ) {
        if (NAME_MAPPING == null || NAME_MAPPING.containsKey(canonicalName)) {
            return;
        }
        // 只对目标 canonical name 触发 ComponentUtils 初始化，避免其他 mod 的未知
        // action 查询把本类的初始化提前拉起来。
        if (!"copy_to_clipboard".equals(canonicalName)) {
            return;
        }
        if (ComponentUtils.COPY_TO_CLIPBOARD != null) {
            NAME_MAPPING.putIfAbsent(
                    ComponentUtils.COPY_TO_CLIPBOARD.getCanonicalName(),
                    ComponentUtils.COPY_TO_CLIPBOARD
            );
        }
    }
}
