function draw(g, state, drawInfo, extraConfig) {
    var arrivalInfoList = drawInfo.arrivalInfoList || [];
    var texArea = drawInfo.texArea;
    var width = texArea[2] - texArea[0];
    var height = texArea[3] - texArea[1];

    g.setColor(rgbToColor(0, 0, 0));
    g.fillRect(texArea[0], texArea[1], texArea[2], texArea[3]);

    if (state.drawBeginTime === undefined) state.drawBeginTime = Timing.elapsed();
    if (state.drawFlag === undefined) state.drawFlag = true;
    if (state.fontSize !== height * 0.25 || state.font === undefined) {
        state.fontSize = height * 0.25;
        state.font = loadResource("font", "fangsu:fonts/ae.ttf").deriveFont(state.fontSize);
    }

    var drawTotalTime = 5;
    var font = state.font;
    g.setFont(font);
    var metrics = g.getFontMetrics(font);
    var textRight = Math.max(0, width - 6);

    for (var i = 0; i < Math.min(2, arrivalInfoList.length); i++) {
        var arrivalInfo = arrivalInfoList[i];
        if (!arrivalInfo) continue;

        var arrivingTime = parseInt((arrivalInfo.arrivalMillis - Date.now()) / 1000);
        var isCjkPage = state.drawFlag;
        var destination = isCjkPage ? TextUtil.getCjkParts(arrivalInfo.destination) : TextUtil.getNonCjkParts(arrivalInfo.destination);
        if (!destination || destination.length === 0) {
            destination = arrivalInfo.destination;
        }

        g.setColor(arrivingTime <= 20 ? rgbToColor(17, 170, 56) : rgbToColor(230, 91, 0));
        var y = height * 0.4 * (i + 1) + 2;
        drawTotalTime = Math.max(drawTotalTime, drawScrollText(destination, 128, 4, y, metrics, state.drawBeginTime));

        var arrivalText = getDispArrival(arrivingTime, isCjkPage);
        g.drawString(arrivalText, textRight - metrics.stringWidth(arrivalText), y);
    }

    if (state.drawBeginTime + drawTotalTime < Timing.elapsed()) {
        state.drawBeginTime = Timing.elapsed();
        state.drawFlag = !state.drawFlag;
    }

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
}
