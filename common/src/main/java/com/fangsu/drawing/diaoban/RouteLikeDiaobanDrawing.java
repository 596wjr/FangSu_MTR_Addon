package com.fangsu.drawing.diaoban;

import com.fangsu.blockEntities.RouteDrawer;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.ui.RouteSelectInfo;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class RouteLikeDiaobanDrawing extends BaseDiaobanDrawing {

    @Override
    public void draw(GraphicsTexture gt, List<RouteSelectInfo> routes, Map<String, Object> drawState, int arrowDirection, int texW, int texH) {
        Graphics2D g = gt.graphics;
        RouteDrawer.RouteDrawInfo drawInfo = buildDrawInfo(routes, arrowDirection, texW, texH);
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, texW, texH);
        g.setComposite(AlphaComposite.SrcOver);

        g.setColor(new Color(18, 18, 18, 230));
        g.fillRoundRect(0, 0, texW, texH, 12, 12);

        final Color routeColor = drawInfo.routeInfo() != null ? drawInfo.routeInfo().routeColor : new Color(0x666666);
        g.setColor(routeColor);
        g.fillRect(0, 0, Math.max(3, texW / 20), texH);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(12, texH / 4)));
        String routeName = drawInfo.routeInfo() != null ? drawInfo.routeInfo().routeName : "N/A";
        g.drawString(routeName, Math.max(8, texW / 14), texH / 2);

        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, texH / 6)));
        String destination = "";
        if (drawInfo.routeInfo() != null && !drawInfo.routeInfo().drawStations.isEmpty()) {
            destination = drawInfo.routeInfo().drawStations.get(drawInfo.routeInfo().drawStations.size() - 1).stationName;
        }
        g.drawString(destination, Math.max(8, texW / 14), (int) (texH * 0.78f));

        if (drawInfo.arrowDirection() == 1) {
            g.fillPolygon(new int[]{texW - 26, texW - 12, texW - 26}, new int[]{texH / 2, texH / 2 - 8, texH / 2 + 8}, 3);
        } else if (drawInfo.arrowDirection() == 2) {
            g.fillPolygon(new int[]{12, 26, 26}, new int[]{texH / 2, texH / 2 - 8, texH / 2 + 8}, 3);
        }
        gt.upload();
    }
}
