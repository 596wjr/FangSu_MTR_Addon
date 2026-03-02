var draw = (g, state, drawInfo, extraConfig) => {
    var arrivalInfoList = drawInfo.arrivalInfoList || [];
    var texArea = drawInfo.texArea;
    var width = texArea[2] - texArea[0];
    var height = texArea[3] - texArea[1];

    g.setColor(rgbToColor(0, 0, 0));
    g.fillRect(texArea[0], texArea[1], texArea[2], texArea[3]);

    // setDebugInfo(drawInfo.toString());

    if (state.drawBeginTime === undefined) state.drawBeginTime = Timing.elapsed();
    if (state.drawFlag === undefined) state.drawFlag = true;
    if (state.page === undefined) state.page = 1;

    var fontSize = height * 0.05;
    if (state.fontSize !== fontSize || state.font === undefined) {
        state.fontSize = fontSize;
        state.font = loadResource("font", "fangsu:fonts/ae.ttf").deriveFont(parseInt(fontSize));
    }

    var font = state.font;
    g.setFont(font);
    var metrics = g.getFontMetrics(font);

    var lineHeight = height / 17;
    var beginHeight = lineHeight * 0.25;
    var beginWidth = beginHeight;
    var endWidth = width - beginWidth;

    g.setColor(rgbToColor(252, 151, 0));
    var arrivalInfo = arrivalInfoList[0];
    if (!arrivalInfo) return;

    var arrivingTime = parseInt((arrivalInfo.arrivalMillis - Date.now()) / 1000);
    var forwardStations = (arrivalInfo.stationNames || []).slice(arrivalInfo.currentStationIndex);
    var pages = Math.max(1, Math.ceil(forwardStations.length / 11));
    if (state.page > pages) state.page = 1;

    drawStrUnified(g, font, getDispArrival(arrivingTime, state.drawFlag), beginWidth, lineHeight + beginHeight, lineHeight * 0.8, 0);
    var platformName = arrivalInfo.currentPlatformName || "-";
    drawStrUnified(g, font, state.drawFlag ? platformName + "站台" : "Plat. " + platformName, endWidth, lineHeight + beginHeight, lineHeight * 0.8, 2);
    drawStrUnified(g, font, getMatching(arrivalInfo.customDestination || arrivalInfo.destination, state.drawFlag), beginWidth, lineHeight * 2 + beginHeight, lineHeight * 0.8, 0);
    drawStrUnified(g, font, getMatching("停靠站：|Stops At:", state.drawFlag) + "(" + state.page + "/" + pages + ")", beginWidth, lineHeight * 4 + beginHeight, lineHeight * 0.8, 0);

    var drawTotalTime = 5;
    var offset = (state.page - 1) * 11;
    for (var i = 0; i < Math.min(11, forwardStations.length - offset); i++) {
        var stationName = getMatching(forwardStations[i + offset], state.drawFlag);
        drawTotalTime = Math.max(drawTotalTime, drawScrollText(stationName, width - beginWidth * 2, beginWidth, lineHeight * (5 + i) + beginHeight, metrics, state.drawBeginTime));
    }

    if (state.drawBeginTime + drawTotalTime < Timing.elapsed()) {
        state.drawBeginTime = Timing.elapsed();
        if (state.page < pages) {
            state.page++;
        } else {
            state.page = 1;
            state.drawFlag = !state.drawFlag;
        }
    }

    g.setColor(Color.RED);
    drawStrUnified(g, font, state.drawFlag ? arrivalInfo.trainCars + " 节" : arrivalInfo.trainCars + "-car", endWidth, lineHeight * 16 + beginHeight, lineHeight * 0.8, 2);

    function drawScrollText(str, maxX, x, y, metrics, beginTime) {
        var textWidth = metrics.stringWidth(str);
        if (textWidth <= maxX) {
            g.drawString(str, x, y);
            return 0;
        }
        var originalClip = g.getClip();
        var totalTextLength = textWidth + maxX;
        var speed = maxX * 0.25;
        var totalTime = Math.max(1, parseInt(totalTextLength / speed));
        g.setClip(new Rectangle(x, y - metrics.getHeight() - 2, maxX, metrics.getHeight() + 4));
        g.drawString(str, x + maxX - ((Timing.elapsed() - beginTime - 0.1) % totalTime) * speed, y);
        g.setClip(originalClip);
        return totalTime;
    }

    function getDispArrival(time, flag) {
        if (time <= 2) return flag ? "已经到达" : "Arrived";
        if (time <= 20) return flag ? "即将进站" : "Arriving";
        if (time <= 60) return String(time) + (flag ? " 秒" : " sec");
        if (time <= 3600) return String(parseInt(time / 60)) + (flag ? " 分" : " min");
        return String(parseInt(time / 3600)) + (flag ? " 时" : " hour");
    }
};
