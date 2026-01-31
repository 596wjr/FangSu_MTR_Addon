var draw = function (g, state, drawInfo, extraConfig) {
    // 获取动态分辨率参数
    var texArea = drawInfo.texArea;
    var x = texArea[0];
    var y = texArea[1];
    var w = texArea[2];
    var h = texArea[3];
    var ctx = drawInfo.ctx;
    var block = drawInfo.block;

    // 原始设计尺寸
    var baseWidth = 256;
    var baseHeight = 128;

    // 计算缩放比例
    var scaleX = w / baseWidth;
    var scaleY = h / baseHeight;
    var scaleFactor = Math.min(scaleX, scaleY);

    // 缩放辅助函数
    function scaleVal(value, isFont) {
        return isFont ? value * scaleFactor : value * scaleX;
    }

    // 绘制背景图（适配动态分辨率）
    g.drawImage(loadRes(res, "img", "fangsu:pids/jcm/base.png"), x, y, w, h, null);

    // 状态初始化
    if (typeof state.drawBeginTime === "undefined" || typeof state.drawFlag === "undefined") {
        state.drawBeginTime = Timing.elapsed();
        state.drawFlag = true;
    }

    // 动态字体大小
    var baseFont = loadRes(res, "font", "mtrsteamloco:fonts/source-han-sans-bold.otf");
    var fontSize = scaleVal(16, true);
    var font = baseFont.deriveFont(fontSize);

    // 天气图标（动态位置和尺寸）
    var weatherIconSize = scaleVal(16, false);
    var weatherIcon = MinecraftClient.worldIsRainingAt(block.getWorldPosVector3f()) ? "fangsu:pids/jcm/weather_rainy.png" : "fangsu:pids/jcm/weather_sunny.png";
    g.drawImage(loadRes(res, "img", weatherIcon), x, y, weatherIconSize, weatherIconSize, null);

    // 时间显示（动态位置）
    var timeFont = baseFont.deriveFont(scaleVal(12, true));
    g.setColor(Color.WHITE);
    g.setFont(timeFont);
    var timeStr = getWorldTime().h + ":" + getWorldTime().m;
    var timeStrWidth = g.getFontMetrics(timeFont).stringWidth(timeStr);
    g.drawString(timeStr, x + w - timeStrWidth - scaleVal(8, false), y + scaleVal(14, false));

    // 动态行高和位置
    var lineHeight = scaleVal(28, false);
    var textStartY = y + scaleVal(36, false);
    var textStartX = x + scaleVal(4, false);
    var textMaxWidth = scaleVal(150, false);
    var arrivalTextX = x + w - scaleVal(58, false);

    // 绘制列车信息
    var drawTotalTime = 5;
    for (var i = 0; i < Math.min(4, drawInfo.arrivalInfoList.length); i++) {
        g.setColor(Color.BLACK);
        var arrivalInfo = drawInfo.arrivalInfoList[i];
        var arrivingTime = parseInt((arrivalInfo.arrivalMillis - Date.now()) / 1000);
        var currentY = textStartY + lineHeight * i;

        if (state.drawFlag) {
            g.setFont(font);
            drawTotalTime = Math.max(drawTotalTime, drawScrollText(TextUtil.getCjkParts(arrivalInfo.destination), textMaxWidth, textStartX, currentY, font, state.drawBeginTime, scaleFactor));
            var arrivalStr = getDispArrival(arrivingTime, true);
            g.drawString(arrivalStr, arrivalTextX - g.getFontMetrics(font).stringWidth(arrivalStr), currentY);
        } else {
            g.setFont(font);
            drawTotalTime = Math.max(drawTotalTime, drawScrollText(TextUtil.getNonCjkParts(arrivalInfo.destination), textMaxWidth, textStartX, currentY, font, state.drawBeginTime, scaleFactor));
            var arrivalStr = getDispArrival(arrivingTime, false);
            g.drawString(arrivalStr, arrivalTextX - g.getFontMetrics(font).stringWidth(arrivalStr), currentY);
        }
    }

    // 状态更新
    if (state.drawBeginTime + drawTotalTime < Timing.elapsed()) {
        state.drawBeginTime = Timing.elapsed();
        state.drawFlag = !state.drawFlag;
    }

    // 调试信息
    ctx.setDebugInfo(
        "drawinfo",
        "Resolution: " + w + "x" + h + " | Scale: " + scaleFactor.toFixed(2) + "\nDrawTime: " + drawTotalTime + "s | Time: " + Timing.elapsed().toFixed(1) + "\nFlag: " + state.drawFlag
    );

    // 辅助函数 - 滚动文本
    function drawScrollText(str, maxX, xPos, yPos, font, beginTime, scale) {
        g.setFont(font);
        var metrics = g.getFontMetrics(font);
        var strWidth = metrics.stringWidth(str);

        if (strWidth <= maxX) {
            g.drawString(str, xPos, yPos);
            return 0;
        }

        var originalClip = g.getClip();
        var totalTextLength = strWidth + maxX;
        var speed = maxX * 0.25;
        var totalTime = parseInt(totalTextLength / speed);
        var clipHeight = metrics.getHeight() + scaleVal(20, false);

        g.setClip(new java.awt.Rectangle(xPos, yPos - metrics.getHeight() - scaleVal(10, false), maxX, clipHeight));

        g.drawString(str, xPos + maxX - ((Timing.elapsed() - beginTime - 0.1) % totalTime) * speed, yPos);

        g.setClip(originalClip);
        return totalTime;
    }

    // 辅助函数 - 到达时间显示
    function getDispArrival(time, flag) {
        if (time <= 2) return flag ? "已经到达" : "Arrived";
        else if (time <= 20) return flag ? "即将进站" : "Arriving";
        else if (time <= 60) return String(time) + (flag ? " 秒" : " sec");
        else if (time <= 3600) return String(parseInt(time / 60)) + (flag ? " 分" : " min");
        return String(parseInt(time / 3600)) + (flag ? " 时" : " hour");
    }

    // 辅助函数 - 获取游戏内时间
    function getWorldTime() {
        var totalTicks = MinecraftClient.worldDayTime();
        var hours = (parseInt(totalTicks / 1000) + 6) % 24;
        var minutes = parseInt((totalTicks % 1000) / 16.67);

        return {
            h: hours < 10 ? "0" + hours : String(hours),
            m: minutes < 10 ? "0" + minutes : String(minutes)
        };
    }
};
