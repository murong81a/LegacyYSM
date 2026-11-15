package com.elfmcys.yesstevemodel.command;

import net.minecraft.command.EntitySelector;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * 1.10.2 的 {@code CommandBase} 没有 1.12.2 的 {@code getPlayers}，这里补一个等价实现。
 */
public final class CommandUtil {
    private CommandUtil() {
    }

    public static List<EntityPlayerMP> getPlayers(MinecraftServer server, ICommandSender sender, String selector) {
        return EntitySelector.matchEntities(sender, selector, EntityPlayerMP.class);
    }
}
