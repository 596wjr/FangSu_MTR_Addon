package com.fangsu.blockEntities;

import com.fangsu.mappings.GsonHelper;
import com.fangsu.Main;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.mtr.LocalRouteDetail;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.ui.RouteSelectInfo;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import com.fangsu.utils.GraphicsTextureHelper;
import com.fangsu.utils.MtrUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import mtr.data.Platform;

import java.awt.*;
import java.util.*;
import java.util.List;

public interface RouteDrawer {

    default void drawFunction(GraphicsTexture gt, ScriptHolderBase scriptHolder, List<RouteSelectInfo> routes,
            Map<String, Object> drawState, int arrowDirection, int texW, int texH) {
        Graphics2D g = gt.graphics;
        if (scriptHolder == null) {
            return;
        }
        LocalRoute route = null;
        Platform plat = null;
        int index = 0;
        if (!routes.isEmpty()) {
            RouteSelectInfo routeSelectInfo = routes.get(0);
            route = routeSelectInfo.route;
            plat = routeSelectInfo.plat;
            if (plat != null) {
                index = route.getPlatformIdIndex(routeSelectInfo.plat.id);
            }
        }
        if (route == null) {
            route = new LocalRoute();
            Main.LOGGER.error("route not found");
        }
        ScriptManager.getInstance().requestRunFunctionWithCallback(scriptHolder, gt::upload, "draw", g, drawState,
                new RouteDrawInfo(
                        route.asRouteDetail(), arrowDirection, plat, index, new int[]{0, 0, texW, texH}
                ));
    }

    default List<RouteSelectInfo> reloadRoute(String routeString) {
        List<JsonElement> rawRoutes = GsonHelper.asList(Main.JSON_PARSER.parse(routeString).getAsJsonArray());
//        Main.LOGGER.info("rawRoutes {}", rawRoutes);
        ArrayList<RouteSelectInfo> routes = new ArrayList<>();
        for (JsonElement rawRoute : rawRoutes) {
            if (rawRoute.getAsJsonArray().size() < 2) {
                continue;
            }
            JsonArray a = rawRoute.getAsJsonArray();
            LocalRoute route = MtrUtil.getRouteById(a.get(0).getAsLong());
            Platform plat = MtrUtil.getPlatformById(a.get(1).getAsLong());
            if (route == null || plat == null) {
                continue;
            }
            routes.add(new RouteSelectInfo(route, plat));
        }
        if (routes.isEmpty()) {
            routes.add(new RouteSelectInfo(new LocalRoute(), null));
        }
        return routes;

    }

    class RouteDrawInfo {

        public final LocalRouteDetail routeInfo;
        public final int arrowDirection;
        public final Platform plat;
        public final int index;
        public final int[] texArea;

        public RouteDrawInfo(LocalRouteDetail routeInfo, int arrowDirection, Platform plat, int index,
                int[] texArea) {
            this.routeInfo = routeInfo;
            this.arrowDirection = arrowDirection;
            this.plat = plat;
            this.index = index;
            this.texArea = texArea;
        }

        public LocalRouteDetail routeInfo() {
            return routeInfo;
        }

        public int arrowDirection() {
            return arrowDirection;
        }

        public Platform plat() {
            return plat;
        }

        public int index() {
            return index;
        }

        public int[] texArea() {
            return texArea;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (RouteDrawInfo) obj;
            return Objects.equals(this.routeInfo, that.routeInfo)
                    && this.arrowDirection == that.arrowDirection
                    && Objects.equals(this.plat, that.plat)
                    && this.index == that.index
                    && Arrays.equals(this.texArea, that.texArea);
        }

        @Override
        public int hashCode() {
            return Objects.hash(routeInfo, arrowDirection, plat, index, Arrays.hashCode(texArea));
        }

        @Override
        public String toString() {
            return "RouteDrawInfo["
                    + "routeInfo=" + routeInfo + ", "
                    + "arrowDirection=" + arrowDirection + ", "
                    + "plat=" + plat + ", "
                    + "index=" + index + ", "
                    + "texArea=" + Arrays.toString(texArea) + ']';
        }

    }
}
