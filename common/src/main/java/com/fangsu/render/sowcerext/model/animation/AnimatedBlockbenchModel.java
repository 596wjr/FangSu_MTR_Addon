package com.fangsu.render.sowcerext.model.animation;

import java.util.List;
import java.util.Map;

public final class AnimatedBlockbenchModel extends AnimatedModel {
    private final String formatVersion;
    private final String modelFormat;

    public AnimatedBlockbenchModel(String formatVersion, String modelFormat,
                                   List<AnimatedModelNode> nodes, Map<String, AnimationClip> clips) {
        super(nodes, clips);
        this.formatVersion = formatVersion;
        this.modelFormat = modelFormat;
    }

    public String formatVersion() { return formatVersion; }
    public String modelFormat() { return modelFormat; }
}
