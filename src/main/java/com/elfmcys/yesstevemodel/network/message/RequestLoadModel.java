package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.data.EncryptTools;
import com.elfmcys.yesstevemodel.data.ModelData;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.util.ThreadTools;
import com.elfmcys.yesstevemodel.util.UuidUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.UUID;

public class RequestLoadModel implements IPacketBufferMessage {
    private String fileName;

    public RequestLoadModel() {
    }

    public RequestLoadModel(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void toBytes(PacketBuffer buf) {
        buf.writeString(this.fileName);
    }

    @Override
    public void fromBytes(PacketBuffer buf) {
        // 文件名只可能是 32 位 md5，限制长度避免恶意包撑爆字符串
        this.fileName = buf.readString(256);
    }

    public static class Handler implements IMessageHandler<RequestLoadModel, IMessage> {
        @Nullable
        @Override
        public IMessage onMessage(RequestLoadModel message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                handleClient(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleClient(RequestLoadModel message) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (!isValidFileName(message.fileName)) {
                    YesSteveModel.LOGGER.warn("YSM: 服务端下发了非法模型文件名 {}，已拒绝", message.fileName);
                    return;
                }
                ClientModelManager.CACHE_MD5.add(message.fileName);
                loadModel(message.fileName);
            });
        }
    }

    /**
     * 服务端只会下发缓存文件的 32 位大写 md5；其它形式一律视为非法输入，
     * 避免 {@code CACHE_CLIENT.resolve(fileName)} 被 {@code ../} 穿越读取本地任意文件。
     */
    @SideOnly(Side.CLIENT)
    private static boolean isValidFileName(String fileName) {
        return fileName != null && fileName.matches("[0-9A-F]{32}");
    }

    @SideOnly(Side.CLIENT)
    public static void loadModel(String fileName) {
        if (!isValidFileName(fileName)) {
            YesSteveModel.LOGGER.warn("YSM: 拒绝加载非法模型文件名 {}", fileName);
            return;
        }
        ThreadTools.THREAD_POOL.submit(() -> {
            try {
                // 有超时的等待：非 YSM 服务器 / 密码包丢失时不再永久占用线程池线程
                if (!ThreadTools.awaitUntil(() -> ClientModelManager.PASSWORD != null,
                        ThreadTools.DEFAULT_WAIT_TIMEOUT_MS)) {
                    YesSteveModel.LOGGER.warn("YSM: 等待服务器密码超时（{} ms），跳过模型 {}",
                            ThreadTools.DEFAULT_WAIT_TIMEOUT_MS, fileName);
                    return;
                }
                if (Minecraft.getMinecraft().player == null) {
                    YesSteveModel.LOGGER.warn("YSM: 玩家尚未进入世界，跳过模型 {}", fileName);
                    return;
                }
                UUID uuid = Minecraft.getMinecraft().player.getUniqueID();
                Path modelFile = ServerModelManager.CACHE_CLIENT.resolve(fileName);
                byte[] fileBytes = FileUtils.readFileToByteArray(modelFile.toFile());
                byte[] password = ClientModelManager.PASSWORD;
                ModelData data = EncryptTools.decryptModel(UuidUtils.asBytes(uuid), password, fileBytes);
                if (data == null) {
                    // 解密失败是"模型缺失"的直接原因，必须留下明确日志，否则客户端只会表现为模型没加载
                    YesSteveModel.LOGGER.warn("YSM: 模型 {} 解密失败（缓存 {} 字节，密码{}），本客户端将缺少该模型",
                            fileName, fileBytes.length, password == null ? "未收到" : "已收到");
                    return;
                }
                Minecraft.getMinecraft().addScheduledTask(() -> ClientModelManager.registerAll(data));
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("YSM: 加载模型 {} 失败", fileName, e);
            }
        });
    }
}
