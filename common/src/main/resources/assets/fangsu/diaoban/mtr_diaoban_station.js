function draw(g, state, drawInfo) {
    setDebugInfo("Running")
    var x = drawInfo.texArea[0];
    var y = drawInfo.texArea[1];
    var w = drawInfo.texArea[2] - drawInfo.texArea[0];
    var h = drawInfo.texArea[3] - drawInfo.texArea[1];
    g.setColor(Color.WHITE);
    g.fillRect(x, y, w, h);

    var routeInfo = drawInfo.routeInfo;
    if (drawInfo.plat) {
        // setDebugInfo(`drawing! drawinfo: ${JSON.stringify(drawInfo, replacer)}`);
        var Serif = loadResource("systemFont", "Noto Serif");
        var SansSerif = loadResource("systemFont", "SansSerif");
        var headAlign = drawInfo.arrowDirection ? Number(drawInfo.arrowDirection) : 0;
        var headString = addPrefix(routeInfo.drawStations[routeInfo.drawStations.length - 1].stationName, "往|To", false);
        var strWidth = getDLStringWidth(g, Serif, Serif, headString, h * 0.4);
        var currentX;
        switch (headAlign) {
            case 0:
                setDebugInfo(`drawing with align 0`);
                currentX = x + w * 0.5 - strWidth * 0.4 - h * 0.3;
                g.setColor(routeInfo.routeColor);
                g.fillOval(currentX, h * 0.3, h * 0.4, h * 0.4);
                g.setColor(isLightColor(routeInfo.routeColor) ? Color.BLACK : Color.WHITE);
                drawStrUnified(g, SansSerif, String(drawInfo.plat.name), currentX + h * 0.2, h * 0.6, h * 0.3, 1);
                currentX += h * 0.6;
                g.setColor(Color.BLACK);
                drawStrDL(g, Serif, SansSerif, headString, currentX, y + h * 0.2, h * 0.45, 0, 0);
                break;
            case 1:
                // setDebugInfo(`drawing with align 1`);
                currentX = x + w * 0.5 - strWidth * 0.4 - h * 0.6;
                g.drawImage(loadResource("img", "fangsu:sign/alb.png"), currentX, y + h * 0.3, h * 0.4, h * 0.4, null);
                currentX += h * 0.6;
                g.setColor(routeInfo.routeColor);
                g.fillOval(currentX, h * 0.3, h * 0.4, h * 0.4);
                g.setColor(isLightColor(routeInfo.routeColor) ? Color.BLACK : Color.WHITE);
                drawStrUnified(g, SansSerif, String(drawInfo.plat.name), currentX + h * 0.2, h * 0.6, h * 0.3, 1);
                currentX += h * 0.6;
                g.setColor(Color.BLACK);
                drawStrDL(g, Serif, SansSerif, headString, currentX, y + h * 0.2, h * 0.45, 0, 0);
                break;
            case 2:
                // setDebugInfo(`drawing with align 2`);
                currentX = x + w * 0.5 - strWidth * 0.4 - h * 0.6;
                g.setColor(Color.BLACK);
                currentX += drawStrDL(g, Serif, SansSerif, headString, currentX, y + h * 0.2, h * 0.45, 0, 1);
                g.setColor(routeInfo.routeColor);
                g.fillOval(currentX, h * 0.3, h * 0.4, h * 0.4);
                g.setColor(isLightColor(routeInfo.routeColor) ? Color.BLACK : Color.WHITE);
                drawStrUnified(g, SansSerif, String(drawInfo.plat.name), currentX + h * 0.2, h * 0.6, h * 0.3, 1);
                currentX += h * 0.6;
                g.drawImage(loadResource("img", "fangsu:sign/arb.png"), currentX, y + h * 0.3, h * 0.4, h * 0.4, null);
                break;
        }
    }
}
