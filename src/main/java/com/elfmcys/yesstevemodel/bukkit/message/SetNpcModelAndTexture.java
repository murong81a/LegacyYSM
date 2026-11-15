package com.elfmcys.yesstevemodel.bukkit.message;

import com.elfmcys.yesstevemodel.network.message.IPacketBufferMessage;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import com.elfmcys.yesstevemodel.util.CompatUtil;

public class SetNpcModelAndTexture implements IPacketBufferMessage {
    private ResourceLocation modelId;
    private ResourceLocation selectTexture;
    private int npcId;

    public SetNpcModelAndTexture() {
    }

    public SetNpcModelAndTexture(ResourceLocation modelId, ResourceLocation selectTexture, int npcId) {
        this.modelId = modelId;
        this.selectTexture = selectTexture;
        this.npcId = npcId;
    }

    @Override
    public void toBytes(PacketBuffer buf) {
        CompatUtil.writeResourceLocation(buf, this.modelId);
        CompatUtil.writeResourceLocation(buf, this.selectTexture);
        buf.writeInt(this.npcId);
    }

    @Override
    public void fromBytes(PacketBuffer buf) {
        this.modelId = CompatUtil.readResourceLocation(buf);
        this.selectTexture = CompatUtil.readResourceLocation(buf);
        this.npcId = buf.readInt();
    }

    public static class Handler implements IMessageHandler<SetNpcModelAndTexture, IMessage> {
        @Override
        public IMessage onMessage(SetNpcModelAndTexture message, MessageContext ctx) {
            return null;
        }
    }
}
