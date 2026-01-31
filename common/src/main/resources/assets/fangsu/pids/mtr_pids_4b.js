var draw = (g, state, drawInfo, extraConfig) => {
    var arrivalInfoList = drawInfo.arrivalInfoList;
    g.setColor(Color.BLACK);
    g.fillRect(drawInfo.texArea[0], drawInfo.texArea[1], drawInfo.texArea[2], drawInfo.texArea[3]);
    if (state.drawBeginTime === undefined) state.drawBeginTime = Timing.elapsed();
    if (state.drawFlag === undefined) state.drawFlag = true;
    if (state.page === undefined) state.page = 1;
    var w = drawInfo.texArea[2] - drawInfo.texArea[0];
    var h = drawInfo.texArea[3] - drawInfo.texArea[1];
    var drawTotalTime = 5;
    var font = loadResource("font", "mtrsteamloco:fonts/ae.ttf").deriveFont(h * 0.05);
    var lineHeight = h / 17;
    var beginHeight = lineHeight * 0.25;
    var beginWidth = beginHeight;
    var endHeight = h - beginHeight;
    var endWidth = w - beginWidth;
    g.setFont(font);
    g.setColor(rgbToColor(252, 151, 0));
    var arrivalInfo = arrivalInfoList[0];
    if (!arrivalInfo) return;
    var arrivingTime = parseInt((arrivalInfo.arrivalMillis - Date.now()) / 1000);
    var forwardStations = arrivalInfo.stationNames.slice(arrivalInfo.currentStationIndex);
    var pages = Math.ceil(forwardStations.length / 11);

    drawStrUnified(g, font, getDispArrival(arrivingTime, state.drawFlag), beginWidth, lineHeight + beginHeight, lineHeight * 0.8, 0);
    drawStrUnified(g, font, state.drawFlag ? arrivalInfo.currentPlatform.name + "站台" : "Plat. " + arrivalInfo.currentPlatform.name, endWidth, lineHeight + beginHeight, lineHeight * 0.8, 2);
    drawStrUnified(g, font, getMatching(arrivalInfo.costomDestination || arrivalInfo.destination, state.drawFlag), beginWidth, lineHeight * 2 + beginHeight, lineHeight * 0.8, 0);
    drawStrUnified(g, font, getMatching("停靠站：|Stops At:", state.drawFlag) + ("(" + state.page + "/" + pages + ")"), beginWidth, lineHeight * 4 + beginHeight, lineHeight * 0.8, 0);

    for (var i = 0; i < Math.min(11, forwardStations.length - (state.page - 1) * 11); i++) {
        var thisTime = drawScrollText(
            getMatching(forwardStations[i + (state.page - 1) * 11], state.drawFlag),
            w - beginWidth * 2,
            beginWidth,
            lineHeight * (5 + i) + beginHeight,
            font,
            state.drawBeginTime
        );
        drawTotalTime = Math.max(drawTotalTime, thisTime);
    }
    if (state.drawBeginTime + drawTotalTime < Timing.elapsed()) {
        state.drawBeginTime = Timing.elapsed();
        if (state.page < pages) state.page++;
        else state.drawFlag = !state.drawFlag;
    }
    g.setColor(Color.RED);
    drawStrUnified(g, font, state.drawFlag ? arrivalInfo.trainCars + " 节" : arrivalInfo.trainCars + "-car", endWidth, lineHeight * 16 + beginHeight, lineHeight * 0.8, 2);

    function drawScrollText(str, maxX, x, y, font, beginTime) {
        g.setFont(font);
        if (g.getFontMetrics(font).stringWidth(str) <= maxX) {
            g.drawString(str, x, y);
            return 0;
        }
        var originalClip = g.getClip();
        var totalTextLeng = g.getFontMetrics(font).stringWidth(str) + maxX;
        var speed = maxX * 0.25;
        var totalTime = parseInt(totalTextLeng / speed);
        g.setClip(new java.awt.Rectangle(x, y - g.getFontMetrics(font).getHeight() - 2, maxX, g.getFontMetrics(font).getHeight() + 4));
        g.drawString(str, x + maxX - ((Timing.elapsed() - beginTime - 0.1) % totalTime) * speed, y);
        g.setClip(originalClip);
        return totalTime;
    }
    function getDispArrival(time, flag) {
        if (time <= 2) return flag ? "已经到达" : "Arrived";
        else if (time <= 20) return flag ? "即将进站" : "Arriving";
        else if (time <= 60) return String(time) + (flag ? " 秒" : " sec");
        else if (time <= 3600) return String(parseInt(time / 60)) + (flag ? " 分" : " min");
        return String(parseInt(time / 3600)) + (flag ? " 时" : " hour");
    }
};
