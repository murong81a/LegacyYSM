package com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo;

import com.google.gson.*;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * 模型格式版本。
 * <p>
 * 原版使用 Maven 的 {@code VersionRange} 来判断版本区间，但 1.10.2 环境里没有
 * {@code maven-artifact}，这里退化成简单的 {@code major.minor} 比较：
 * {@code [1.12.0,)} 归为 {@link #NEW}，其余（{@code [1.10.0]}）归为 {@link #LEGACY}。
 */
public enum FormatVersion {
    /**
     * 旧版本基岩版模型，仅限 1.10.0
     */
    LEGACY,
    /**
     * 新版本基岩版模型，往后的 1.14.0，1.16.0 1.21.0 通通用此版本读取
     */
    NEW;

    private static final int NEW_MAJOR = 1;
    private static final int NEW_MINOR = 12;

    public static FormatVersion forValue(String value) throws IOException {
        int[] parts = parse(value);
        if (parts[0] > NEW_MAJOR || (parts[0] == NEW_MAJOR && parts[1] >= NEW_MINOR)) {
            return NEW;
        }
        return LEGACY;
    }

    private static int[] parse(String value) throws IOException {
        if (value == null) {
            throw new IOException("Null format version");
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        int comma = trimmed.indexOf(',');
        if (comma >= 0) {
            trimmed = trimmed.substring(0, comma);
        }
        String[] split = trimmed.split("\\.");
        if (split.length < 2) {
            throw new IOException("Invalid format version: " + value);
        }
        try {
            return new int[]{Integer.parseInt(split[0].trim()), Integer.parseInt(split[1].trim())};
        } catch (NumberFormatException e) {
            throw new IOException("Invalid format version: " + value, e);
        }
    }

    public String toValue() {
        return switch (this) {
            case LEGACY -> "1.10.0";
            case NEW -> "1.12.0";
        };
    }

    public static class Serializer implements JsonSerializer<FormatVersion>, JsonDeserializer<FormatVersion> {
        @Override
        public FormatVersion deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return FormatVersion.forValue(json.getAsString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public JsonElement serialize(FormatVersion src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toValue());
        }
    }
}
