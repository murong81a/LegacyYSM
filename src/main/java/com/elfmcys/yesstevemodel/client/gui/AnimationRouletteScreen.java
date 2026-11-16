package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.client.config.GeneralConfig;
import com.elfmcys.yesstevemodel.client.input.AnimationRouletteKey;
import com.elfmcys.yesstevemodel.client.input.ExtraAnimationKey;
import com.elfmcys.yesstevemodel.event.CapabilityEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.ExtraInfo;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.SetPlayAnimation;
import com.elfmcys.yesstevemodel.util.ModelIdUtil;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.io.IOException;

public class AnimationRouletteScreen extends Screen {
    /// 一圈显示的槽位数；实际可用槽位由模型的 extra_animation_names 长度决定，超出部分靠翻页
    private static final int SLOT_COUNT = 8;
    private int x;
    private int y;
    private int selectId = -1;
    /// 当前页，每页 SLOT_COUNT 个槽位
    private int page = 0;
    private @Nullable String[] names;

    public AnimationRouletteScreen() {
    }

    @Override
    public void initGui() {
        this.x = this.width / 2;
        this.y = this.height / 2 - 8;
        // 模型同步是异步的（登录/切换世界/重载资源包期间 EXTRA_INFO 可能为空），
        // 因此这里必须先兜底为空数组，否则开轮盘界面会 NPE 崩游戏
        this.names = new String[0];

        if (this.mc != null && this.mc.player != null) {
            CapabilityEvent.getModelInfoCap(this.mc.player).ifPresent(cap -> {
                ResourceLocation modelId = cap.getModelId();
                if (modelId == null) {
                    return;
                }
                ExtraInfo extraInfo = ClientModelManager.EXTRA_INFO.get(ModelIdUtil.getInfoId(modelId));
                if (extraInfo == null) {
                    return;
                }
                String[] extraNames = extraInfo.getExtraAnimationNames();
                if (extraNames != null) {
                    this.names = extraNames;
                }
            });
        }
    }

    @Override
    public void drawScreen(int pMouseX, int pMouseY, float pPartialTick) {
        this.drawRoulette(pMouseX, pMouseY);
        this.drawRouletteText();
    }

    @Override
    public void mouseClicked(int pMouseX, int pMouseY, int pButton) throws IOException {
        int slot = this.page * SLOT_COUNT + this.selectId;
        int total = this.names == null ? 0 : this.names.length;
        if (this.selectId >= 0 && slot >= 0 && slot < total && this.mc != null) {
            this.mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            NetworkHandler.CHANNEL.sendToServer(new SetPlayAnimation(slot));
            if (this.mc.player != null && GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG) {
                this.mc.player.sendMessage(new TextComponentTranslation("message.yes_steve_model.model.animation_roulette.play", slot));
            }
            this.mc.displayGuiScreen(null);
        }
        super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    /**
     * 滚轮翻页：额外动画多于 8 个时（最多见过 36 个），原来的轮盘只能按到前 8 个。
     */
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel != 0) {
            int maxPage = this.maxPage();
            this.page += dWheel > 0 ? -1 : 1;
            if (this.page < 0) this.page = maxPage;
            if (this.page > maxPage) this.page = 0;
        }
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        super.handleKeyboardInput();
        if (Keyboard.getEventKeyState()) {
            int key = Keyboard.getEventKey();
            int maxPage = this.maxPage();
            if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_PRIOR) {
                this.page = this.page > 0 ? this.page - 1 : maxPage;
            } else if (key == Keyboard.KEY_RIGHT || key == Keyboard.KEY_NEXT) {
                this.page = this.page < maxPage ? this.page + 1 : 0;
            }
        }
    }

    private int maxPage() {
        int total = this.names == null ? 0 : this.names.length;
        return total <= SLOT_COUNT ? 0 : (total - 1) / SLOT_COUNT;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected boolean canGuiClose(int keyCode) {
        return super.canGuiClose(keyCode) || isKeyActiveIgnoreConflict(AnimationRouletteKey.ANIMATION_ROULETTE_KEY, keyCode);
    }

    private void drawRouletteText() {
        int count = SLOT_COUNT;
        int total = this.names == null ? 0 : this.names.length;
        int maxPage = this.maxPage();
        if (this.page > maxPage) this.page = maxPage;
        float startDeg = (float) Math.PI / count;
        for (int i = 0; i < count; i++) {
            int r = 65;
            int slot = this.page * count + i;
            String keyText = TextFormatting.YELLOW + "[ ";
            KeyBinding keyMapping = ExtraAnimationKey.EXTRA_ANIMATION_KEYS.get(i);
            if (keyMapping.getKeyCode() == Keyboard.KEY_NONE) {
                keyText += I18n.format("key.yes_steve_model.extra_animation.none");
            } else {
                keyText += keyMapping.getDisplayName();
            }
            keyText += " ]";
            String label = slot < total ? this.names[slot] : null;
            if (StringUtils.isNoneBlank(label)) {
                this.drawCenteredString(this.fontRendererObj, label, (int) (this.x + r * MathHelper.cos(startDeg)), (int) (this.y + r * MathHelper.sin(startDeg) - (float) this.fontRendererObj.FONT_HEIGHT / 2 - 8), 0xF3EFE0);
            } else {
                this.drawCenteredString(this.fontRendererObj, String.valueOf(slot), (int) (this.x + r * MathHelper.cos(startDeg)), (int) (this.y + r * MathHelper.sin(startDeg) - (float) this.fontRendererObj.FONT_HEIGHT / 2 - 8), 0xF3EFE0);
            }
            this.drawCenteredString(this.fontRendererObj, keyText, (int) (this.x + r * MathHelper.cos(startDeg)), (int) (this.y + r * MathHelper.sin(startDeg) - (float) this.fontRendererObj.FONT_HEIGHT / 2 + 4), 0xF3EFE0);
            startDeg = startDeg + 2 * (float) Math.PI / count;
        }
        if (maxPage > 0) {
            String hint = I18n.format("message.yes_steve_model.model.animation_roulette.page", this.page + 1, maxPage + 1);
            this.drawCenteredString(this.fontRendererObj, hint, this.x, this.y - 118, 0xF3EFE0);
        }
    }

    private void drawRoulette(int mouseX, int mouseY) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tesselator = Tessellator.getInstance();
        VertexBuffer bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        int count = SLOT_COUNT;
        float theta = (float) MathHelper.atan2(mouseY - this.y, mouseX - this.x);
        if (theta < 0) {
            theta = (float) Math.PI * 2 + theta;
        }
        float dx = mouseX - this.x;
        float dy = mouseY - this.y;
        float distance = MathHelper.sqrt(dx * dx + dy * dy);
        boolean isSelected = false;
        for (int i = 0; i < count; i++) {
            float spacingDeg = (float) Math.PI / 90;
            float startDeg = (2 * (float) Math.PI / count) * i + spacingDeg;
            float endDeg = (2 * (float) Math.PI / count) * (i + 1) - spacingDeg;
            if (startDeg < theta && theta < endDeg && 50 < distance && distance < 100) {
                this.drawFan(bufferbuilder, 25, 105, startDeg, endDeg, 0xf0FFB100);
                isSelected = true;
                this.selectId = i;
            } else {
                this.drawFan(bufferbuilder, 25, 105, startDeg, endDeg, 0x90000000);
            }
        }
        if (!isSelected) {
            this.selectId = -1;
        }

        tesselator.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private void drawFan(VertexBuffer builder, float rIn, float rOut, float startDeg, float endDeg, int color) {
        float alpha = (color >> 24 & 255) / 255.0F;
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        builder.pos(this.x + rOut * MathHelper.cos(startDeg), this.y + rOut * MathHelper.sin(startDeg), this.zLevel).color(red, green, blue, alpha).endVertex();
        builder.pos(this.x + rIn * MathHelper.cos(startDeg), this.y + rIn * MathHelper.sin(startDeg), this.zLevel).color(red, green, blue, alpha).endVertex();
        builder.pos(this.x + rIn * MathHelper.cos(endDeg), this.y + rIn * MathHelper.sin(endDeg), this.zLevel).color(red, green, blue, alpha).endVertex();
        builder.pos(this.x + rOut * MathHelper.cos(endDeg), this.y + rOut * MathHelper.sin(endDeg), this.zLevel).color(red, green, blue, alpha).endVertex();
    }
}
