package com.fangsu.render.scripting.util;

import com.fangsu.render.model.RawModel;

public class DynamicModelHolder {
    private RawModel uploadedModel;

    public void uploadLater(RawModel model) { this.uploadedModel = model; }
    public RawModel getUploadedModel() { return uploadedModel; }
}
