package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
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

public class HandleFile implements IPacketBufferMessage {
    private String name;
    private UploadFile.Dir dir;
    private String action;
    private String rename;

    public HandleFile() {
    }

    public HandleFile(String name, UploadFile.Dir dir, String action, String rename) {
        this.name = name;
        this.dir = dir;
        this.action = action;
        this.rename = rename;
    }

    @Override
    public void toBytes(PacketBuffer buf) {
        buf.writeString(this.name);
        buf.writeEnumValue(this.dir);
        buf.writeString(this.action);
        buf.writeString(this.rename);
    }

    @Override
    public void fromBytes(PacketBuffer buf) {
        this.name = buf.readString(Short.MAX_VALUE);
        this.dir = buf.readEnumValue(UploadFile.Dir.class);
        this.action = buf.readString(Short.MAX_VALUE);
        this.rename = buf.readString(Short.MAX_VALUE);
    }

    public static class Handler implements IMessageHandler<HandleFile, IMessage> {
        private static final String ACTION_DELETE = "delete";
        private static final String ACTION_MOVE = "move";
        private static final String ACTION_RENAME = "rename";

        @Nullable
        @Override
        public IMessage onMessage(HandleFile message, MessageContext ctx) {
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
                    YesSteveModel.LOGGER.warn("YSM: 拒绝玩家 {} 的文件操作 {}：没有权限", player.getName(), message.action);
                    notifyError(player, "没有权限执行该操作");
                    return;
                }
                handle(message, player);
            });
            return null;
        }

        private static void handle(HandleFile message, EntityPlayerMP player) {
            if (message.dir == null) {
                reject(player, message, "目录类型非法");
                return;
            }
            // 模型管理只允许操作根目录下一级的文件/文件夹，路径、..、盘符一律拒绝
            if (!PathSafety.isPlainName(message.name)) {
                reject(player, message, "文件名非法（不允许路径、.. 或盘符）");
                return;
            }
            if (message.action == null || message.action.isEmpty()) {
                reject(player, message, "操作类型为空");
                return;
            }
            File root = message.dir == UploadFile.Dir.CUSTOM
                    ? ServerModelManager.CUSTOM.toFile()
                    : ServerModelManager.AUTH.toFile();
            File file;
            try {
                file = PathSafety.resolveInside(root, message.name);
                PathSafety.assertInside(root, file);
            } catch (IllegalArgumentException e) {
                reject(player, message, e.getMessage());
                return;
            }
            if (!file.exists()) {
                fail(player, "文件不存在：" + message.name);
                return;
            }
            if (ACTION_DELETE.equals(message.action)) {
                delete(player, root, file);
            } else if (ACTION_MOVE.equals(message.action)) {
                move(message, player, root, file);
            } else if (ACTION_RENAME.equals(message.action)) {
                rename(message, player, root, file);
            } else {
                reject(player, message, "未知操作");
            }
        }

        private static void delete(EntityPlayerMP player, File root, File file) {
            try {
                // 删除前再确认一次目标仍在根目录内
                PathSafety.assertInside(root, file);
                FileUtils.forceDelete(file);
            } catch (IOException | RuntimeException e) {
                YesSteveModel.LOGGER.error("YSM: 玩家 {} 删除文件 {} 失败", player.getName(), file, e);
                notifyError(player, "删除文件失败：" + detail(e));
            }
        }

        private static void move(HandleFile message, EntityPlayerMP player, File sourceRoot, File file) {
            File destRoot = message.dir == UploadFile.Dir.CUSTOM
                    ? ServerModelManager.AUTH.toFile()
                    : ServerModelManager.CUSTOM.toFile();
            File dest;
            try {
                dest = PathSafety.resolveInside(destRoot, message.name);
                PathSafety.assertInside(destRoot, dest);
            } catch (IllegalArgumentException e) {
                reject(player, message, e.getMessage());
                return;
            }
            moveTo(player, sourceRoot, file, destRoot, dest);
        }

        private static void rename(HandleFile message, EntityPlayerMP player, File root, File file) {
            if (!PathSafety.isPlainName(message.rename)) {
                reject(player, message, "新文件名非法（不允许路径、.. 或盘符）");
                return;
            }
            File dest;
            try {
                dest = PathSafety.resolveInside(root, message.rename);
                PathSafety.assertInside(root, dest);
            } catch (IllegalArgumentException e) {
                reject(player, message, e.getMessage());
                return;
            }
            if (file.equals(dest)) {
                fail(player, "新旧文件名相同，未执行重命名");
                return;
            }
            moveTo(player, root, file, root, dest);
        }

        private static void moveTo(EntityPlayerMP player, File sourceRoot, File source, File destRoot, File dest) {
            if (dest.exists()) {
                fail(player, "目标位置已存在同名文件：" + dest.getName());
                return;
            }
            try {
                // 移动前对源和目标都做包含关系校验
                PathSafety.assertInside(sourceRoot, source);
                PathSafety.assertInside(destRoot, dest);
                if (source.isDirectory()) {
                    FileUtils.moveDirectory(source, dest);
                } else {
                    FileUtils.moveFile(source, dest);
                }
            } catch (IOException | RuntimeException e) {
                YesSteveModel.LOGGER.error("YSM: 玩家 {} 移动文件 {} -> {} 失败", player.getName(), source, dest, e);
                notifyError(player, "移动文件失败：" + detail(e));
            }
        }

        private static void reject(EntityPlayerMP player, HandleFile message, String reason) {
            YesSteveModel.LOGGER.warn("YSM: 拒绝玩家 {} 的文件操作 name={} action={}：{}",
                    player.getName(), message.name, message.action, reason);
            notifyError(player, reason);
        }

        private static void fail(EntityPlayerMP player, String reason) {
            YesSteveModel.LOGGER.warn("YSM: 玩家 {} 的文件操作失败：{}", player.getName(), reason);
            notifyError(player, reason);
        }

        private static void notifyError(EntityPlayerMP player, String reason) {
            player.sendMessage(new TextComponentString("[YSM] 操作失败：" + sanitize(reason)));
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
}
