package com.elfmcys.yesstevemodel.geckolib3.core.molang.util;

import java.util.Collection;
import java.util.HashSet;

/**
 * 1.10.2 环境里 fastutil 是精简版，没有 {@code IntOpenHashSet}，这里直接用 {@link HashSet}。
 */
public class PooledStringHashSet extends HashSet<Integer> {
    public PooledStringHashSet() {
    }

    public PooledStringHashSet(int initialCapacity) {
        super(initialCapacity);
    }

    public PooledStringHashSet(Collection<String> m) {
        for (String key : m) {
            this.add(StringPool.computeIfAbsent(key));
        }
    }

    public PooledStringHashSet(HashSet<Integer> m) {
        super(m);
    }

    public boolean contains(String key) {
        int intKey = StringPool.getName(key);
        if (intKey == StringPool.NONE) {
            return false;
        } else {
            return super.contains(intKey);
        }
    }

    public boolean add(String key) {
        return super.add(StringPool.computeIfAbsent(key));
    }
}
