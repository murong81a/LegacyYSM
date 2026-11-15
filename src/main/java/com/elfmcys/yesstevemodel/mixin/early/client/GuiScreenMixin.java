package com.elfmcys.yesstevemodel.mixin.early.client;

import com.elfmcys.yesstevemodel.util.ComponentUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.10.2 的 {@code GuiScreen.handleComponentClick(ITextComponent)} 是
 * {@code protected boolean} 实例方法（不是 1.12.2 的 static），所以处理器也必须是
 * 实例方法。
 * <p>
 * 字节码中第一次 {@code ClickEvent.getAction()} 在 offset 56，位于
 * “Shift 按下 + insertion” 分支（offset 16-48）之后、null 检查（offset 51-52）之后，
 * 因此在这个 INVOKE 上注入不会破坏 Shift 点击插入文本的行为。
 * <p>
 * 与原 1.12.2 代码的差异：原代码只调用 {@code cir.cancel()}，对
 * {@code CallbackInfoReturnable<Boolean>} 而言注入器会以默认值 {@code false}
 * 提前返回，表示“未处理”。这里改为 {@code setReturnValue(true)}，同样提前返回，
 * 但把返回值设为 {@code true}，表示“点击已处理”。
 */
@Mixin(GuiScreen.class)
public class GuiScreenMixin {
    @Inject(
            method = "handleComponentClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/text/event/ClickEvent;getAction()Lnet/minecraft/util/text/event/ClickEvent$Action;",
                    ordinal = 0
            ),
            cancellable = true
    )
    private void ysm$handleCopyClick(ITextComponent component, CallbackInfoReturnable<Boolean> cir) {
        if (component == null) {
            return;
        }
        ClickEvent clickEvent = component.getStyle().getClickEvent();
        if (clickEvent != null && clickEvent.getAction() == ComponentUtils.COPY_TO_CLIPBOARD) {
            GuiScreen.setClipboardString(clickEvent.getValue());
            cir.setReturnValue(true);
        }
    }
}
