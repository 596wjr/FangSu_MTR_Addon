package fabric.cn.zbx1425.mtrsteamloco.render.scripting.util;

import fabric.cn.zbx1425.sowcerext.model.RawModel;

public class DynamicModelHolder {
    private RawModel rawModel;

    public void uploadLater(RawModel model) {
        this.rawModel = model;
    }

    public RawModel getRawModel() {
        return rawModel;
    }
}
