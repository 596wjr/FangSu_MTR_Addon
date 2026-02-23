package com.fangsu;

import com.fangsu.render.sowcer.util.DrawContext;
import com.fangsu.render.sowcerext.reuse.AtlasManager;
import com.fangsu.render.sowcerext.reuse.DrawScheduler;
import com.fangsu.render.sowcerext.reuse.ModelManager;

public class MainClient {
    public static DrawScheduler drawScheduler = new DrawScheduler();
    public static ModelManager modelManager = new ModelManager();
    public static AtlasManager atlasManager = new AtlasManager();

    public static DrawContext drawContext = new DrawContext();
}
