package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.util.PathSafety;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

public class UploadFile implements IPacketBufferMessage {
    private String name;
    private byte[] fileBytes;
    private Dir dir;

    public UploadFile() {
    }

    public UploadFile(String name, byte[] fileBytes, Dir dir) {
        this.name = name;
        this.fileBytes = fileBytes;
        this.dir = dir;
    }

    @Override
    public void toBytes(PacketBuffer buf) {
        buf.writeString(this.name);
        buf.writeByteArray(this.fileBytes);
        buf.writeEnumValue(this.dir);
    }

    @Override
    public void fromBytes(PacketBuffer buf) {
        this.name = buf.readString(Short.MAX_VALUE);
        this.fileBytes = buf.readByteArray();
        this.dir = buf.readEnumValue(Dir.class);
    }

    public static class Handler implements IMessageHandler<UploadFile, IMessage> {
        @Nullable
        @Override
        public IMessage onMessage(UploadFile message, MessageContext ctx) {
            if (!ctx.side.isServer()) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }
            // 网络线程只做调度，文件操作和聊天反馈都放到服务端世界线程执行
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                if (!player.canUseCommand(4, StringUtils.EMPTY)) {
                    YesSteveModel.LOGGER.warn("YSM: 拒绝玩家 {} 的上传请求：没有权限", player.getName());
                    notifyError(player, "没有权限执行该操作");
                    return;
                }
                writeFile(message, player);
            });
            return null;
        }

        private static void writeFile(UploadFile message, EntityPlayerMP player) {
            if (message.dir == null) {
                reject(player, message.name, "目录类型非法");
                return;
            }
            // 上传只允许单个纯文件名，路径、..、盘符一律拒绝
            if (!PathSafety.isPlainName(message.name)) {
                reject(player, message.name, "文件名非法（不允许路径、.. 或盘符）");
                return;
            }
            if (message.fileBytes == null) {
                reject(player, message.name, "文件内容为空");
                return;
            }
            File root = message.dir == Dir.CUSTOM
                    ? ServerModelManager.CUSTOM.toFile()
                    : ServerModelManager.AUTH.toFile();
            File file;
            try {
                file = PathSafety.resolveInside(root, message.name);
                // 写入前再做一次包含关系校验，避免后续改动破坏 resolveInside 的保证
                PathSafety.assertInside(root, file);
            } catch (IllegalArgumentException e) {
                reject(player, message.name, e.getMessage());
                return;
            }
            try {
                FileUtils.writeByteArrayToFile(file, message.fileBytes);
            } catch (IOException | RuntimeException e) {
                YesSteveModel.LOGGER.error("YSM: 玩家 {} 上传文件 {} 失败", player.getName(), message.name, e);
                notifyError(player, "写入文件失败：" + detail(e));
                return;
            }
            NetworkHandler.sendToClientPlayer(new CompleteFeedback(), player);
        }

        private static void reject(EntityPlayerMP player, String name, String reason) {
            YesSteveModel.LOGGER.warn("YSM: 拒绝玩家 {} 的上传请求，文件名 {}：{}", player.getName(), name, reason);
            notifyError(player, reason);
        }

        /**
         * 失败时也要给客户端明确提示，并发送完成反馈把客户端的“上传中”状态复位，
         * 否则玩家会一直卡在无法再次上传的状态。
         */
        private static void notifyError(EntityPlayerMP player, String reason) {
            player.sendMessage(new TextComponentString("[YSM] 上传失败：" + sanitize(reason)));
            NetworkHandler.sendToClientPlayer(new CompleteFeedback(), player);
        }

        /**
         * 客户端可控的文件名可能带 § 等格式字符，进聊天栏前先做无害化处理。
         */
        private static String sanitize(String text) {
            if (text == null) return "";
            return text.replace('§', '?').replace('\n', ' ').replace('\r', ' ');
        }

        private static String detail(Throwable e) {
            String message = e.getMessage();
            return StringUtils.isBlank(message) ? e.getClass().getSimpleName() : message;
        }
    }

    public enum Dir {
        CUSTOM,
        AUTH
    }
}
