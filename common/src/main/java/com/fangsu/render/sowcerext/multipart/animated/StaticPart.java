package com.fangsu.render.sowcerext.multipart.animated;

import com.fangsu.render.sowcer.model.VertArrays;
import com.fangsu.render.sowcer.util.AttrUtil;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.multipart.MultipartUpdateProp;
import com.fangsu.render.sowcerext.multipart.PartBase;
import com.fangsu.render.sowcerext.reuse.ModelManager;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.Vector3f;

public class StaticPart extends PartBase {

    private final ModelCluster model;

    private StaticPart(ModelCluster model) {
        this.model = model;
    }

    public StaticPart(RawModel rawModel, ModelManager modelManager) {
        model = modelManager.uploadVertArrays(rawModel);
    }

    @Override
    public void update(MultipartUpdateProp prop) {

    }

    @Override
    public ModelCluster getModel(MultipartUpdateProp prop) {
        return model;
    }

    @Override
    public Matrix4f getTransform(MultipartUpdateProp prop) {
        return parent == null ? AttrUtil.MAT_NO_TRANSFORM : parent.getTransform(prop);
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    public PartBase copy() {
        StaticPart result = new StaticPart(model);
        return result;
    }
}
