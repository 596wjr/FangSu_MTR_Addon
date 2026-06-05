package com.fangsu.drawing.pids;

import com.fangsu.blockEntities.BlockEntityPids;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.utils.MtrUtil;

import java.util.List;
import java.util.Map;

public abstract class BasePidsDrawing {

    public abstract void draw(GraphicsTexture gt, List<MtrUtil.PidsArrivalInfo> arrivalInfoList,
                              Map<String, Object> drawState, int texW, int texH,
                              BlockEntityPids.DrawInfoPids drawInfo);

    protected BlockEntityPids.DrawInfoPids buildDrawInfo(
            List<MtrUtil.PidsArrivalInfo> arrivalInfoList,
            int texW, int texH,
            BlockEntityPids.DrawInfoPids originalInfo) {
        return originalInfo;
    }
}
