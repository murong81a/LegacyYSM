package com.elfmcys.yesstevemodel.geckolib3.core.molang.util;

import java.util.HashMap;

public class PooledStringHashMap<V> extends HashMap<Integer, V> {
    public PooledStringHashMap() {
    }

    public PooledStringHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    public PooledStringHashMap(HashMap<Integer, V> m) {
        super(m);
    }

    public V get(String key) {
        int intKey = StringPool.getName(key);
        if (intKey == StringPool.NONE) {
            return null;
        } else {
            return super.get(intKey);
        }
    }

    public void put(String key, V value) {
        super.put(StringPool.computeIfAbsent(key), value);
    }
}
