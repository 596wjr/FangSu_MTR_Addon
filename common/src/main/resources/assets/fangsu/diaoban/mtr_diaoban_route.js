function draw(g, state, drawInfo) {
    setDebugInfo("running")
    var x = drawInfo.texArea[0];
    var y = drawInfo.texArea[1];
    var w = drawInfo.texArea[2] - drawInfo.texArea[0];
    var h = drawInfo.texArea[3] - drawInfo.texArea[1];

    var Serif = loadResource("systemFont", "Noto Serif");
    // var SansSerif = Resources.getSystemFont("SansSerif");

    g.setColor(Color.WHITE);
    g.fillRect(drawInfo.texArea[0], drawInfo.texArea[1], drawInfo.texArea[2], drawInfo.texArea[3]);

    var routeInfo = drawInfo.routeInfo;

    if (routeInfo != null && routeInfo != undefined) {
        var originalClip = g.getClip();
        var originalTransform = g.getTransform();
        var routeColor = routeInfo.routeColor;
        var arrowSide = drawInfo.arrowDirection ? Number(drawInfo.arrowDirection) : 0;

        var distance = (w * 0.8) / (routeInfo.drawStations.length - 1);
        g.setColor(routeColor);
        g.fillRect(x + w * 0.1, y + h * 0.47, w * 0.8, h * 0.06);
        g.setColor(rgbToColor(124, 124, 124));
        g.fillRect(x + w * 0.1 + (arrowSide != 2 ? w * 0.8 - drawInfo.index * distance : 0), y + h * 0.47, drawInfo.index * distance, h * 0.06);

        for (var i = 0; i < routeInfo.drawStations.length; i++) {
            var currentX = x + w * 0.1 + (arrowSide == 2 ? i * distance : w * 0.8 - i * distance);
            var nameOnTop = i % 2 == 1;
            var hasPassed = i < drawInfo.index;
            var thisStn = routeInfo.drawStations[i];

            if (thisStn.transInfo.length > 0) {
                g.setClip(new RoundRectangle2D.Double(currentX - h * 0.03, y + h * (nameOnTop ? 0.5 : 0.375), h * 0.06, h * 0.125, h * 0.04, h * 0.04));
                var finalCjkName = "",
                    finalNonCjkName = "";
                for (var j = 0; j < thisStn.transInfo.length; j++) {
                    var thisX = currentX - h * 0.03 + ((h * 0.06) / thisStn.transInfo.length) * j;
                    var thisTrans = thisStn.transInfo[j];
                    g.setColor(hasPassed ? rgbToColor(124, 124, 124) : thisTrans.routeColor);
                    g.fillRect(thisX, y + h * 0.3, (h * 0.06) / thisStn.transInfo.length, h * 0.4);
                    if (hasCjkPart(thisTrans.routeName))
                        if (finalCjkName == "") finalCjkName += getMatching(thisTrans.routeName, true);
                        else finalCjkName += ` / ${getMatching(thisTrans.routeName, true)}`;
                    if (hasNonCjkPart(thisTrans.routeName))
                        if (finalNonCjkName == "") finalNonCjkName += getMatching(thisTrans.routeName, false);
                        else finalNonCjkName += ` / ${getMatching(thisTrans.routeName, false)}`;
                }
                g.setClip(originalClip);
                g.setColor(hasPassed ? rgbToColor(124, 124, 124) : Color.BLACK);
                drawStrDL(g, Serif, Serif, `${finalCjkName} | ${finalNonCjkName}`, currentX, y + h * (nameOnTop ? 0.675 : 0.275), h * 0.075, 1, 1);
            }

            g.setColor(hasPassed ? rgbToColor(124, 124, 124) : Color.BLACK);
            g.fillOval(currentX - h * 0.06, y + h * 0.44, h * 0.12, h * 0.12);
            g.setColor(Color.WHITE);
            g.fillOval(currentX - h * 0.045, y + h * 0.455, h * 0.09, h * 0.09);

            if (i == drawInfo.index) {
                var strWidth = getDLStringWidth(g, Serif, Serif, thisStn.stationName, h * 0.2);
                g.setColor(Color.BLACK);
                g.fillRect(currentX - strWidth * 0.5 - h * 0.015, y + h * (nameOnTop ? 0.225 : 0.575), strWidth + h * 0.03, h * 0.2);
                g.setColor(Color.WHITE);
                drawStrDL(g, Serif, Serif, thisStn.stationName, currentX, y + h * (nameOnTop ? 0.25 : 0.6), h * 0.15, 1, 1);
            } else {
                g.setColor(hasPassed ? rgbToColor(124, 124, 124) : Color.BLACK);
                drawStrDL(g, Serif, Serif, thisStn.stationName, currentX, y + h * (nameOnTop ? 0.25 : 0.6), h * 0.15, 1, 1);
            }
        }
    }
}
