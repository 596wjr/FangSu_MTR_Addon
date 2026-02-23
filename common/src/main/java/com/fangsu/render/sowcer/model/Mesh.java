package com.fangsu.render.sowcer.model;

import com.fangsu.render.sowcer.batch.MaterialProp;
import com.fangsu.render.sowcer.object.IndexBuf;
import com.fangsu.render.sowcer.object.VertBuf;

import java.io.Closeable;

public class Mesh implements Closeable {

    public VertBuf vertBuf;
    public IndexBuf indexBuf;

    public MaterialProp materialProp;

    public Mesh(VertBuf vertBuf, IndexBuf indexBuf, MaterialProp materialProp) {
        this.vertBuf = vertBuf;
        this.indexBuf = indexBuf;
        this.materialProp = materialProp;
    }

    @Override
    public void close() {
        vertBuf.close();
        indexBuf.close();
    }
}
