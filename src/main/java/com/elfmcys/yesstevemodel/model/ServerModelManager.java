package com.elfmcys.yesstevemodel.model;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.data.EncryptTools;
import com.elfmcys.yesstevemodel.data.ModelData;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.Converter;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.ExtraInfo;
import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.RawGeoModel;
import com.elfmcys.yesstevemodel.model.format.ServerModelInfo;
import com.elfmcys.yesstevemodel.model.format.Type;
import com.elfmcys.yesstevemodel.model.format.access.IModelAccess;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.RequestSyncModel;
import com.elfmcys.yesstevemodel.util.GetJarResources;
import com.elfmcys.yesstevemodel.util.Md5Utils;
import com.elfmcys.yesstevemodel.util.ObjectStreamUtil;
import com.elfmcys.yesstevemodel.util.ResourceUtil;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerList;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.elfmcys.yesstevemodel.model.format.FormatManager.*;

public final class ServerModelManager {
    /**
     * 配置相关文件夹
     */
    public static final Path FOLDER = Paths.get("config", YesSteveModel.MOD_ID);

    /**
     * 内置模型输出的文件夹
     */
    public static final Path BUILTIN = FOLDER.resolve("builtin");
    /**
     * 自定义模型所放置的文件夹
     */
    public static final Path CUSTOM = FOLDER.resolve("custom");
    public static final Path AUTH = FOLDER.resolve("auth");
    public static final Path EXPORT = FOLDER.resolve("export");

    /**
     * 生成缓存文件的文件夹
     */
    public static final Path CACHE = FOLDER.resolve("cache");
    public static final Path CACHE_SERVER = CACHE.resolve("server");
    /**
     * 存储密码的文件
     */
    public static final Path PASSWORD_FILE = CACHE_SERVER.resolve("PASSWORD");
    public static final Path CACHE_CLIENT = CACHE.resolve("client");
    /**
     * 模型名称 -> 模型额外信息缓存
     * 可以方便的通过此缓存，来判断客户端发来的 MD5 在不在服务端
     * 从而将服务器文件发送给玩家
     * 还可以获取其他服务端模型信息
     */
    public static final Map<String, ServerModelInfo> CACHE_NAME_INFO = Maps.newHashMap();

    /**
     * 放置授权模型名称
     */
    public static final Set<String> AUTH_MODELS = Sets.newHashSet();

    /** 本次 reloadPacks 的缓存统计：builtin / custom / auth 成功数，以及失败数 */
    private static int cachedBuiltin;
    private static int cachedCustom;
    private static int cachedAuth;
    private static int cachedFailed;

    public static void sendRequestSyncModelMessage(PlayerList playerList) {
        for (EntityPlayerMP player : playerList.getPlayers()) {
            NetworkHandler.sendToClientPlayer(new RequestSyncModel(), player);
        }
    }

    public static void sendRequestSyncModelMessage() {
        ClientModelManager.sendSyncModelMessage();
    }

    public static void sendRequestSyncModelMessage(EntityPlayer player) {
        NetworkHandler.sendToClientPlayer(new RequestSyncModel(), player);
    }

    public static void reloadPacks() {
        CACHE_NAME_INFO.clear();
        AUTH_MODELS.clear();
        cachedBuiltin = 0;
        cachedCustom = 0;
        cachedAuth = 0;
        cachedFailed = 0;

        createFolder(FOLDER);
        createFolder(CUSTOM);
        createFolder(AUTH);
        createFolder(EXPORT);

        createFolder(CACHE);
        createFolder(CACHE_SERVER);
        createFolder(CACHE_CLIENT);

        deleteFolder(BUILTIN);
        GetJarResources.copyFolder(getCustomFiles("builtin"), BUILTIN.getParent(), "builtin");
        initPassword();
        cacheAllModels(BUILTIN);
        cacheAllModels(CUSTOM);
        cacheAllModels(AUTH);
        // 三个目录都处理完后给出一条总计，方便快速判断本次 reload 是否整体失败
        YesSteveModel.LOGGER.info("YSM: 模型缓存汇总：builtin {} 个，custom {} 个，auth {} 个，失败 {} 个",
                cachedBuiltin, cachedCustom, cachedAuth, cachedFailed);
    }

    private static void initPassword() {
        try {
            EncryptTools.createRandomPassword();
            File passwordFile = PASSWORD_FILE.toFile();
            if (passwordFile.isFile()) {
                EncryptTools.readPassword(FileUtils.readFileToByteArray(passwordFile));
            } else {
                FileUtils.writeByteArrayToFile(passwordFile, EncryptTools.writePassword());
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("YSM: 初始化模型密码失败（{}），客户端将无法解密模型", PASSWORD_FILE, e);
        }
    }

    private static String getCustomFiles(String path) {
        return String.format("/assets/%s/%s", YesSteveModel.MOD_ID, path);
    }

    private static void createFolder(Path path) {
        File folder = path.toFile();
        if (!folder.isDirectory()) {
            try {
                Files.createDirectories(path);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("YSM: 创建模型目录 {} 失败", path, e);
            }
        }
    }

    private static void deleteFolder(Path path) {
        try {
            FileUtils.deleteDirectory(path.toFile());
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("YSM: 删除模型目录 {} 失败", path, e);
        }
    }

    /*
    模型加载
     */

    public static void cacheAllModels(Path rootPath) {
        File[] files = rootPath.toFile().listFiles();
        if (files == null) {
            YesSteveModel.LOGGER.warn("YSM: 模型目录 {} 不存在或不可读，跳过缓存", rootPath);
            return;
        }
        int success = 0;
        int failed = 0;
        for (File file : files) {
            Type type = Type.getType(file);
            if (type == Type.UNKNOWN) continue;
            String modelId = type.getFileName(file);
            if (!ResourceUtil.isValidResourceLocation(modelId)) continue;
            if (loadLegacyModel(rootPath, file, modelId, type)) {
                success++;
                if (rootPath.equals(BUILTIN)) {
                    cachedBuiltin++;
                } else if (rootPath.equals(CUSTOM)) {
                    cachedCustom++;
                } else if (rootPath.equals(AUTH)) {
                    cachedAuth++;
                }
            } else {
                failed++;
                cachedFailed++;
            }
        }
        YesSteveModel.LOGGER.info("YSM: 模型缓存汇总（{}）：成功 {} 个，失败 {} 个",
                rootPath.getFileName(), success, failed);
    }

    /**
     * @return 模型是否成功缓存
     */
    private static boolean loadLegacyModel(Path rootPath, File file, String modelId, Type type) {
        try (IModelAccess access = type.createAccess(file)) {
            if (access == null) {
                YesSteveModel.LOGGER.warn("YSM: 无法读取 {} 模型 {}（访问器创建失败），已跳过",
                        type.getName(), file.getName());
                return false;
            }
            boolean isAuth = rootPath.equals(AUTH);
            ModelData modelData = getModelData(access, modelId, isAuth, type);
            if (modelData == null) {
                YesSteveModel.LOGGER.warn("YSM: 加载 {} 模型 {} 失败（缺少必要模型部件），已跳过",
                        type.getName(), file.getName());
                return false;
            }
            ServerModelInfo info = cacheModel(modelData);
            CACHE_NAME_INFO.put(modelId, info);
            if (isAuth) AUTH_MODELS.add(modelId);
            return true;
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("YSM: 加载 {} 模型 {} 失败", type.getName(), file.getName(), e);
            return false;
        }
    }

    /**
     * @return 若为 null 则代表模型无效。
     */
    @Nullable
    public static ModelData getModelData(IModelAccess access, String modelId, boolean isAuth, Type type) throws IOException {
        Map<String, byte[]> model = Maps.newHashMap();
        // info.json
        byte[] infoData = access.readFile(INFO_FILE_NAME);
        if (infoData != null && infoData.length > 0) {
            String infoJson = new String(infoData, StandardCharsets.UTF_8);
            ExtraInfo info = YesSteveModel.GSON.fromJson(infoJson, ExtraInfo.class);
            model.put(INFO_NAME, ObjectStreamUtil.toByteArray(info));
        }
        for (String modelName : MODEL_NAMES) {
            byte[] data = access.readFile(getModelFileName(modelName));
            if (data == null || data.length == 0) {
                if (isModelNameNecessary(modelName)) return null;
                continue;
            }
            String json = new String(data, StandardCharsets.UTF_8);
            RawGeoModel rawModel = Converter.fromJsonString(json);
            model.put(modelName, ObjectStreamUtil.toByteArray(rawModel));
        }

        Map<String, byte[]> texture = Maps.newHashMap();
        for (String pngPath : access.listFiles(".png")) {
            byte[] data = access.readFile(pngPath);
            if (data != null) {
                texture.put(pngPath, data);
            }
        }

        Map<String, byte[]> animation = Maps.newHashMap();
        for (String animName : ANIMATION_NAMES) {
            byte[] animData = access.readFile(getAnimFileName(animName));
            if (animData == null || animData.length == 0) {
                File defaultFile = getDefaultAnimFile(animName);
                if (defaultFile.isFile()) {
                    animData = FileUtils.readFileToByteArray(defaultFile);
                }
            }
            if (animData != null) {
                animation.put(animName, animData);
            }
        }

        return new ModelData(modelId, isAuth, type, model, texture, animation);
    }

    /**
     * 使用 {@link EncryptTools#assembleEncryptModels(ModelData)} 加密，并以 MD5 值命名存入服务端缓存目录。
     *
     * @param modelData 封装好的序列化模型文件二进制流
     */
    @Nonnull
    private static ServerModelInfo cacheModel(ModelData modelData) throws IOException {
        byte[] dataBytes = EncryptTools.assembleEncryptModels(modelData);
        modelData.setMd5(Md5Utils.md5Hex(dataBytes).toUpperCase(Locale.US));
        FileUtils.writeByteArrayToFile(CACHE_SERVER.resolve(modelData.getInfo().getMd5()).toFile(), dataBytes);
        return modelData.getInfo();
    }
}
