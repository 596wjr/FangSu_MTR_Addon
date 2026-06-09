package com.fangsu.drawing.sis;

import com.fangsu.blockEntities.BlockEntitySis;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.mtr.LocalStation;
import com.fangsu.scripting.GraphicsTexture;

import java.util.Map;

public abstract class BaseSisDrawing {

    public abstract void draw(GraphicsTexture gt, Map<String, Object> drawState,
                              int arrowDirection, int texW, int texH,
                              BlockEntitySis.SISDrawInfo drawInfo);

    protected BlockEntitySis.SISDrawInfo buildDrawInfo(int texW, int texH,
                                                       LocalStation station, LocalRoute[] routes,
                                                       BlockEntitySis.SISDrawInfo originalInfo) {
        return originalInfo;
    }
}
