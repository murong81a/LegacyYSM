package com.elfmcys.yesstevemodel.data;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.model.format.Type;
import com.elfmcys.yesstevemodel.util.AESUtil;
import com.elfmcys.yesstevemodel.util.ByteInteger;
import com.elfmcys.yesstevemodel.util.DeflateUtil;
import com.elfmcys.yesstevemodel.util.Md5Utils;
import com.google.common.collect.Maps;
import java.util.Arrays;

import javax.annotation.Nullable;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.InflaterInputStream;

@SuppressWarnings("ResultOfMethodCallIgnored")
public final class EncryptTools {
    /**
     * 二进制文件的头部幻数
     * <pre>
     * YSGP 的 ASCII 码
     * YSGP 就是 Ying Su Group，映素小组的缩写
     * </pre>
     */
    public static final int HEAD = 0x59_53_47_50;
    /// 二进制文件的版本号
    public static final int VERSION = 0x00_00_00_01;
    /// 加密方法
    private static final String ENCRYPTION_METHOD = "AES";
    /// 二进制密码文件长度
    private static final int PASSWORD_SIZE = 40;
    /// 单个模型文件（模型 / 贴图 / 动画块）解压后的长度上限：64 MiB
    public static final int MAX_MODEL_FILE_SIZE = 64 << 20;
    /// 整个模型包解压后的总长度上限：512 MiB
    public static final int MAX_UNPACKED_SIZE = 512 << 20;
    /// 单个字符串（模型 id / 部件名 / 相对路径）的字节上限：1 KiB
    private static final int MAX_STRING_SIZE = 1024;
    /// 单个模型包内条目数量上限，避免恶意数据构造超长循环
    private static final int MAX_MAP_ENTRIES = 4096;
    /// 模型包密码（可能由服务端重载线程重建，工作线程读取，故用 volatile 保证可见性）
    private static volatile SecretKey SECRET_KEY;
    /// 模型包特征矩阵（还是密码）
    private static volatile IvParameterSpec IV;

    public static void createRandomPassword() {
        SECRET_KEY = AESUtil.generateKey();
        IV = AESUtil.generateIv();
    }

    public static void readPassword(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length != PASSWORD_SIZE) {
            YesSteveModel.LOGGER.warn("YSM: 密码文件长度非法（期望 {}，实际 {}），忽略该文件",
                    PASSWORD_SIZE, fileBytes == null ? -1 : fileBytes.length);
            return;
        }
        int head = ByteInteger.bytes2Int(fileBytes, 0);
        int version = ByteInteger.bytes2Int(fileBytes, 4);
        if (head != HEAD) {
            YesSteveModel.LOGGER.warn("YSM: 密码文件头部幻数不匹配（{}），忽略该文件", Integer.toHexString(head));
            return;
        }
        if (version != VERSION) {
            YesSteveModel.LOGGER.warn("YSM: 密码文件版本不匹配（{}，期望 {}），忽略该文件",
                    version, VERSION);
            return;
        }
        byte[] password = Arrays.copyOfRange(fileBytes, 8, 8 + 16);
        byte[] iv = Arrays.copyOfRange(fileBytes, 24, 24 + 16);
        SECRET_KEY = new SecretKeySpec(password, ENCRYPTION_METHOD);
        IV = new IvParameterSpec(iv);
    }

    public static byte[] writePassword() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(ByteInteger.int2Bytes(HEAD));
        stream.write(ByteInteger.int2Bytes(VERSION));
        stream.write(SECRET_KEY.getEncoded());
        stream.write(IV.getIV());
        return stream.toByteArray();
    }

    /**
     * 将附加信息和加密文件块组合成二进制文件
     * <pre>
     * -----------------------------------------
     * 59 53 47 50                        幻数
     * 00 00 00 01                      版本号
     * 00 00 00 00 00 00 00 00
     * 00 00 00 00 00 00 00 00        文件 MD5
     * -----------------------------------------
     * 加密文件块
     * </pre>
     */
    public static byte[] assembleEncryptModels(ModelData data) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(ByteInteger.int2Bytes(HEAD));
        stream.write(ByteInteger.int2Bytes(VERSION));
        byte[] encryptModelBytes = encryptModel(data);
        byte[] md5 = Md5Utils.md5(encryptModelBytes);
        stream.write(md5);
        stream.write(encryptModelBytes);
        return stream.toByteArray();
    }


    /**
     * 将模型、材质、动画组合成加密、压缩、二进制数据
     * <pre>
     * -----------------------------------------
     * 7F FF FF FF 模型名称字符串
     *
     * 7F FF FF FF 模型文件数量
     * 7F FF FF FF 模型文件名称
     * 7F FF FF FF 模型文件大小
     * 7F FF FF FF 模型文件名称
     * 7F FF FF FF 模型文件大小
     *
     * 7F FF FF FF 材质文件大小
     * 7F FF FF FF 动画文件大小
     * 模型二进制文件块
     * 材质二进制文件块
     * 动画二进制文件块
     * -----------------------------------------
     * 进行一次 tar.gz 压缩
     * 进行一次 AES 加密
     * <pre>
     */
    private static byte[] encryptModel(ModelData data) throws IOException {
        try {
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();

            writeString(tmp, data.getModelId());
            writeBoolean(tmp, data.isAuth());

            writeMapDataInfo(tmp, data.getModel());
            writeMapDataInfo(tmp, data.getTexture());
            writeMapDataInfo(tmp, data.getAnimation());

            writeMapData(tmp, data.getModel());
            writeMapData(tmp, data.getTexture());
            writeMapData(tmp, data.getAnimation());

            byte[] output = DeflateUtil.compressBytes(tmp.toByteArray());
            return AESUtil.encrypt(SECRET_KEY, IV, output).toByteArray();
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("YSM: 加密模型数据失败", e);
            // 不能返回空数组：那会生成一个客户端必然解析失败的缓存文件
            throw new IOException("YSM: 加密模型数据失败", e);
        }
    }

    private static void writeString(ByteArrayOutputStream stream, String string) throws IOException {
        byte[] stringBytes = string.getBytes(StandardCharsets.UTF_8);
        // 与 readString 的上限保持一致，避免服务端生成客户端必然拒绝的缓存
        if (stringBytes.length > MAX_STRING_SIZE) {
            throw new IOException("YSM: 模型字符串过长：" + stringBytes.length + "（上限 " + MAX_STRING_SIZE + "）");
        }
        stream.write(ByteInteger.int2Bytes(stringBytes.length));
        stream.write(stringBytes);
    }

    private static void writeBoolean(ByteArrayOutputStream stream, boolean value) throws IOException {
        stream.write(ByteInteger.int2Bytes(value ? 1 : 0));
    }

    private static void writeFileInfo(ByteArrayOutputStream stream, String name, byte[] input) throws IOException {
        // 与 readMapData 的上限保持一致
        if (input.length > MAX_MODEL_FILE_SIZE) {
            throw new IOException("YSM: 模型文件 " + name + " 过大：" + input.length
                    + "（上限 " + MAX_MODEL_FILE_SIZE + "）");
        }
        writeString(stream, name);
        stream.write(ByteInteger.int2Bytes(input.length));
    }

    private static void writeMapDataInfo(ByteArrayOutputStream stream, Map<String, byte[]> input) throws IOException {
        if (input.size() > MAX_MAP_ENTRIES) {
            throw new IOException("YSM: 模型条目数量过多：" + input.size() + "（上限 " + MAX_MAP_ENTRIES + "）");
        }
        stream.write(ByteInteger.int2Bytes(input.size()));
        for (String name : input.keySet()) {
            writeFileInfo(stream, name, input.get(name));
        }
    }

    private static void writeMapData(ByteArrayOutputStream stream, Map<String, byte[]> input) throws IOException {
        for (byte[] data : input.values()) {
            stream.write(data);
        }
    }

    public static byte[] encryptPassword(byte[] uuid, byte[] input) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(uuid, ENCRYPTION_METHOD);
        IvParameterSpec iv = new IvParameterSpec(uuid);
        return AESUtil.encrypt(secretKey, iv, input).toByteArray();
    }

    private static byte[] decryptPassword(byte[] uuid, byte[] input) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(uuid, ENCRYPTION_METHOD);
        IvParameterSpec iv = new IvParameterSpec(uuid);
        byte[] rawPassword = AESUtil.decrypt(secretKey, iv, input).toByteArray();
        if (rawPassword.length != PASSWORD_SIZE) {
            YesSteveModel.LOGGER.warn("YSM: 解密后的密码长度非法（{}，期望 {}）", rawPassword.length, PASSWORD_SIZE);
            return new byte[0];
        }
        if (ByteInteger.bytes2Int(rawPassword, 0) != HEAD) {
            YesSteveModel.LOGGER.warn("YSM: 解密后的密码头部幻数不匹配");
            return new byte[0];
        }
        if (ByteInteger.bytes2Int(rawPassword, 4) != VERSION) {
            YesSteveModel.LOGGER.warn("YSM: 解密后的密码版本不匹配");
            return new byte[0];
        }
        return rawPassword;
    }

    @Nullable
    public static ModelData decryptModel(byte[] uuid, byte[] password, byte[] modelRawData) {
        try {
            byte[] rawPassword = decryptPassword(uuid, password);
            if (rawPassword.length == 0) {
                return null;
            }
            // 先校验固定头部长度，避免后续 bytes2Int 越界
            if (modelRawData == null || modelRawData.length < 24) {
                YesSteveModel.LOGGER.warn("YSM: 模型数据长度不足 24 字节（{}），拒绝解析",
                        modelRawData == null ? -1 : modelRawData.length);
                return null;
            }
            if (ByteInteger.bytes2Int(modelRawData, 0) != HEAD) {
                YesSteveModel.LOGGER.warn("YSM: 模型数据头部幻数不匹配，拒绝解析");
                return null;
            }
            if (ByteInteger.bytes2Int(modelRawData, 4) != VERSION) {
                YesSteveModel.LOGGER.warn("YSM: 模型数据版本不匹配，拒绝解析");
                return null;
            }

            String md5 = Md5Utils.toHexString(Arrays.copyOfRange(modelRawData, 8, 8 + 16));
            byte[] encryptModelData = Arrays.copyOfRange(modelRawData, 24, modelRawData.length);
            String dataMd5 = Md5Utils.md5Hex(encryptModelData);
            if (!md5.equals(dataMd5)) {
                // FIXME: 2023/7/11 很奇怪，这一块会出现不一致的问题
                YesSteveModel.LOGGER.warn("Check values are not equal {} / {}", md5, dataMd5);
            }

            byte[] passwordBytes = Arrays.copyOfRange(rawPassword, 8, 8 + 16);
            byte[] ivBytes = Arrays.copyOfRange(rawPassword, 24, 24 + 16);
            SecretKeySpec key = new SecretKeySpec(passwordBytes, ENCRYPTION_METHOD);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);
            ByteArrayOutputStream decryptModelData = AESUtil.decrypt(key, iv, encryptModelData);
            // 使用带上限的解压，防止 deflate 炸弹把客户端内存撑爆
            byte[] modelData = decompressBounded(decryptModelData.toByteArray(), MAX_UNPACKED_SIZE);

            if (modelData.length == 0) {
                YesSteveModel.LOGGER.warn("YSM: 模型解压结果为空，拒绝解析");
                return null;
            }

            ByteArrayInputStream tmp = new ByteArrayInputStream(modelData);

            String modelId = readString(tmp);
            boolean isAuth = readBoolean(tmp);
            Map<String, Integer> modelMapDataInfo = readMapDataInfo(tmp);
            Map<String, Integer> textureMapDataInfo = readMapDataInfo(tmp);
            Map<String, Integer> animationMapDataInfo = readMapDataInfo(tmp);

            Map<String, byte[]> modelMapData = readMapData(tmp, modelMapDataInfo);
            Map<String, byte[]> textureMapData = readMapData(tmp, textureMapDataInfo);
            Map<String, byte[]> animationMapData = readMapData(tmp, animationMapDataInfo);

            return new ModelData(modelId, isAuth, Type.UNKNOWN, modelMapData, textureMapData, animationMapData);
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("YSM: 解密/解压模型失败（数据可能已损坏或是恶意压缩炸弹）", e);
        }
        return null;
    }

    /**
     * 解压 deflate 数据，并强制限制解压后的总长度。
     * <p>
     * 不能直接使用 {@link DeflateUtil#decompressBytes(byte[])}：它不限制输出长度，
     * 一个几 KB 的 deflate 炸弹就能把客户端内存撑爆（OOM）。
     *
     * @param maxOut 解压后允许的最大字节数
     * @throws IOException 解压结果超过 {@code maxOut} 或数据格式非法时抛出
     */
    private static byte[] decompressBounded(byte[] input, int maxOut) throws IOException {
        if (input.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(input))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inflater.read(buffer)) > 0) {
                total += read;
                if (total > maxOut) {
                    throw invalidData("解压后数据超过上限 " + maxOut + " 字节（已解压 " + total + " 字节）");
                }
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private static Map<String, byte[]> readMapData(ByteArrayInputStream tmp, Map<String, Integer> modelMapDataInfo) throws IOException {
        Map<String, byte[]> output = Maps.newHashMap();
        for (Map.Entry<String, Integer> entry : modelMapDataInfo.entrySet()) {
            String name = entry.getKey();
            int size = entry.getValue();
            // 分配前校验：负数 / 超过单文件上限 / 超过剩余数据一律拒绝
            if (size < 0 || size > MAX_MODEL_FILE_SIZE) {
                throw invalidData("模型文件 " + name + " 长度非法：" + size + "（上限 " + MAX_MODEL_FILE_SIZE + "）");
            }
            if (size > tmp.available()) {
                throw invalidData("模型文件 " + name + " 长度 " + size + " 超过剩余数据 " + tmp.available());
            }
            byte[] sizeBytes = new byte[size];
            readFully(tmp, sizeBytes);
            output.put(name, sizeBytes);
        }
        return output;
    }

    private static Map<String, Integer> readMapDataInfo(ByteArrayInputStream tmp) throws IOException {
        Map<String, Integer> mapDataInfo = Maps.newHashMap();
        int modelCount = readInt(tmp);
        if (modelCount < 0 || modelCount > MAX_MAP_ENTRIES) {
            throw invalidData("模型条目数量非法：" + modelCount + "（上限 " + MAX_MAP_ENTRIES + "）");
        }
        for (int i = 0; i < modelCount; i++) {
            String name = readString(tmp);
            int size = readInt(tmp);
            if (size < 0 || size > MAX_MODEL_FILE_SIZE) {
                throw invalidData("模型文件 " + name + " 长度非法：" + size + "（上限 " + MAX_MODEL_FILE_SIZE + "）");
            }
            mapDataInfo.put(name, size);
        }
        return mapDataInfo;
    }

    private static String readString(ByteArrayInputStream stream) throws IOException {
        int size = readInt(stream);
        if (size < 0 || size > MAX_STRING_SIZE) {
            throw invalidData("字符串长度非法：" + size + "（上限 " + MAX_STRING_SIZE + "）");
        }
        if (size > stream.available()) {
            throw invalidData("字符串长度 " + size + " 超过剩余数据 " + stream.available());
        }
        byte[] stringBytes = new byte[size];
        readFully(stream, stringBytes);
        return new String(stringBytes, StandardCharsets.UTF_8);
    }

    private static boolean readBoolean(ByteArrayInputStream stream) throws IOException {
        return readInt(stream) != 0;
    }

    private static int readInt(ByteArrayInputStream stream) throws IOException {
        if (stream.available() < 4) {
            throw invalidData("模型数据剩余 " + stream.available() + " 字节，不足以读取 4 字节整数");
        }
        byte[] sizeBytes = new byte[4];
        readFully(stream, sizeBytes);
        return ByteInteger.bytes2Int(sizeBytes, 0);
    }

    /**
     * 从 {@link ByteArrayInputStream} 精确读满整个数组；读不满说明数据被截断。
     */
    private static void readFully(ByteArrayInputStream stream, byte[] output) throws IOException {
        int offset = 0;
        while (offset < output.length) {
            int read = stream.read(output, offset, output.length - offset);
            if (read < 0) {
                throw invalidData("模型数据提前结束：期望 " + output.length + " 字节，实际 " + offset + " 字节");
            }
            offset += read;
        }
    }

    /**
     * 统一构造并记录「拒绝非法模型数据」的异常，避免静默返回空数组。
     */
    private static IOException invalidData(String message) {
        YesSteveModel.LOGGER.warn("YSM: 拒绝非法模型数据：{}", message);
        return new IOException(message);
    }
}
