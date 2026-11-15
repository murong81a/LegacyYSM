package com.elfmcys.yesstevemodel.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * 把 {@code Minecraft#toggleFullscreen()V} 的方法体清空，只留 RETURN。
 * 方法体里没有分支，因此不需要 StackMapTable，避免 COMPUTE_FRAMES 的类加载问题。
 */
public class FullscreenGuardTransformer implements IClassTransformer {

    private static final String TARGET_CLASS = "net.minecraft.client.Minecraft";
    private static final String GUARD_PROPERTY = "ysm.blockFullscreen";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (!Boolean.parseBoolean(System.getProperty(GUARD_PROPERTY, "true"))) {
            return basicClass;
        }
        if (!TARGET_CLASS.equals(name) && !TARGET_CLASS.equals(transformedName)) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            if (!"()V".equals(method.desc)) {
                continue;
            }
            // 开发环境是 MCP 名，生产环境是 SRG 名
            if (!"toggleFullscreen".equals(method.name) && !"func_71352_k".equals(method.name)) {
                continue;
            }
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.maxStack = 0;
            method.maxLocals = 1;
            changed = true;
        }

        if (!changed) {
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
