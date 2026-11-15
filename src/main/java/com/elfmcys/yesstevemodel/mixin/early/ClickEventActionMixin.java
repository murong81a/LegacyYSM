package com.elfmcys.yesstevemodel.mixin.early;

import com.elfmcys.yesstevemodel.util.ComponentUtils;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 1.10.2 的 {@link ClickEvent.Action} 与 1.12.2 结构一致：只有 5 个原版常量，
 * 私有静态 {@code NAME_MAPPING} 在 {@code <clinit>} 里创建，然后遍历
 * {@code values()} 填入 canonical name。
 * <p>
 * {@code ComponentUtils.COPY_TO_CLIPBOARD} 通过 Forge 的 {@code EnumHelper.addEnum}
 * 反射扩展 {@code $VALUES}；在 {@code <clinit>} 的 RETURN 处 {@code $VALUES} 与
 * {@code NAME_MAPPING} 都已初始化完成，因此可以安全地把自定义 action 注册进去，
 * 让 {@code Style.Serializer} 的 JSON 反序列化能识别 {@code "copy_to_clipboard"}。
 * <p>
 * 这里刻意<b>不用 {@code @Shadow}</b> 取 {@code NAME_MAPPING}：生产环境里该字段叫
 * {@code field_150679_e}（运行时是 notch 名），必须先经 refmap 再经 SRG→notch
 * 重映射；实测 MixinBooter 11.18 下 {@code @Shadow} 字段的 refmap 条目不会被应用，
 * 注入时直接报 {@code @Shadow field NAME_MAPPING was not located}。
 * 改用 Forge 的 {@link ObfuscationReflectionHelper}（同时给出 MCP 名与 SRG 名），
 * dev 与生产都能取到，也就不需要为字段准备 refmap 条目。
 * <p>
 * 额外兜底：如果 {@code ComponentUtils} 先于 {@code ClickEvent.Action} 初始化，
 * {@code <clinit>} 回调里 {@code COPY_TO_CLIPBOARD} 仍是 {@code null}，
 * {@link #ysm$ensureCopyActionRegistered} 会在首次按名查询时再补一次。
 */
@Mixin(ClickEvent.Action.class)
public class ClickEventActionMixin {

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void ysm$registerCopyAction(CallbackInfo ci) {
        ysm$putCopyAction(ysm$nameMapping());
    }

    @Inject(method = "getValueByCanonicalName", at = @At("HEAD"))
    private static void ysm$ensureCopyActionRegistered(
            String canonicalName,
            CallbackInfoReturnable<ClickEvent.Action> cir
    ) {
        // 只对目标 canonical name 触发 ComponentUtils 初始化，避免其他 mod 的
        // 未知 action 查询把本类的初始化提前拉起来。
        if (!"copy_to_clipboard".equals(canonicalName)) {
            return;
        }
        Map<String, ClickEvent.Action> nameMapping = ysm$nameMapping();
        if (nameMapping != null && !nameMapping.containsKey(canonicalName)) {
            ysm$putCopyAction(nameMapping);
        }
    }

    @Unique
    private static void ysm$putCopyAction(Map<String, ClickEvent.Action> nameMapping) {
        if (nameMapping == null || ComponentUtils.COPY_TO_CLIPBOARD == null) {
            return;
        }
        // putIfAbsent：如果另一个 mod 已经注册了同名 action，不要覆盖它。
        nameMapping.putIfAbsent(
                ComponentUtils.COPY_TO_CLIPBOARD.getCanonicalName(),
                ComponentUtils.COPY_TO_CLIPBOARD
        );
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static Map<String, ClickEvent.Action> ysm$nameMapping() {
        try {
            return (Map<String, ClickEvent.Action>) ObfuscationReflectionHelper.getPrivateValue(
                    ClickEvent.Action.class, null, "NAME_MAPPING", "field_150679_e");
        } catch (Throwable t) {
            return null;
        }
    }
}
