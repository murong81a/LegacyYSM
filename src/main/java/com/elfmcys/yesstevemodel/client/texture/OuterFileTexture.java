package com.elfmcys.yesstevemodel.client.texture;

import com.elfmcys.yesstevemodel.YesSteveModel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class OuterFileTexture extends AbstractTexture {
    private final byte[] data;
    private final String name;

    public OuterFileTexture(byte[] data) {
        this(data, "unknown");
    }

    public OuterFileTexture(byte[] data, String name) {
        this.data = data;
        this.name = name;
    }

    @Override
    public void loadTexture(@Nonnull IResourceManager resourceManager) {
        this.deleteGlTexture();

        BufferedImage bufferedimage = null;
        try (ByteArrayInputStream is = new ByteArrayInputStream(this.data)) {
            bufferedimage = ImageIO.read(is);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (bufferedimage != null) {
            TextureUtil.uploadTextureImageAllocate(this.getGlTextureId(), bufferedimage, false, false);
        } else {
            // ImageIO 读不出来时返回 null 而不抛异常（例如作者把 WebP 改名成 .png，
            // Java 8 没有 WebP 解码器）。以前这里静默跳过，结果模型渲染成白模、
            // 日志一行都没有。至少要说清楚是哪个文件、大概什么格式。
            String kind = "unknown";
            if (this.data.length >= 12) {
                if (this.data[0] == 'R' && this.data[1] == 'I' && this.data[2] == 'F' && this.data[3] == 'F') {
                    kind = "WebP(RIFF)";
                } else if (this.data.length >= 8 && (this.data[0] & 0xFF) == 0xFF && (this.data[1] & 0xFF) == 0xD8) {
                    kind = "JPEG";
                }
            }
            YesSteveModel.LOGGER.warn(
                    "YSM: 贴图 {} 无法解码（{} 字节，疑似 {}）。模型会渲染成白模；请把贴图转成真正的 PNG。",
                    this.name, this.data.length, kind);
        }
    }
}
