package com.elfmcys.yesstevemodel.client.util;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.ClientProxy;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.renderer.CustomPlayerRenderer;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoReplacedEntityRenderer;
import com.elfmcys.yesstevemodel.util.ModelIdUtil;
import com.google.common.collect.Lists;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import com.elfmcys.yesstevemodel.util.CompatUtil;

/*
1.16.5 - 1.12.2
player.yBodyRot - player.renderYawOffset	韬綋杞悜瑙掑害
player.yRot - player.rotationYaw	瑙嗗彛鍋忚埅瑙?
player.xRot	- player.rotationPitch	瑙嗗彛淇话瑙?
player.yHeadRot	- player.rotationYawHead	澶撮儴鍋忚埅瑙?
player.yHeadRotO - player.prevRotationYawHead	涓婁竴鍒诲ご閮ㄥ亸鑸
 */
public final class RenderUtil {
    public static void renderTextureScreenEntity(float pPosX, float pPosY, float pScale, float pitch, float yaw, EntityPlayer player, ResourceLocation modelId, ResourceLocation textureId, boolean showGround, Consumer<CustomPlayerEntity> consumer) {
        if (player == null) {
            return;
        }
        try {
            CustomPlayerRenderer renderer = ClientProxy.getInstance();
            AnimatableEntity<?> animatable = AnimatableCacheUtil.TEXTURE_GUI_CACHE.get(modelId, () -> new CustomPlayerEntity(null));
            if (animatable instanceof CustomPlayerEntity entity) {
                consumer.accept(entity);

                entity.setModelLocation(ModelIdUtil.getMainId(modelId));
                entity.setTextureLocation(textureId);

                GlStateManager.pushMatrix();
                GlStateManager.translate(pPosX, pPosY, 1050.0F);
                GlStateManager.scale(1.0F, 1.0F, -1.0F);

                /*
                鍔ㄧ敾浣嶇Щ鐭╅樀寮€濮?
                 */
                GlStateManager.pushMatrix();
                GlStateManager.translate(0.0D, 0.0D, 1000.0D);
                GlStateManager.scale(pScale, pScale, pScale);
                GlStateManager.translate(0, 0.8, 0);
                GlStateManager.rotate(180.0F, 0, 0, 1);
                rotateAndEnableLighting();
                float xp = -10 + pitch;
                GlStateManager.rotate(xp, 1, 0, 0);

                float yBodyRot = player.renderYawOffset;
                float yBodyRotO = player.prevRenderYawOffset;
                float yRot = player.rotationYaw;
                float yRotO = player.prevRotationYaw;
                float xRot = player.rotationPitch;
                float xRotO = player.prevRotationPitch;
                float yHeadRot = player.rotationYawHead;
                float yHeadRotO = player.prevRotationYawHead;
                Entity vehicle = player.ridingEntity;

                boolean sleeping = player.sleeping;
                BlockPos bedLocation = player.bedLocation;
                float renderOffsetX = player.renderOffsetX;
                float renderOffsetY = player.renderOffsetY;
                float renderOffsetZ = player.renderOffsetZ;

                player.renderYawOffset = -yaw;
                player.prevRenderYawOffset = player.renderYawOffset;
                player.rotationYaw = 180;
                player.prevRotationYaw = player.rotationYaw;
                player.rotationPitch = 0;
                player.prevRotationPitch = player.rotationPitch;
                player.rotationYawHead = player.renderYawOffset;
                player.prevRotationYawHead = player.rotationYawHead;
                player.ridingEntity = null;

                RenderManager dispatcher = Minecraft.getMinecraft().getRenderManager();
                xp = 180.0F - xp;
                dispatcher.setPlayerViewY(xp);
                dispatcher.setRenderShadow(false);
                if (entity.hasPreviewAnimation("sleep")) {
                    GlStateManager.rotate(yaw - 90, 0, 1, 0);
                    GlStateManager.translate(0.5, 0.5625, 0);
                    player.sleeping = true;
                    // 鍘熺増鐨勫簥鐫″畬鍚庝笉浼氶噸缃繖鍑犱釜鐘舵€?
                    player.bedLocation = null;
                    player.renderOffsetX = 0;
                    player.renderOffsetY = 0;
                    player.renderOffsetZ = 0;
                }
                if (entity.hasPreviewAnimation("swim") || entity.hasPreviewAnimation("swim_stand")) {
                    //player.setPose(Pose.SWIMMING);
                }
                if (entity.hasPreviewAnimation("sneak") || entity.hasPreviewAnimation("sneaking")) {
                    //player.setPose(Pose.CROUCHING);
                }
                if (entity.hasPreviewAnimation("sit")) {
                    GlStateManager.translate(0, -0.5, 0);
                }
                if (entity.hasPreviewAnimation("ride")) {
                    GlStateManager.translate(0, 0.85, 0);
                }
                if (entity.hasPreviewAnimation("ride_pig")) {
                    GlStateManager.translate(0, 0.3125, 0);
                }
                if (entity.hasPreviewAnimation("boat")) {
                    GlStateManager.translate(0, -0.45, 0);
                }
                renderer.render(player, entity, 0, 0, 0, 0.0F, 1.0F);
                // 娓呯悊瀹炰綋娓叉煋
                GlStateManager.enableRescaleNormal();
                GlStateManager.enableColorMaterial();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
                try {
                    renderExtraEntity(yaw, player, entity, dispatcher);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
                GlStateManager.popMatrix();
                /*
                鍔ㄧ敾浣嶇Щ鐭╅樀缁撴潫
                 */
                if (showGround) {
                    if (entity.hasPreviewAnimation("sleep")) {
                        renderBed(pScale, pitch, yaw);
                    }
                    renderGround(pScale, pitch, yaw);
                }
                dispatcher.setRenderShadow(true);

                player.renderYawOffset = yBodyRot;
                player.prevRenderYawOffset = yBodyRotO;
                player.rotationYaw = yRot;
                player.prevRotationYaw = yRotO;
                player.rotationPitch = xRot;
                player.prevRotationPitch = xRotO;
                player.rotationYawHead = yHeadRot;
                player.prevRotationYawHead = yHeadRotO;
                player.ridingEntity = vehicle;

                player.sleeping = sleeping;
                player.bedLocation = bedLocation;
                player.renderOffsetX = renderOffsetX;
                player.renderOffsetY = renderOffsetY;
                player.renderOffsetZ = renderOffsetZ;

                GlStateManager.popMatrix();

                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableRescaleNormal();
                GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
                GlStateManager.disableTexture2D();
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            }
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private static void renderBed(float scale, float pitch, float yaw) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0D, 0.0D, 1000.0D);
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(0, 0.8, 0);
        GlStateManager.rotate(180.0F, 0, 0, 1);
        GlStateManager.rotate(-10 + pitch, 1, 0, 0);

        GlStateManager.rotate(yaw + 180, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, 0.5);
        ItemStack stack = new ItemStack(Items.BED, 1, EnumDyeColor.RED.getMetadata());
        Minecraft.getMinecraft().getRenderItem().renderItem(stack, net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIXED);
        GlStateManager.popMatrix();
    }

    private static void renderGround(float scale, float pitch, float yaw) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0D, 0.0D, 1000.0D);
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(0, 0.8, 0);
        GlStateManager.rotate(180.0F, 0, 0, 1);
        GlStateManager.rotate(-10 + pitch, 1, 0, 0);

        GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.translate(-1.5, -1, -1.5);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                GlStateManager.translate(0, 0, 1);
                renderSingleBlock(Blocks.GRASS.getDefaultState());
            }
            GlStateManager.translate(1, 0, -3);
        }
        GlStateManager.translate(-1, 1, 1);
        renderSingleBlock(Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS));
        GlStateManager.translate(0, 0, 1);
        renderSingleBlock(Blocks.RED_FLOWER.getDefaultState().withProperty(Blocks.RED_FLOWER.getTypeProperty(), BlockFlower.EnumFlowerType.RED_TULIP));
        GlStateManager.popMatrix();
    }

    private static void renderSingleBlock(IBlockState state) {
        GlStateManager.pushMatrix(); // 涓€瀹氳 push锛屽師鐗堟柟鍧楁覆鏌撲笉骞插噣
        final Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        mc.getBlockRendererDispatcher().renderBlockBrightness(state, 1.0F);
        GlStateManager.popMatrix();
    }

    @SuppressWarnings({"DataFlowIssue", "UnnecessaryReturnStatement"})
    private static void renderExtraEntity(float yaw, EntityPlayer player, CustomPlayerEntity playerEntity, RenderManager dispatcher) throws ExecutionException {
        if (playerEntity.hasPreviewAnimation("ride")) {
            Entity entity = AnimatableCacheUtil.ENTITIES_CACHE.get(CompatUtil.getEntityId(EntityHorse.class), () -> new EntityHorse(player.world));
            renderExtraEntity(yaw, player, dispatcher, entity);
            return;
        }
        if (playerEntity.hasPreviewAnimation("ride_pig")) {
            Entity entity = AnimatableCacheUtil.ENTITIES_CACHE.get(CompatUtil.getEntityId(EntityPig.class), () -> new EntityPig(player.world));
            renderExtraEntity(yaw, player, dispatcher, entity);
            return;
        }
        if (playerEntity.hasPreviewAnimation("boat")) {
            Entity entity = AnimatableCacheUtil.ENTITIES_CACHE.get(CompatUtil.getEntityId(EntityBoat.class), () -> new EntityBoat(player.world));
            renderExtraEntity(yaw, player, dispatcher, entity);
            return;
        }
    }

    private static void renderExtraEntity(float yaw, EntityPlayer player, RenderManager dispatcher, Entity entity) {
        GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        dispatcher.doRenderEntity(entity, 0, -entity.getMountedYOffset() - player.getYOffset(), 0, 0.0F, 1.0F, false);
        // 娓呯悊瀹炰綋娓叉煋
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableColorMaterial();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
    }

    public static void renderEntityInInventory(int pPosX, int pPosY, int pScale, EntityPlayer player, ResourceLocation modelId, ResourceLocation textureId, Consumer<CustomPlayerEntity> consumer, boolean disableRot) {
        if (player == null) {
            return;
        }
        try {
            CustomPlayerRenderer renderer = ClientProxy.getInstance();
            AnimatableEntity<?> animatable = AnimatableCacheUtil.GUI_CACHE.get(modelId, () -> new CustomPlayerEntity(null));
            if (animatable instanceof CustomPlayerEntity entity) {
                consumer.accept(entity);
                renderModel(pPosX, pPosY, (float) pScale, player, modelId, textureId, renderer, entity, disableRot);
            }
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@link com.elfmcys.yesstevemodel.client.gui.button.TextureButton#renderWidget(Minecraft, int, int, float)}
     */
    @SuppressWarnings("JavadocReference")
    public static void renderTextureButtonEntity(int pPosX, int pPosY, int pScale, EntityPlayer player, ResourceLocation modelId, ResourceLocation textureId) {
        renderEntityInInventory(pPosX, pPosY, pScale, player, modelId, textureId, entity -> {
            if (entity.hasPreviewAnimation()) {
                entity.clearPreviewAnimation();
            }
        }, false);
    }

    private static void renderModel(
            double pPosX, double pPosY, float pScale, EntityPlayer player,
            ResourceLocation modelId, ResourceLocation textureId,
            GeoReplacedEntityRenderer<EntityPlayer, CustomPlayerEntity> renderer, CustomPlayerEntity entity,
            boolean disableRot
    ) {
        entity.setModelLocation(ModelIdUtil.getMainId(modelId));
        entity.setTextureLocation(textureId);

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) pPosX, (float) pPosY, 1050.0F);
        GlStateManager.scale(1.0F, 1.0F, -1.0F);

        GlStateManager.translate(0.0D, disableRot ? 5.5D : 0.0D, 1000.0D);
        GlStateManager.scale(pScale, pScale, pScale);
        GlStateManager.rotate(180.0F, 0, 0, 1);
        rotateAndEnableLighting();
        float xp = disableRot ? 0.0F : -10.0F;
        GlStateManager.rotate(xp, 1, 0, 0);

        float yBodyRot = player.renderYawOffset;
        float yBodyRotO = player.prevRenderYawOffset;
        float yRot = player.rotationYaw;
        float yRotO = player.prevRotationYaw;
        float xRot = player.rotationPitch;
        float xRotO = player.prevRotationPitch;
        float yHeadRot = player.rotationYawHead;
        float yHeadRotO = player.prevRotationYawHead;
        Entity vehicle = player.ridingEntity;

        ItemStack[] itemStacks = new ItemStack[EntityEquipmentSlot.values().length];
        int i = 0;
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            itemStacks[i] = player.getItemStackFromSlot(slot);
            if (slot == EntityEquipmentSlot.MAINHAND) {
                player.inventory.mainInventory[player.inventory.currentItem] = null;
            } else if (slot == EntityEquipmentSlot.OFFHAND) {
                player.inventory.offHandInventory[0] = null;
            } else {
                player.inventory.armorInventory[slot.getIndex()] = null;
            }
            i++;
        }

        float renderYRot = disableRot ? 180.0F : 200.0F;
        player.renderYawOffset = renderYRot;
        player.prevRenderYawOffset = player.renderYawOffset;
        player.rotationYaw = renderYRot;
        player.prevRotationYaw = player.rotationYaw;
        player.rotationPitch = 0.0F;
        player.prevRotationPitch = player.rotationPitch;
        player.rotationYawHead = player.renderYawOffset;
        player.prevRotationYawHead = player.rotationYawHead;
        player.ridingEntity = null;

        RenderManager dispatcher = Minecraft.getMinecraft().getRenderManager();
        xp = 180.0F - xp;
        dispatcher.setPlayerViewY(xp);
        dispatcher.setRenderShadow(false);
        renderer.render(player, entity, 0, 0, 0, 0.0F, 1.0F);
        dispatcher.setRenderShadow(true);

        player.renderYawOffset = yBodyRot;
        player.prevRenderYawOffset = yBodyRotO;
        player.rotationYaw = yRot;
        player.prevRotationYaw = yRotO;
        player.rotationPitch = xRot;
        player.prevRotationPitch = xRotO;
        player.rotationYawHead = yHeadRot;
        player.prevRotationYawHead = yHeadRotO;
        player.ridingEntity = vehicle;

        i = 0;
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            ItemStack itemStack = itemStacks[i];
            if (slot == EntityEquipmentSlot.MAINHAND) {
                player.inventory.mainInventory[player.inventory.currentItem] = itemStack;
            } else if (slot == EntityEquipmentSlot.OFFHAND) {
                player.inventory.offHandInventory[0] = itemStack;
            } else {
                player.inventory.armorInventory[slot.getIndex()] = itemStack;
            }
            i++;
        }
        GlStateManager.popMatrix();
    }

    public static void renderPlayerEntity(EntityPlayer player, double posX, double posY, float scale, float yawOffset, int z) {
        GlStateManager.enableColorMaterial();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) posX + scale * 0.5f, (float) posY + scale * 2, z);
        GlStateManager.scale(1, 1, -1);
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.rotate(180.0F, 0, 0, 1);
        rotateAndEnableLighting();
        float yRot = player.renderYawOffset + yawOffset - 180;
        GlStateManager.rotate(yRot, 0, 1, 0);
        RenderManager renderDispatcher = Minecraft.getMinecraft().getRenderManager();
        yRot = 180.0F - yRot;
        renderDispatcher.setPlayerViewY(yRot);
        renderDispatcher.setRenderShadow(false);
        renderDispatcher.doRenderEntity(player, 0, 0, 0, 0.0F, 1.0F, false); // 鏈€鍚庤繖涓弬鏁版槸闅愯棌纰版挒绠?
        renderDispatcher.setRenderShadow(true);
        GlStateManager.popMatrix();

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void rotateAndEnableLighting() {
        GlStateManager.rotate(135.0F, 0, 1, 0);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135.0F, 0, 1, 0);
    }

    /**
     * 浣跨敤 GUI 鍍忕礌鍧愭爣鐨勮鍓柟娉曘€?
     */
    public static void scissor(int screenX, int screenY, int boxWidth, int boxHeight) {
        final Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                screenX * scale,
                mc.displayHeight - (screenY * scale + boxHeight * scale),
                Math.max(0, boxWidth * scale),
                Math.max(0, boxHeight * scale)
        );
    }

    /**
     * 鎶婃枃瀛楁埅涓哄琛岋紝骞堕€愯鐢诲嚭銆?
     *
     * @return 鏈€鍚庝竴琛岀殑鐩稿 Y 鍧愭爣銆?
     */
    @SuppressWarnings("UnusedReturnValue")
    public static int drawWordWrap(@Nonnull FontRenderer font, @Nullable String text, int x, int y, int wrapWidth, int color) {
        int currentY = 0;
        if (text == null) return currentY;
        for (String line : listLineBreakStringToWidth(font, text, wrapWidth)) {
            if (line.isEmpty()) {
                currentY += font.FONT_HEIGHT;
                continue;
            }
            font.drawStringWithShadow(line, x, y + currentY, color);
            currentY += font.FONT_HEIGHT;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return currentY;
    }

    /**
     * 鎶婂彲鑳藉寘鍚?{@code \n} 鎴栬€?{@code \\n} 鐨勬枃瀛楁埅涓哄琛屻€?br>
     * {@link FontRenderer#listFormattedStringToWidth(String, int)} 鐨勪紭鍖栫増锛屽畠涓嶅彲浠ュ鐞嗘崲琛屻€?
     */
    public static List<String> listLineBreakStringToWidth(@Nonnull FontRenderer font, @Nullable String text, int wrapWidth) {
        final List<String> lineList = Lists.newArrayList();
        if (text == null) return lineList;
        text = text.replace("\\n", "\n");
        String[] paragraphs = text.split("\n", -1);
        for (String para : paragraphs) {
            if (para.isEmpty()) {
                lineList.add(StringUtils.EMPTY);
                continue;
            }
            lineList.addAll(font.listFormattedStringToWidth(para, wrapWidth));
        }
        return lineList;
    }

    /**
     * 娓叉煋瀹?GUI 閲岀殑妯″瀷鍚庡浣嶅浐瀹氱绾跨姸鎬併€?
     * <p>
     * 鍏夊奖锛圤ptiFine锛変笅妯″瀷娓叉煋鍙兘鐣欎笅鍏夌収/棰滆壊鏉愯川/娣峰悎鐘舵€侊紝瀵艰嚧鍚庨潰鐨?GUI
     * 鎺т欢锛堟寜閽€佹枃瀛楋級琚煋鑹叉垨杩囦寒銆傝繖閲岀粺涓€澶嶄綅锛屽苟淇濇寔娣卞害娴嬭瘯鍏抽棴锛圙UI 鎯緥锛夈€?
     * <p>
     * 鍔?{@code -Dysm.debugGl=true} 鍚姩鏃朵細鍦ㄩ娆¤皟鐢ㄦ墦鍗颁竴娆?GL 鐘舵€侊紝渚夸簬鎺掓煡
     * 鍏夊奖鐜涓?GUI 鍙戠豢/琚鍓殑闂銆?
     */
    public static void resetGuiRenderState() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        if (!DEBUG_GL_LOGGED && Boolean.getBoolean("ysm.debugGl")) {
            DEBUG_GL_LOGGED = true;
            // 0x8B8D = GL_CURRENT_PROGRAM, 0x8CA6 = GL_FRAMEBUFFER_BINDING(GL_FRAMEBUFFER)
            YesSteveModel.LOGGER.info(
                    "[ysm] GUI GL state: depthTest={} lighting={} colorMaterial={} blend={} program={} fbo={}",
                    GL11.glGetBoolean(GL11.GL_DEPTH_TEST),
                    GL11.glGetBoolean(GL11.GL_LIGHTING),
                    GL11.glGetBoolean(GL11.GL_COLOR_MATERIAL),
                    GL11.glGetBoolean(GL11.GL_BLEND),
                    GL11.glGetInteger(0x8B8D),
                    GL11.glGetInteger(0x8CA6));
        }
    }

    private static boolean DEBUG_GL_LOGGED = false;
}

