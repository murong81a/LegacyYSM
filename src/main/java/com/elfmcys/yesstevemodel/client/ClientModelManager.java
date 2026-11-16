package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionManager;
import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;
import com.elfmcys.yesstevemodel.data.ModelData;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.MolangParser;
import com.elfmcys.yesstevemodel.geckolib3.file.AnimationFile;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.ExtraInfo;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.FormatVersion;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.RawGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.tree.RawGeometryTree;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.GeoBuilder;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.geckolib3.resource.GeckoLibCache;
import com.elfmcys.yesstevemodel.geckolib3.util.json.JsonAnimationUtils;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.model.format.FormatManager;
import com.elfmcys.yesstevemodel.model.format.Type;
import com.elfmcys.yesstevemodel.model.format.access.FolderModelAccess;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.SyncModelFiles;
import com.elfmcys.yesstevemodel.util.ModelIdUtil;
import com.elfmcys.yesstevemodel.util.ObjectStreamUtil;
import com.elfmcys.yesstevemodel.util.ThreadTools;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.JsonException;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientModelManager {
    public static Map<ResourceLocation, List<ResourceLocation>> MODELS = Maps.newHashMap();
    public static Map<ResourceLocation, Pair<Double, Double>> SCALE_INFO = Maps.newHashMap();
    public static Map<ResourceLocation, List<String>> METADATA = Maps.newHashMap();
    public static Map<ResourceLocation, ExtraInfo> EXTRA_INFO = Maps.newHashMap();
    public static AnimationFile DEFAULT_ANIMATION_FILE = new AnimationFile();
    public static List<String> CACHE_MD5 = Lists.newArrayList();
    public static List<String> AUTH_MODELS = Lists.newArrayList();
    /**
     * 服务器下发的模型包密码。
     * <p>
     * 由网络包处理（主线程）写入、由 {@link ThreadTools} 线程池读取，必须 volatile 才能保证可见性，
     * 否则工作线程可能永远读到 null（表现为一直等密码）。
     */
    public static volatile byte[] PASSWORD;

    /**
     * 已打印过的 warn 去重键（modelId + 阶段）。
     * 同一个模型每次同步都会重新注册，不去重会把日志刷爆。
     */
    private static final Set<String> WARNED_KEYS = ConcurrentHashMap.newKeySet();
    /** 已打印过 info 汇总的模型，保证每个模型只打一条汇总。 */
    private static final Set<String> SUMMARY_KEYS = ConcurrentHashMap.newKeySet();
    /** 本次同步中发生过部分失败的模型，供 registerAll 末尾汇总判断。 */
    private static final Set<ResourceLocation> PARTIAL_FAILED = ConcurrentHashMap.newKeySet();

    public static void registerAll(ModelData data) {
        ResourceLocation modelId = new ResourceLocation(YesSteveModel.MOD_ID, data.getModelId());
        if (data.isAuth()) {
            AUTH_MODELS.add(data.getModelId());
        }
        int geoCount = 0;
        int animationCount = 0;
        int textureCount = 0;
        boolean stepFailed = false;

        // geo -> 动画 -> 贴图：三步互相隔离，任一步抛异常都不影响后续步骤
        try {
            geoCount = ClientModelManager.registerGeo(modelId, data.getModel());
        } catch (Throwable t) {
            stepFailed = true;
            PARTIAL_FAILED.add(modelId);
            YesSteveModel.LOGGER.error("YSM: 注册模型 {} 的 geo 失败（geo 部件 {} 个，贴图 {} 张，动画 {} 个）",
                    modelId, sizeOf(data.getModel()), sizeOf(data.getTexture()), sizeOf(data.getAnimation()), t);
        }

        // registerAnimations 内部会 remove("arrow")，必须传可变拷贝；
        // 否则遇到不可变 Map 会抛 UnsupportedOperationException，导致贴图步骤也被跳过。
        try {
            animationCount = ClientModelManager.registerAnimations(ModelIdUtil.getMainId(modelId),
                    Maps.newHashMap(data.getAnimation()));
        } catch (Throwable t) {
            stepFailed = true;
            PARTIAL_FAILED.add(modelId);
            YesSteveModel.LOGGER.error("YSM: 注册模型 {} 的动画失败（动画文件 {} 个）",
                    modelId, sizeOf(data.getAnimation()), t);
        }

        try {
            textureCount = ClientModelManager.registerTexture(modelId, data.getTexture());
        } catch (Throwable t) {
            stepFailed = true;
            PARTIAL_FAILED.add(modelId);
            YesSteveModel.LOGGER.error("YSM: 注册模型 {} 的贴图失败（贴图 {} 张）",
                    modelId, sizeOf(data.getTexture()), t);
        }

        boolean partial = stepFailed || PARTIAL_FAILED.remove(modelId);
        // 每个模型只打一条汇总，避免重复同步时刷屏
        if (SUMMARY_KEYS.add(modelId.toString())) {
            YesSteveModel.LOGGER.info("YSM: 注册模型 {} 汇总：geo {} 个，贴图 {} 张，动画 {} 条，auth={}{}",
                    modelId, geoCount, textureCount, animationCount, data.isAuth(),
                    partial ? "，部分步骤失败（详见上方 YSM: error/warn）" : "");
        }
    }

    /**
     * 注册模型的 geo 部件，并处理 info.json。
     *
     * @return 成功注册的 geo 部件数量
     */
    public static int registerGeo(ResourceLocation modelId, Map<String, byte[]> modelData) {
        int count = 0;
        for (Map.Entry<String, byte[]> entry : modelData.entrySet()) {
            String partName = entry.getKey();
            if (FormatManager.INFO_NAME.equals(partName)) continue;
            if (registerGeo(modelId, partName, entry.getValue())) {
                count++;
            }
        }
        byte[] infoData = modelData.get(FormatManager.INFO_NAME);
        if (infoData != null) {
            Object infoObj = ObjectStreamUtil.toObject(infoData);
            if (infoObj instanceof ExtraInfo) {
                addExtraInfo(modelId, (ExtraInfo) infoObj);
            } else {
                PARTIAL_FAILED.add(modelId);
                warnOnce(modelId + ":info", "YSM: 模型 {} 的 info 反序列化失败（{} 字节，实际类型 {}），将缺少额外信息",
                        modelId, infoData.length, typeOf(infoObj));
            }
        }
        return count;
    }

    /**
     * @return 该 geo 部件是否注册成功
     */
    private static boolean registerGeo(ResourceLocation modelId, String partName, byte[] partData) {
        Map<ResourceLocation, GeoModel> geoModels = GeckoLibCache.getInstance().getGeoModels();
        try {
            Object obj = ObjectStreamUtil.toObject(partData);
            if (!(obj instanceof RawGeoModel)) {
                PARTIAL_FAILED.add(modelId);
                warnOnce(modelId + ":geo:" + partName + ":type",
                        "YSM: 模型 {} 的 geo 部件 {} 反序列化失败（{} 字节，实际类型 {}），已跳过",
                        modelId, partName, partData == null ? -1 : partData.length, typeOf(obj));
                return false;
            }
            RawGeoModel rawModel = (RawGeoModel) obj;
            if (rawModel.getFormatVersion() != FormatVersion.NEW) {
                PARTIAL_FAILED.add(modelId);
                warnOnce(modelId + ":geo:" + partName + ":version",
                        "YSM: 模型 {} 的 geo 部件 {} 格式版本不是 NEW（实际 {}），已跳过",
                        modelId, partName, rawModel.getFormatVersion());
                return false;
            }
            RawGeometryTree rawGeometryTree = RawGeometryTree.parseHierarchy(rawModel);
            ResourceLocation partId = ModelIdUtil.getSubModelId(modelId, partName);
            GeoModel geoModel = GeoBuilder.getGeoBuilder().constructGeoModel(rawGeometryTree);
            if (FormatManager.MAIN_MODEL_NAME.equals(partName)) {
                SCALE_INFO.put(partId, Pair.of(rawGeometryTree.properties.getHeightScale(), rawGeometryTree.properties.getWidthScale()));
                addExtraInfo(modelId, rawGeometryTree.properties.getExtraInfo());
            }
            geoModels.put(partId, geoModel);
            return true;
        } catch (Throwable t) {
            PARTIAL_FAILED.add(modelId);
            YesSteveModel.LOGGER.error("YSM: 注册模型 {} 的 geo 部件 {} 失败（{} 字节）",
                    modelId, partName, partData == null ? -1 : partData.length, t);
            return false;
        }
    }

    /**
     * 注册模型的贴图。
     *
     * @return 成功注册的贴图数量
     */
    public static int registerTexture(ResourceLocation modelId, Map<String, byte[]> mapData) {
        List<ResourceLocation> textures = Lists.newArrayList();
        for (String name : mapData.keySet()) {
            if (isModelTexture(ModelIdUtil.getInfoId(modelId), name)) {
                ResourceLocation textureId = ModelIdUtil.getSubModelId(modelId, name);
                textures.add(textureId);
            }
        }
        MODELS.put(modelId, textures);
        int count = 0;
        for (Map.Entry<String, byte[]> entry : mapData.entrySet()) {
            ResourceLocation textureId = ModelIdUtil.getSubModelId(modelId, entry.getKey());
            if (registerTexture(modelId, textureId, entry.getValue())) {
                count++;
            }
        }
        return count;
    }

    /**
     * @return 该贴图是否注册成功
     */
    private static boolean registerTexture(ResourceLocation modelId, ResourceLocation textureId, byte[] data) {
        if (data == null || data.length == 0) {
            PARTIAL_FAILED.add(modelId);
            warnOnce(modelId + ":tex:" + textureId,
                    "YSM: 模型 {} 的贴图 {} 数据为空（{} 字节），该贴图将缺失",
                    modelId, textureId, data == null ? -1 : data.length);
            return false;
        }
        // 确保主线程上传
        final Minecraft mc = Minecraft.getMinecraft();
        // 已经在客户端主线程时直接上传，避免"先 bindTexture 未命中 -> 渲染成缺失贴图"的一帧闪烁
        if ("Client thread".equals(Thread.currentThread().getName())) {
            mc.getTextureManager().loadTexture(textureId, new OuterFileTexture(data));
            return true;
        }
        mc.addScheduledTask(() -> {
            mc.getTextureManager().loadTexture(textureId, new OuterFileTexture(data));
        });
        return true;
    }

    private static boolean isModelTexture(ResourceLocation infoId, String name) {
        // 兼容 V3 解包出来的 "textures/arrow.png" / "avatar/xxx.png" 这类带目录的路径：
        // 只按文件名判断，避免 arrow / specular / 头像被注册成可选皮肤。
        String fileName = name.toLowerCase(java.util.Locale.ROOT);
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            String dir = fileName.substring(0, slash + 1).toLowerCase(java.util.Locale.ROOT);
            if (dir.contains("avatar/")) return false;
            fileName = fileName.substring(slash + 1);
        }
        if (fileName.equals(FormatManager.ARROW_TEXTURE_FILE_NAME)) return false;
        if (fileName.endsWith("_specular.png")) return false;
        if (fileName.equals("tacz_use.png")) return false;
        final ExtraInfo extraInfo = EXTRA_INFO.get(infoId);
        if (extraInfo == null) return true;
        return !fileName.equals(baseName(extraInfo.getGuiBackground()))
                && !fileName.equals(baseName(extraInfo.getGuiForeground()));
    }

    private static String baseName(String path) {
        if (path == null) return "";
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        int slash = lower.lastIndexOf('/');
        return slash >= 0 ? lower.substring(slash + 1) : lower;
    }

    /**
     * 把能力里保存的贴图 id 归一化成当前模型真正注册过的 id。
     * <p>
     * 命令手输（/ysm model set ... skin）、旧存档 NBT、模型尚未同步完成等情况下，
     * select_texture 可能是一个从未注册到 TextureManager 的 id，此时 bindTexture 会回落到
     * 原版缺失贴图（紫黑格）。这里统一兜底成该模型的第一张已注册贴图。
     *
     * @return 归一化后的贴图 id；模型还没注册时原样返回，交由调用方处理
     */
    @Nullable
    public static ResourceLocation resolveTexture(ResourceLocation modelId, @Nullable ResourceLocation textureId) {
        List<ResourceLocation> textures = MODELS.get(modelId);
        if (textures == null || textures.isEmpty()) {
            return textureId;
        }
        if (textureId != null && textures.contains(textureId)) {
            return textureId;
        }
        return textures.get(0);
    }

    /**
     * @return 注册到主模型上的动画数量
     */
    private static int registerAnimations(ResourceLocation mainId, Map<String, byte[]> mapData) {
        Map<ResourceLocation, AnimationFile> animations = GeckoLibCache.getInstance().getAnimations();

        if (mapData.containsKey("arrow")) {
            byte[] arrowBytes = mapData.get("arrow");
            AnimationFile arrowsAnimationFile = getAnimationFile(new String(arrowBytes, StandardCharsets.UTF_8));
            animations.put(ModelIdUtil.getArrowId(ModelIdUtil.getModelIdFromMainId(mainId)), arrowsAnimationFile);
            mapData.remove("arrow");
        }

        AnimationFile main = new AnimationFile();
        mapData.forEach((name, bytes) -> {
            AnimationFile other = getAnimationFile(new String(bytes, StandardCharsets.UTF_8));
            mergeAnimationFile(main, other);
        });
        // 从默认补全缺失的动画
        DEFAULT_ANIMATION_FILE.animations().forEach((name, action) -> {
            if (!main.animations().containsKey(name)) {
                main.putAnimation(name, action);
            }
        });
        main.animations().forEach((name, animation) -> ConditionManager.addTest(mainId, name));
        animations.put(mainId, main);
        return main.animations().size();
    }

    private static AnimationFile getAnimationFile(String file) {
        AnimationFile animationFile = new AnimationFile();
        MolangParser parser = GeckoLibCache.getInstance().parser;
        JsonObject jsonObject = JsonUtils.gsonDeserialize(YesSteveModel.GSON, file, JsonObject.class, false);
        if (jsonObject != null) {
            for (Map.Entry<String, JsonElement> entry : JsonAnimationUtils.getAnimations(jsonObject)) {
                String animationName = entry.getKey();
                Animation animation;
                try {
                    animation = JsonAnimationUtils.deserializeJsonToAnimation(JsonAnimationUtils.getAnimation(jsonObject, animationName), parser);
                    animationFile.putAnimation(animationName, animation);
                } catch (JsonException e) {
                    YesSteveModel.LOGGER.error("YSM: 解析动画 JSON 失败（动画名 {}，{} 字符）",
                            animationName, file.length(), e);
                }
            }
        }
        return animationFile;
    }

    /**
     * @return main animation
     */
    private static AnimationFile mergeAnimationFile(AnimationFile main, AnimationFile other) {
        other.animations().forEach(main::putAnimation);
        return main;
    }

    public static void loadDefaultModel() {
        try {
            Path defaultPath = ServerModelManager.BUILTIN.resolve("default");
            try (FolderModelAccess access = new FolderModelAccess(defaultPath)) {
                ModelData data = ServerModelManager.getModelData(access, "default", false, Type.FOLDER);
                if (data == null) {
                    YesSteveModel.LOGGER.warn("YSM: 加载默认模型失败（模型数据不完整），将缺少默认动画与贴图");
                    return;
                }
                data.getAnimation().forEach((name, bytes) -> {
                    AnimationFile animationFile = getAnimationFile(new String(bytes, StandardCharsets.UTF_8));
                    if (!"arrow".equals(name)) mergeAnimationFile(DEFAULT_ANIMATION_FILE, animationFile);
                });
                ClientModelManager.registerAll(data);
            }
        } catch (IOException e) {
            YesSteveModel.LOGGER.error("YSM: 加载默认模型失败（{}）", ServerModelManager.BUILTIN.resolve("default"), e);
        }
    }

    public static void sendSyncModelMessage() {
        // 新一轮同步：清空去重与部分失败标记，保证新问题能重新打印
        WARNED_KEYS.clear();
        SUMMARY_KEYS.clear();
        PARTIAL_FAILED.clear();
        MODELS.clear();
        CACHE_MD5.clear();
        AUTH_MODELS.clear();
        SCALE_INFO.clear();
        METADATA.clear();
        EXTRA_INFO.clear();
        ConditionManager.clear();
        String[] md5Info = getMd5Info();
        SyncModelFiles syncModelFiles = new SyncModelFiles(md5Info);
        ThreadTools.THREAD_POOL.submit(() -> {
            try {
                // 有超时的等待：断线回主菜单时不再永久占住线程池线程
                if (!ThreadTools.awaitUntil(() -> Minecraft.getMinecraft().getConnection() != null,
                        ThreadTools.DEFAULT_WAIT_TIMEOUT_MS)) {
                    YesSteveModel.LOGGER.warn("YSM: 等待服务器连接超时（{} ms），本次模型同步取消",
                            ThreadTools.DEFAULT_WAIT_TIMEOUT_MS);
                    return;
                }
                NetworkHandler.CHANNEL.sendToServer(syncModelFiles);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("YSM: 发送模型同步请求失败", e);
            }
        });
    }

    /**
     * 同一个 key 只打印一次 warn，避免每次同步重复刷屏。
     */
    private static void warnOnce(String key, String format, Object... args) {
        if (WARNED_KEYS.add(key)) {
            YesSteveModel.LOGGER.warn(format, args);
        }
    }

    private static int sizeOf(Map<String, byte[]> map) {
        return map == null ? 0 : map.size();
    }

    private static String typeOf(Object obj) {
        return obj == null ? "null" : obj.getClass().getName();
    }

    private static String[] getMd5Info() {
        Collection<File> files = FileUtils.listFiles(ServerModelManager.CACHE_CLIENT.toFile(), FileFileFilter.FILE, null);
        String[] output = new String[files.size()];
        int i = 0;
        for (File file : files) {
            output[i] = file.getName();
            i++;
        }
        return output;
    }

    private static byte[] getBytes(Path root, String fileName) throws IOException {
        return FileUtils.readFileToByteArray(root.resolve(fileName).toFile());
    }

    private static void addExtraInfo(ResourceLocation modelId, @Nullable ExtraInfo extraInfo) {
        if (extraInfo == null) return;
        ResourceLocation infoId = ModelIdUtil.getInfoId(modelId);
        EXTRA_INFO.put(infoId, extraInfo);
        METADATA.put(infoId, readMetaData(extraInfo));
        if (extraInfo.getFree()) {
            AUTH_MODELS.remove(modelId.getResourcePath());
        }
        if (modelId.getResourcePath().equals("default") && extraInfo.getPreviewAnimation() != null) {
            DEFAULT_ANIMATION_FILE.animations().remove(extraInfo.getPreviewAnimation());
        }
    }

    @Nullable
    private static List<String> readMetaData(@Nullable ExtraInfo extraInfo) {
        if (extraInfo == null || StringUtils.isBlank(extraInfo.getName())) {
            return null;
        }
        List<String> component = Lists.newArrayList();
        component.add(TextFormatting.GOLD + extraInfo.getName());
        if (extraInfo.getTips() != null && StringUtils.isNoneBlank(extraInfo.getTips())) {
            String[] split = extraInfo.getTips().split("\n");
            Arrays.stream(split).forEach(s -> component.add(TextFormatting.GRAY + I18n.format(s)));
        }
        if (extraInfo.getAuthors() != null && extraInfo.getAuthors().length != 0) {
            component.add(I18n.format("gui.yes_steve_model.model.authors", TextFormatting.RESET + StringUtils.join(extraInfo.getAuthors(), "丨")));
        }
        if (StringUtils.isNoneBlank(extraInfo.getLicense())) {
            component.add(I18n.format("gui.yes_steve_model.model.license", TextFormatting.RESET + extraInfo.getLicense()));
        }
        return component;
    }
}
