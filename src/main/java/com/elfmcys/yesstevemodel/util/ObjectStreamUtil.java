package com.elfmcys.yesstevemodel.util;

import com.elfmcys.yesstevemodel.YesSteveModel;

import javax.annotation.Nullable;
import java.io.*;

public final class ObjectStreamUtil {
    public static byte[] toByteArray(Object object) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(stream)) {
            output.writeObject(object);
        } catch (IOException e) {
            YesSteveModel.LOGGER.error("YSM: 序列化对象失败（{}）",
                    object == null ? "null" : object.getClass().getName(), e);
        }
        return stream.toByteArray();
    }

    @Nullable
    public static Object toObject(byte[] data) {
        if (data == null || data.length == 0) {
            YesSteveModel.LOGGER.warn("YSM: 反序列化数据为空（{}），返回 null", data == null ? "null" : "0 字节");
            return null;
        }
        ByteArrayInputStream stream = new ByteArrayInputStream(data);
        try (ObjectInputStream input = new ObjectInputStream(stream)) {
            return input.readObject();
        } catch (ClassNotFoundException | IOException e) {
            YesSteveModel.LOGGER.error("YSM: 反序列化对象失败（{} 字节）", data.length, e);
        }
        return null;
    }
}
