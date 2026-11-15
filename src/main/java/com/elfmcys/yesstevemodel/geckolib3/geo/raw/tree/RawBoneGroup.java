package com.elfmcys.yesstevemodel.geckolib3.geo.raw.tree;

import com.elfmcys.yesstevemodel.geckolib3.geo.raw.pojo.Bone;
import java.util.HashMap;

import java.util.Map;

public class RawBoneGroup {
    public Map<String, RawBoneGroup> children = new HashMap<>();
    public Bone selfBone;

    public RawBoneGroup(Bone bone) {
        this.selfBone = bone;
    }
}
