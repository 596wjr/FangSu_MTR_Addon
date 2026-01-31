var draw = (g, state, drawInfo, extraConfig) => {
    var arrivalInfoList = drawInfo.arrivalInfoList;
    g.setColor(Color.BLACK);
    g.fillRect(drawInfo.texArea[0], drawInfo.texArea[1], drawInfo.texArea[2], drawInfo.texArea[3]);
    if (state.drawBeginTime === undefined || state.drawFlag === undefined) {
        state.drawBeginTime = Timing.elapsed();
        state.drawFlag = true;
    }
    var w = drawInfo.texArea[2] - drawInfo.texArea[0];
    var h = drawInfo.texArea[3] - drawInfo.texArea[1];
    var drawTotalTime = 5;
    var font = loadResource("font", "mtrsteamloco:fonts/ae.ttf").deriveFont(h * 0.25);
    for (var i = 0; i < Math.min(2, arrivalInfoList.length); i++) {
        g.setColor(Color.WHITE);
        var arrivingTime = parseInt((arrivalInfoList[i].arrivalMillis - Date.now()) / 1000);
        g.setColor(arrivingTime <= 20 ? rgbToColor(17, 170, 56) : rgbToColor(230, 91, 0));
        if ((state.drawFlag && hasCjkPart(arrivalInfoList[i].destination)) || (!state.drawFlag && !hasNonCjkPart(arrivalInfoList[i].destination))) {
            g.setFont(font);
            drawTotalTime = Math.max(drawTotalTime, drawScrollText(TextUtil.getCjkParts(arrivalInfoList[i].destination), 128, 4, h * 0.4 * (i + 1) + 2, font, state.drawBeginTime));
            g.drawString(getDispArrival(arrivingTime, true), 250 - g.getFontMetrics(font).stringWidth(getDispArrival(arrivingTime, true)), h * 0.4 * (i + 1) + 2);
        } else {
            g.setFont(font);
            drawTotalTime = Math.max(drawTotalTime, drawScrollText(TextUtil.getNonCjkParts(arrivalInfoList[i].destination), 128, 4, h * 0.4 * (i + 1) + 2, font, state.drawBeginTime));
            g.drawString(getDispArrival(arrivingTime, false), 250 - g.getFontMetrics(font).stringWidth(getDispArrival(arrivingTime, false)), h * 0.4 * (i + 1) + 2);
        }
    }
    if (state.drawBeginTime + drawTotalTime < Timing.elapsed()) {
        state.drawBeginTime = Timing.elapsed();
        state.drawFlag = !state.drawFlag;
    }

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
