package com.elfmcys.yesstevemodel.capability;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.config.ServerConfig;
import com.elfmcys.yesstevemodel.util.ModelIdUtil;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;

public class ModelInfoCapability {
    private ResourceLocation modelId = new ResourceLocation(YesSteveModel.MOD_ID, ServerConfig.DEFAULT_MODEL_ID);
    private ResourceLocation selectTexture = new ResourceLocation(YesSteveModel.MOD_ID, ServerConfig.DEFAULT_MODEL_ID + "/" + ServerConfig.DEFAULT_MODEL_TEXTURE);
    private String animation = "idle";
    private boolean playAnimation = false;
    private boolean dirty;

    public void setModelAndTexture(ResourceLocation modelId, ResourceLocation selectTexture) {
        this.modelId = modelId;
        this.selectTexture = normalizeTextureId(modelId, selectTexture);
        this.markDirty();
    }

    /**
     * 兼容历史存档/手输命令产生的非标准贴图 id，统一成 {@code <model>/<file>.png} 形式。
     * <p>
     * 例如 {@code wine_fox/skin} -> {@code wine_fox/skin.png}、
     * {@code wine_fox/wine_fox/skin} -> {@code wine_fox/skin.png}。
     * 无法确认存在时保持归一化结果，渲染侧还会用 {@code ClientModelManager.resolveTexture} 再兜底一次。
     */
    private static ResourceLocation normalizeTextureId(ResourceLocation modelId, @Nullable ResourceLocation textureId) {
        if (modelId == null || textureId == null) {
            return new ResourceLocation(YesSteveModel.MOD_ID, ServerConfig.DEFAULT_MODEL_ID + "/" + ServerConfig.DEFAULT_MODEL_TEXTURE);
        }
        String path = textureId.getResourcePath();
        String prefix = modelId.getResourcePath() + "/";
        while (path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return ModelIdUtil.getSubModelId(modelId, path);
    }

    public void copyFrom(ModelInfoCapability source) {
        this.modelId = source.modelId;
        this.selectTexture = source.selectTexture;
        this.animation = source.animation;
        this.playAnimation = source.playAnimation;
        this.markDirty();
    }

    public ResourceLocation getModelId() {
        return this.modelId;
    }

    public ResourceLocation getSelectTexture() {
        return this.selectTexture;
    }

    public void setSelectTexture(ResourceLocation selectTexture) {
        this.selectTexture = normalizeTextureId(this.modelId, selectTexture);
        this.markDirty();
    }

    public void playAnimation(String animation) {
        this.animation = animation;
        this.playAnimation = true;
        this.markDirty();
    }

    public void stopAnimation() {
        this.playAnimation = false;
        this.markDirty();
    }

    public String getAnimation() {
        return this.animation;
    }

    public boolean isPlayAnimation() {
        return this.playAnimation;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("model_id", this.modelId.toString());
        tag.setString("select_texture", this.selectTexture.toString());
        tag.setString("animation", this.animation);
        tag.setBoolean("play_animation", this.playAnimation);
        return tag;
    }

    public void deserializeNBT(NBTTagCompound nbt) {
        this.modelId = new ResourceLocation(nbt.getString("model_id"));
        this.selectTexture = normalizeTextureId(this.modelId, new ResourceLocation(nbt.getString("select_texture")));
        this.animation = nbt.getString("animation");
        this.playAnimation = nbt.getBoolean("play_animation");
    }

    public static class Storage implements Capability.IStorage<ModelInfoCapability> {
        @Nullable
        @Override
        public NBTBase writeNBT(Capability<ModelInfoCapability> capability, ModelInfoCapability instance, EnumFacing side) {
            return instance.serializeNBT();
        }

        @Override
        public void readNBT(Capability<ModelInfoCapability> capability, ModelInfoCapability instance, EnumFacing side, NBTBase nbt) {
            instance.deserializeNBT((NBTTagCompound) nbt);
        }
    }
}
