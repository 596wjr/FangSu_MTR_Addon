package com.fangsu.render.sowcerext.multipart;

import com.fangsu.render.sowcer.model.VertArrays;
import com.fangsu.render.sowcer.object.VertArray;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcer.math.Matrix4f;

public abstract class PartBase {

    public PartBase parent;

    public abstract void update(MultipartUpdateProp prop);

    public abstract ModelCluster getModel(MultipartUpdateProp prop);

    public abstract Matrix4f getTransform(MultipartUpdateProp prop);

    public abstract boolean isStatic();

}
