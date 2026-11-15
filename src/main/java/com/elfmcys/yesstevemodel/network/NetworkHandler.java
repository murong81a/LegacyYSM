package com.elfmcys.yesstevemodel.network;

import com.elfmcys.yesstevemodel.bukkit.message.OpenModelGuiMessage;
import com.elfmcys.yesstevemodel.bukkit.message.SetNpcModelAndTexture;
import com.elfmcys.yesstevemodel.bukkit.message.SyncNpcDataMessage;
import com.elfmcys.yesstevemodel.bukkit.message.UpdateNpcDataMessage;
import com.elfmcys.yesstevemodel.network.message.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.function.Supplier;

public final class NetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("yesstevemodel");
    public static final int OPEN_NPC_MODEL_GUI = 93;
    public static final int SET_NPC_MODEL_ID = 94;
    public static final int SYNC_NPC_DATA = 95;
    public static final int UPDATE_NPC_DATA = 96;

    public static void init() {
        // 最后一个参数是接收端
        reg(() -> SyncModelFiles.Handler.class, SyncModelFiles.class, 0, Side.SERVER);
        reg(() -> SendModelFile.Handler.class, SendModelFile.class, 1, Side.CLIENT);
        reg(() -> RequestSyncModel.Handler.class, RequestSyncModel.class, 2, Side.CLIENT);
        reg(() -> RequestLoadModel.Handler.class, RequestLoadModel.class, 3, Side.CLIENT);
        reg(() -> SyncModelInfo.Handler.class, SyncModelInfo.class, 4, Side.CLIENT);
        reg(() -> SetModelAndTexture.Handler.class, SetModelAndTexture.class, 5, Side.SERVER);
        reg(() -> SyncAuthModels.Handler.class, SyncAuthModels.class, 6, Side.CLIENT);
        reg(() -> SetPlayAnimation.Handler.class, SetPlayAnimation.class, 7, Side.SERVER);
        reg(() -> SyncStarModels.Handler.class, SyncStarModels.class, 8, Side.CLIENT);
        reg(() -> SetStarModel.Handler.class, SetStarModel.class, 9, Side.SERVER);
        reg(() -> RequestServerModelInfo.Handler.class, RequestServerModelInfo.class, 10, Side.CLIENT);
        reg(() -> UploadFile.Handler.class, UploadFile.class, 11, Side.SERVER);
        reg(() -> CompleteFeedback.Handler.class, CompleteFeedback.class, 12, Side.CLIENT);
        reg(() -> RefreshModelManage.Handler.class, RefreshModelManage.class, 13, Side.SERVER);
        reg(() -> HandleFile.Handler.class, HandleFile.class, 14, Side.SERVER);
        reg(() -> SyncArrowModel.Handler.class, SyncArrowModel.class, 15, Side.CLIENT);
        initBukkit();
    }

    private static void initBukkit() {
        reg(() -> OpenModelGuiMessage.Handler.class, OpenModelGuiMessage.class, OPEN_NPC_MODEL_GUI, Side.CLIENT);
        reg(() -> SetNpcModelAndTexture.Handler.class, SetNpcModelAndTexture.class, SET_NPC_MODEL_ID, Side.SERVER);
        reg(() -> SyncNpcDataMessage.Handler.class, SyncNpcDataMessage.class, SYNC_NPC_DATA, Side.CLIENT);
        reg(() -> UpdateNpcDataMessage.Handler.class, UpdateNpcDataMessage.class, UPDATE_NPC_DATA, Side.CLIENT);
    }

    /**
     * 1.10.2 的 {@code SimpleNetworkWrapper#registerMessage} 会在两侧都立即 new 出 Handler，
     * 而客户端 Handler 的字节码引用了 {@code Minecraft.player}（EntityPlayerSP）。
     * 专用服务端加载这种类会被 Forge 的 SideTransformer 拒绝
     * （NoClassDefFoundError: net/minecraft/client/entity/EntityPlayerSP）。
     * <p>
     * 因此 Handler 不属于当前侧时注册无副作用的 {@link NoopHandler}：消息类仍然完成注册
     * （否则发送端查不到 discriminator），但不会加载/实例化客户端 Handler。
     * 用 {@link Supplier} 是为了让 {@code Xxx.Handler.class} 这个类常量只在匹配侧才解析。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void reg(Supplier<Class<?>> handlerSupplier, Class<?> message, int id, Side side) {
        boolean physicalClient = FMLCommonHandler.instance().getSide().isClient();
        // 客户端 JVM 里同时存在客户端和集成服务器两个逻辑侧，两侧 Handler 都应注册；
        // 只有专用服务端（physical SERVER）不能加载客户端 Handler。
        boolean canLoadHandler = physicalClient || side == Side.SERVER;
        Class<?> handler = canLoadHandler ? handlerSupplier.get() : NoopHandler.class;
        CHANNEL.registerMessage((Class) handler, (Class) message, id, side);
    }

    public static final class NoopHandler implements IMessageHandler<IMessage, IMessage> {
        @Override
        public IMessage onMessage(IMessage message, MessageContext ctx) {
            return null;
        }
    }

    public static void sendToClientPlayer(IMessage message, EntityPlayer player) {
        if (player instanceof EntityPlayerMP playerMP) {
            CHANNEL.sendTo(message, playerMP);
        }
    }

    /**
     * 等价于 1.12.2 的 {@code SimpleNetworkWrapper#sendToAllTracking}。
     * <p>
     * 1.10.2 只有 {@code sendToAllAround}，这里退化为“同一维度内所有玩家”，
     * 语义上覆盖了所有能看到该实体的玩家，只是多发给了一些远处的玩家。
     */
    public static void sendToTracking(IMessage message, Entity entity) {
        if (entity == null || entity.world == null) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player == entity) {
                continue;
            }
            if (player.world == entity.world) {
                CHANNEL.sendTo(message, player);
            }
        }
    }
}
