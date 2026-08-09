// 方速列车 LCD 示例脚本（两版通用）
// 挂载方式：mtr_custom_resources.json 的 custom_trains[].lcd 中添加 script 字段：
//   "lcd": { "id": "mtr", "slots": "mtr:r_train/slots.json",
//            "script": "fangsu:lcd/r_train_lcd.js",
//            "extraConfig": { "font": "fangsu:fonts/ae.ttf" } }
//
// 入口函数签名（与 PIDS 脚本同风格）：
//   function draw(g, state, trainStatus, info, extraConfig)
//   g            : java.awt.Graphics2D（整张 LCD 纹理，本次调用需画满）
//   state        : 每列车持久的状态 Map（跨帧，可缓存字体/时间）
//   trainStatus  : com.fangsu.train.TrainStatus（公共字段/方法直读）
//   info         : { id, script, texSize: [w,h], slots: [{name, texArea: [x,y,w,h]}] }
//   extraConfig  : lcd.extraConfig 配置（缺省 {}）

function draw(g, state, trainStatus, info, extraConfig) {
    var texSize = info.texSize;
    var width = texSize[0];
    var height = texSize[1];

    // 白色背景打底
    g.setColor(rgbToColor(255, 255, 255));
    g.fillRect(0, 0, width, height);

    // 字体惰性加载（跨帧缓存到 state）
    var fontSize = 28;
    if (state.font === undefined || state.fontSize !== fontSize) {
        var fontPath = (extraConfig && extraConfig.font) ? extraConfig.font : "fangsu:fonts/ae.ttf";
        state.font = loadResource("font", fontPath).deriveFont(fontSize);
        state.fontSize = fontSize;
    }
    var font = state.font;
    var smallFont = font.deriveFont(fontSize * 0.6);

    var marginX = Math.round(width * 0.04);
    var textY = Math.round(height * 0.2);
    g.setColor(rgbToColor(0, 0, 0));

    // 无线路信息
    if (trainStatus.currentRoute === null || trainStatus.drawableRoute === null) {
        drawStrUnified(g, font, "无线路信息", marginX, textY, fontSize, 0);
        drawStrUnified(g, smallFont, "No route loaded", marginX, textY + Math.round(fontSize * 1.5), Math.round(fontSize * 0.6), 0);
        return;
    }

    // 线路名 + 线路色条
    var route = trainStatus.drawableRoute;
    var colorBarX = marginX;
    var colorBarH = Math.round(fontSize * 1.3);
    g.setColor(route.routeColor);
    g.fillRoundRect(colorBarX, textY - colorBarH, colorBarH, colorBarH, colorBarH, colorBarH);
    drawStrUnified(g, font, route.routeName, colorBarX + colorBarH + 12, textY, fontSize, 0);

    // 下一站 / 当前站（passingStatus === 2 为下一站）
    var nextIndex = trainStatus.getThisRoutePlatformsNextIndexGlobal();
    var stations = route.getStations(nextIndex);
    var stationName = null;
    for (var i = 0; i < stations.size(); i++) {
        var station = stations.get(i);
        if (station.passingStatus === 2) {
            stationName = station.stationName;
            break;
        }
    }
    var y = textY + Math.round(fontSize * 2.2);
    if (stationName !== null) {
        var cjkName = TextUtil.getCjkMatching(stationName, true);
        var nonCjkName = TextUtil.getCjkMatching(stationName, false);
        drawStrUnified(g, font, cjkName, marginX, y, fontSize, 0);
        drawStrUnified(g, smallFont, nonCjkName, marginX, y + Math.round(fontSize * 1.4), Math.round(fontSize * 0.6), 0);
    } else {
        drawStrUnified(g, smallFont, "Departing...", marginX, y, Math.round(fontSize * 0.6), 0);
    }

    // 车速（speed() 单位为 m/s）
    y = Math.round(height * 0.68);
    drawStrUnified(g, font, Math.round(trainStatus.speed() * 3.6) + " km/h", marginX, y, fontSize, 0);

    // 车门状态
    var doorOpen = trainStatus.doorLeftOpen[0] || trainStatus.doorRightOpen[0];
    drawStrUnified(g, font, doorOpen ? "门开" : "门关", marginX + Math.round(width * 0.35), y, fontSize, 0);

    // 日期时间
    drawStrUnified(g, smallFont, formatDate(false) + " " + formatWeekday(false),
        marginX, Math.round(height * 0.85), Math.round(fontSize * 0.6), 0);
}
