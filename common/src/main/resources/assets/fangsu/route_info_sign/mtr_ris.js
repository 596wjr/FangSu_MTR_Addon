function draw(g, state, drawInfo) {
    setDebugInfo("drawing")

    var x = drawInfo.texArea[0];
    var y = drawInfo.texArea[1];
    var w = drawInfo.texArea[2] - drawInfo.texArea[0];
    var h = drawInfo.texArea[3] - drawInfo.texArea[1];

    g.setColor(Color.WHITE);
    g.fillRect(drawInfo.texArea[0], drawInfo.texArea[1], drawInfo.texArea[2], drawInfo.texArea[3]);
    g.setColor(Color.BLACK);
    g.fillRect(drawInfo.texArea[0], drawInfo.texArea[1], w, h * 0.1);

    var routeInfo = drawInfo.routeInfo;
    // setDebugInfo(`Drawing! routeinfo: ${JSON.stringify(routeInfo)}`);
    if (routeInfo != null && routeInfo != undefined && routeInfo.drawStations) {
        // setDebugInfo(`drawing! drawinfo: ${JSON.stringify(drawInfo, replacer)}`);
        var Serif = Resources.getSystemFont("Noto Serif");
        var SansSerif = Resources.getSystemFont("SansSerif");
        var headAlign = drawInfo.arrowDirection ? Number(drawInfo.arrowDirection) : 0;
        var headString = addPrefix(routeInfo.drawStations[routeInfo.drawStations.length - 1].stationName, "往|To", false);
        var strWidth = getDLStringWidth(g, Serif, Serif, headString, h * 0.07);
        var headWidth = strWidth + (headAlign == 0 ? h * 0.1 : h * 0.2);
        var currentX;
        if (drawInfo.plat) {
            switch (headAlign) {
                case 0:
                    // setDebugInfo(`drawing with align 0`);
                    currentX = x + w * 0.5 - strWidth * 0.5 - h * 0.04;
                    g.setColor(routeInfo.routeColor);
                    g.fillOval(currentX, h * 0.015, h * 0.07, h * 0.07);
                    g.setColor(Color.WHITE);
                    drawStrUnified(g, SansSerif, String(drawInfo.plat.name), currentX + h * 0.05, h * 0.065, h * 0.05, 2);
                    currentX += h * 0.08;
                    g.setColor(Color.WHITE);
                    drawStrDL(g, Serif, SansSerif, headString, currentX, y + h * 0.01, h * 0.065, 0, 0);
                    break;
                case 1:
                    // setDebugInfo(`drawing with align 1`);
                    currentX = x + w * 0.5 - strWidth * 0.5 - h * 0.08;
                    g.drawImage(loadResource("img", "fangsu:sign/al.png"), currentX, y + h * 0.02, h * 0.06, h * 0.06, null);
                    currentX += h * 0.08;
                    g.setColor(routeInfo.routeColor);
                    g.fillOval(currentX, h * 0.015, h * 0.07, h * 0.07);
                    g.setColor(Color.WHITE);
                    drawStrUnified(g, SansSerif, String(drawInfo.plat.name), currentX + h * 0.05, h * 0.065, h * 0.05, 2);
                    currentX += h * 0.08;
                    g.setColor(Color.WHITE);
                    drawStrDL(g, Serif, SansSerif, headString, currentX, y + h * 0.01, h * 0.065, 0, 0);
                    break;
                case 2:
                    // setDebugInfo(`drawing with align 2`);
                    currentX = x + w * 0.5 - strWidth * 0.5 - h * 0.08;
                    g.setColor(Color.WHITE);
                    currentX += drawStrDL(g, Serif, SansSerif, headString, currentX, y + h * 0.01, h * 0.065, 0, 2);
                    g.setColor(routeInfo.routeColor);
                    g.fillOval(currentX, h * 0.015, h * 0.07, h * 0.07);
                    g.setColor(Color.WHITE);
                    drawStrUnified(g, SansSerif, String(drawInfo.plat.name), currentX + h * 0.05, h * 0.065, h * 0.05, 2);
                    currentX += h * 0.08;
                    g.drawImage(loadResource("img", "fangsu:sign/ar.png"), currentX, y + h * 0.02, h * 0.06, h * 0.06, null);
                    break;
            }
        }
        var originalClip = g.getClip();
        for (var i = 0; i < routeInfo.drawStations.length; i++) {
            var currentY = y + h * 0.1 + ((h * 0.75) / routeInfo.drawStations.length) * (routeInfo.drawStations.length - i);
            var thisStn = routeInfo.drawStations[i];
            var hasPassed = i <= drawInfo.index;

            if (i > 0) {
                g.setColor(hasPassed ? rgbToColor(124, 124, 124) : routeInfo.routeColor);
                g.fillRect(x + w * 0.49, currentY, w * 0.02, (h * 0.75) / routeInfo.drawStations.length);
            }
        }
        for (var i = 0; i < routeInfo.drawStations.length; i++) {
            var currentY = y + h * 0.1 + ((h * 0.75) / routeInfo.drawStations.length) * (routeInfo.drawStations.length - i);
            var thisStn = routeInfo.drawStations[i];
            var hasPassed = i < drawInfo.index;

            if (thisStn.transInfo.length > 0) {
                g.setClip(new RoundRectangle2D.Double(x + w * 0.47, currentY - w * 0.015, w * 0.1, w * 0.03, w * 0.04, w * 0.04));
                var finalCjkName = "",
                    finalNonCjkName = "";
                for (var j = 0; j < thisStn.transInfo.length; j++) {
                    var thisY = currentY - w * 0.015 + ((w * 0.03) / thisStn.transInfo.length) * j;
                    var thisTrans = thisStn.transInfo[j];
                    g.setColor(hasPassed ? rgbToColor(124, 124, 124) : thisTrans.routeColor);
                    g.fillRect(x + w * 0.48, thisY, w * 0.1, (w * 0.03) / thisStn.transInfo.length);
                    if (hasCjkPart(thisTrans.routeName))
                        if (finalCjkName == "") finalCjkName += getMatching(thisTrans.routeName, true);
                        else finalCjkName += ` / ${getMatching(thisTrans.routeName, true)}`;
                    if (hasNonCjkPart(thisTrans.routeName))
                        if (finalNonCjkName == "") finalNonCjkName += getMatching(thisTrans.routeName, false);
                        else finalNonCjkName += ` / ${getMatching(thisTrans.routeName, false)}`;
                }

                g.setClip(originalClip);
                g.setColor(hasPassed ? rgbToColor(124, 124, 124) : Color.BLACK);
                drawStrDL(g, Serif, Serif, `${finalCjkName} | ${finalNonCjkName}`, x + w * 0.58, thisY - w * 0.015, w * 0.045, 0, 0);
            }

            g.setColor(hasPassed ? rgbToColor(124, 124, 124) : Color.BLACK);
            g.fillOval(x + w * 0.47, currentY - w * 0.03, w * 0.06, w * 0.06);
            g.setColor(Color.WHITE);
            g.fillOval(x + w * 0.478, currentY - w * 0.022, w * 0.044, w * 0.044);

            if (i == drawInfo.index) {
                var strWidth = getDLStringWidth(g, Serif, Serif, thisStn.stationName, w * 0.08);
                g.setColor(Color.BLACK);
                g.fillRect(x + w * 0.44 - strWidth, currentY - w * 0.05, strWidth + w * 0.02, w * 0.1);
                g.setColor(Color.WHITE);
                drawStrDL(g, Serif, Serif, thisStn.stationName, x + w * 0.45, currentY - w * 0.04, w * 0.08, 2, 2);
            } else {
                g.setColor(hasPassed ? rgbToColor(124, 124, 124) : Color.BLACK);
                drawStrDL(g, Serif, Serif, thisStn.stationName, x + w * 0.46, currentY - w * 0.04, w * 0.08, 2, 2);
            }
        }
    }
}
