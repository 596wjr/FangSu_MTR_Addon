package com.fangsu.drawing.diaoban;

import com.fangsu.blockEntities.RouteDrawer;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.ui.RouteSelectInfo;

import java.util.List;
import java.util.Map;

public abstract class BaseDiaobanDrawing {

    public abstract void draw(GraphicsTexture gt, List<RouteSelectInfo> routes,
            Map<String, Object> drawState, int arrowDirection, int texW, int texH);

    protected RouteDrawer.RouteDrawInfo buildDrawInfo(List<RouteSelectInfo> routes, int arrowDirection, int texW, int texH) {
        RouteSelectInfo info = routes.isEmpty() ? null : routes.get(0);
        if (info == null || info.route == null) {
            return new RouteDrawer.RouteDrawInfo(null, arrowDirection, null, 0, new int[]{0, 0, texW, texH});
        }
        int index = info.plat != null ? info.route.getPlatformIdIndex(info.plat.id) : 0;
        return new RouteDrawer.RouteDrawInfo(info.route.asRouteDetail(), arrowDirection, info.plat, index, new int[]{0, 0, texW, texH});
    }
}
