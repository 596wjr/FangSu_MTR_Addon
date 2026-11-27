package com.fangsu.render.sowcerext.model.animation;

import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.animation.AnimationClip.Vector;

/** 父节点在数组中排在子节点之前，网格以本节点原点为中心。 */
public record AnimatedModelNode(String id, int parent, Vector origin, Vector rotation, RawModel geometry) {}
