package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.data.EncryptTools;
import com.elfmcys.yesstevemodel.model.format.ServerModelInfo;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.util.ThreadTools;
import com.elfmcys.yesstevemodel.util.UuidUtils;
import com.google.common.collect.Lists;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.elfmcys.yesstevemodel.model.ServerModelManager.*;

public class SyncModelFiles implements IPacketBufferMessage {
    /** 与 EncryptTools.MAX_MAP_ENTRIES 保持一致，避免客户端一次塞入过多条目 */
    private static final int MAX_MD5_ENTRIES = 4096;
    /** md5 固定为 32 位十六进制，64 已留足余量，同时避免超长字符串 */
    private static final int MAX_MD5_LENGTH = 64;

    private String[] md5Info;

    public SyncModelFiles() {
    }

    public SyncModelFiles(String[] md5Info) {
        this.md5Info = md5Info;
    }

    @Override
    public void toBytes(PacketBuffer buf) {
        buf.writeVarInt(this.md5Info.length);
        for (String md5 : this.md5Info) {
            buf.writeString(md5);
        }
    }

    @Override
    public void fromBytes(PacketBuffer buf) {
        int count = buf.readVarInt();
        // 先校验条目数，避免负数或超大数组直接分配/解析
        if (count < 0 || count > MAX_MD5_ENTRIES) {
            YesSteveModel.LOGGER.warn("YSM: SyncModelFiles 条目数非法（{}，上限 {}），已拒绝该包",
                    count, MAX_MD5_ENTRIES);
            // 直接抛出，让上层丢弃该包；不能当成空列表，否则服务端会把全部模型重新下发一遍
            throw new IllegalArgumentException("invalid SyncModelFiles entry count: " + count);
        }
        String[] output = new String[count];
        try {
            for (int i = 0; i < count; i++) {
                output[i] = buf.readString(MAX_MD5_LENGTH);
            }
        } catch (RuntimeException e) {
            // 超长字符串/缓冲区不足都属于非法包，记录后继续抛出，让上层丢弃该包
            YesSteveModel.LOGGER.warn("YSM: SyncModelFiles 解析失败（条目数 {}），已拒绝该包", count, e);
            throw e;
        }
        this.md5Info = output;
    }

    public static class Handler implements IMessageHandler<SyncModelFiles, IMessage> {
        @Nullable
        @Override
        public IMessage onMessage(SyncModelFiles message, MessageContext ctx) {
            if (ctx.side.isServer()) {
                FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                    EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
                    if (sender == null) {
                        return;
                    }
                    sendPassword(sender);
                    sendModelFiles(message.md5Info, sender);
                });
            }
            return null;
        }

        private static void sendModelFiles(String[] md5Info, EntityPlayerMP sender) {
            Collection<String> cache = CACHE_NAME_INFO.values().stream().map(ServerModelInfo::getMd5).collect(Collectors.toList());
            List<String> output = Lists.newArrayList(cache);
            for (String md5 : md5Info) {
                if (cache.contains(md5)) {
                    output.remove(md5);
                    NetworkHandler.sendToClientPlayer(new RequestLoadModel(md5), sender);
                }
            }
            for (String md5 : output) {
                File modelFile = CACHE_SERVER.resolve(md5).toFile();
                try {
                    byte[] modelBytes = FileUtils.readFileToByteArray(modelFile);
                    ThreadTools.THREAD_POOL.submit(() -> NetworkHandler.sendToClientPlayer(new SendModelFile(modelBytes), sender));
                } catch (IOException e) {
                    YesSteveModel.LOGGER.error("YSM: 读取服务端模型缓存文件 {} 失败（玩家 {} 将无法收到该模型）",
                            md5, sender.getName(), e);
                }
            }
        }

        private static void sendPassword(EntityPlayerMP sender) {
            try {
                byte[] password = FileUtils.readFileToByteArray(PASSWORD_FILE.toFile());
                byte[] uuid = UuidUtils.asBytes(sender.getUniqueID());
                byte[] output = EncryptTools.encryptPassword(uuid, password);
                ThreadTools.THREAD_POOL.submit(() -> NetworkHandler.sendToClientPlayer(new SendModelFile(output), sender));
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("YSM: 读取/加密模型密码文件 {} 失败（玩家 {} 将无法解密模型）",
                        PASSWORD_FILE, sender.getName(), e);
            }
        }
    }
}
