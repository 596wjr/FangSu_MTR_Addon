package com.fangsu.train.lcds;

import com.fangsu.mtr.ColorNameTuple;
import com.fangsu.mtr.DrawableRoute;
import com.fangsu.scripting.G2dTextHelper;
import com.fangsu.scripting.TextUtil;
import com.fangsu.train.LcdBase;
import com.fangsu.train.LcdInfo;
import com.fangsu.train.TrainStatus;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class MtrLcd extends LcdBase {
    @Override
    public void draw(Graphics2D g, TrainStatus status, LcdInfo info, Map<String, Object> state, String side, int x, int y, int w, int h, Runnable callback) {
        Font cjkFont = ResourceUtil.loadFont(new ResourceLocation("mtr:font/noto-serif-cjk-tc-semibold.ttf"));
        Font nonCjkFont = ResourceUtil.loadFont(new ResourceLocation("mtr:font/noto-sans-semibold.ttf"));

        if (status.currentRoute == null || status.drawableRoute == null) {
            g.setColor(Color.WHITE);
            g.fillRect(x, y, w, h);
            g.setColor(Color.BLACK);
            drawStationNameCenter(g, x, y, w, h, "无线路信息", "No route loaded", cjkFont, nonCjkFont);
            drawMindTheGap(g, x, y, w, h, cjkFont, nonCjkFont);
            callback.run();
            return;
        }
        g.setColor(Color.WHITE);
        g.fillRect(x, y, w, h);

        int nextStationIndex = status.getThisRoutePlatformsNextIndexGlobal();
        DrawableRoute route = status.drawableRoute;
        List<DrawableRoute.DrawableRouteStation> stations = route.getStations(nextStationIndex);

        if (status.trainStatus == 3 || status.trainStatus == 2 || status.trainStatus == 1) {
            boolean isLeft = side.contains("left");
            boolean isReverse = isLeft == status.isReverse;

            drawRoute(g, x, y, w, h, route, stations, cjkFont, nonCjkFont, isReverse);
            drawRouteName(g, x, y, w, h, cjkFont, nonCjkFont, route.routeColor, route.routeName, isLeft);

        } else if (status.trainStatus == 4) {
            DrawableRoute.DrawableRouteStation thisStation = null;
            for (DrawableRoute.DrawableRouteStation station : stations) {
                if (station.passingStatus == 2) thisStation = station;
            }
            if (thisStation != null) {
                String cjkName = TextUtil.getCjkMatching(thisStation.stationName, true);
                String nonCjkName = TextUtil.getCjkMatching(thisStation.stationName, false);
                drawStationNameCenter(g, x, y, w, h, cjkName, nonCjkName, cjkFont, nonCjkFont);
                drawMindTheGap(g, x, y, w, h, cjkFont, nonCjkFont);

                boolean leftOpen = status.doorLeftOpen[0];
                boolean rightOpen = status.doorRightOpen[0];
                boolean isLeft = side.contains("left");
                if (leftOpen || rightOpen) {
                    boolean doorOnRight = (isLeft && rightOpen) || (!isLeft && leftOpen);
                    boolean doorOnThisSide = (isLeft && leftOpen) || (!isLeft && rightOpen);
                    drawDoorOpen(g, x, y, w, h, cjkFont, nonCjkFont, doorOnRight, doorOnThisSide);
                }
            } else {
                boolean isLeft = side.contains("left");
                boolean isReverse = isLeft == status.isReverse;
                drawRoute(g, x, y, w, h, route, stations, cjkFont, nonCjkFont, isReverse);
                drawRouteName(g, x, y, w, h, cjkFont, nonCjkFont, route.routeColor, route.routeName, isLeft);
            }
        } else {
            g.setColor(Color.BLACK);
            g.drawString("state: " + status.trainStatus, x + 20, y + 20);
            drawStationNameCenter(g, x, y, w, h, "方速MTR扩展", "FangSu MTR Addon", cjkFont, nonCjkFont);
            drawMindTheGap(g, x, y, w, h, cjkFont, nonCjkFont);
        }
//        g.setColor(Color.BLACK);
//        g.drawString("side: " + side, x + 20, y + 60);
//        g.drawString("is_reversed: " + (status.isReverse ? "true" : "false"), x + 20, y + 80);

        callback.run();
    }

    private void drawRoute(Graphics2D g, int x, int y, int w, int h, DrawableRoute route, List<DrawableRoute.DrawableRouteStation> stations,
                           Font cjkFont, Font nonCjkFont, boolean isReverse) {
        Color routeColor = route.routeColor;
        Color passedColor = Color.GRAY;
        Color blinkColor = Color.decode("0xffcd00");
        boolean blinkState = System.currentTimeMillis() / 1000 % 2 == 0;

        int distant = stations.size() <= 1 ? w : (int) (w * 0.8 / (stations.size() - 1));
        int centralY = y + h / 2;
        int lineSize = h / 11;
        int stationSize = h / 8;
        int interchangeHeight = h / 6;
        int interchangeWidth = h / 8;


        //draw line
        for (int i = 0; i < stations.size(); i++) {
            if (i == 0) continue;
            DrawableRoute.DrawableRouteStation station = stations.get(i);
            int currentX = (int) (x + (isReverse ? w * 0.9 : w * 0.1) + (isReverse ? -1 : 1) * distant * i);
            if (station.passingStatus == 2 || station.passingStatus == 3) {
                g.setColor(routeColor);
            } else g.setColor(passedColor);
            g.fillRect(currentX - (isReverse ? 0 : distant) - 1, centralY - lineSize / 2, distant + 2, lineSize);

            //draw arrow
            if (station.passingStatus == 2) {
                int arrowPos = currentX + (isReverse ? distant / 2 : -distant / 2);
                int arrowSize = lineSize / 2;

                int arrowLeft = isReverse ? -1 : 1;
                int[] baseArrowXPoints = new int[]{
                        arrowPos - arrowSize * arrowLeft, arrowPos + arrowSize / 2 * arrowLeft, arrowPos, arrowPos + arrowSize / 2 * arrowLeft, arrowPos + arrowSize * arrowLeft,
                        arrowPos + arrowSize / 2 * arrowLeft, arrowPos, arrowPos + arrowSize / 2 * arrowLeft, arrowPos - arrowSize * arrowLeft
                };
                int[] baseArrowYPoints = new int[]{
                        centralY - arrowSize / 4, centralY - arrowSize / 4, centralY - arrowSize / 2, centralY - arrowSize / 2, centralY,
                        centralY + arrowSize / 2, centralY + arrowSize / 2, centralY + arrowSize / 4, centralY + arrowSize / 4
                };


                Polygon arrow = new Polygon(
                        baseArrowXPoints, baseArrowYPoints, 9
                );

                g.setColor(Color.black);
                g.setStroke(new BasicStroke(arrowSize / 4f));
                g.drawPolygon(arrow);
                g.setColor(blinkState ? blinkColor : Color.WHITE);
                g.fillPolygon(arrow);
            }
        }

        //draw station
        for (int i = 0; i < stations.size(); i++) {
            DrawableRoute.DrawableRouteStation station = stations.get(i);
            boolean hasPassed = station.passingStatus < 2;
            boolean isInRoute = !(station.passingStatus == 0 || station.passingStatus == 4);
            int currentX = (int) (x + (isReverse ? w * 0.9 : w * 0.1) + (isReverse ? -1 : 1) * distant * i);
            if (!station.transInfo.isEmpty() && !hasPassed) {
                if (station.transInfo.size() == 1) {
                    var thisTrans = station.transInfo.get(0);
                    g.setColor(isInRoute ? thisTrans.routeColor : passedColor);
                    g.fillRoundRect(currentX - lineSize / 2, centralY - interchangeHeight, lineSize, interchangeHeight, lineSize, lineSize);
                    g.setColor(isInRoute ? Color.BLACK : passedColor);
                    int textWidth = G2dTextHelper.getMultiLinesWidth(g, cjkFont, nonCjkFont, stationSize, thisTrans.routeName.split("\\|"));
                    G2dTextHelper.drawStrMultiLines(g, cjkFont, nonCjkFont, currentX - textWidth / 2, centralY - interchangeHeight - stationSize - lineSize / 5 - stationSize, stationSize, 1, thisTrans.routeName.split("\\|"));
                } else {
                    for (int j = 0; j < station.transInfo.size(); j++) {
                        ColorNameTuple thisTrans = station.transInfo.get(j);
                        int currentY = (int) (centralY - lineSize * 2 - lineSize * 1.5 * j);
                        g.setColor(isInRoute ? thisTrans.routeColor : passedColor);
                        g.fillRoundRect(currentX, currentY, interchangeWidth, lineSize, lineSize, lineSize);
                        g.setColor(isInRoute ? Color.BLACK : passedColor);
                        G2dTextHelper.drawStrMultiLines(g, cjkFont, nonCjkFont, currentX + interchangeWidth, currentY - lineSize, lineSize, 0, thisTrans.routeName.split("\\|"));
                    }
                    int stationHeight = (int) (lineSize * 0.5 + lineSize * 1.5 * station.transInfo.size());
                    g.setColor(!isInRoute ? passedColor : routeColor);
                    g.fillRoundRect(currentX - stationSize / 2, centralY - stationSize / 2 - stationHeight, stationSize, stationHeight + stationSize, stationSize, stationSize);
                    g.setColor(station.passingStatus == 2 ? blinkState ? blinkColor : Color.WHITE : Color.WHITE);
                    g.fillRoundRect(currentX - lineSize / 2, centralY - lineSize / 2 - stationHeight, lineSize, stationHeight + lineSize, stationSize, stationSize);
                }
            }
            if (station.transInfo.size() <= 1) {
                g.setColor(hasPassed || !isInRoute ? passedColor : routeColor);
                g.fillOval(currentX - stationSize / 2, centralY - stationSize / 2, stationSize, stationSize);
                g.setColor(station.passingStatus == 2 ? blinkState ? blinkColor : Color.WHITE : Color.WHITE);
                g.fillOval(currentX - lineSize / 2, centralY - lineSize / 2, lineSize, lineSize);
            }
            g.setColor(hasPassed || !isInRoute ? passedColor : Color.BLACK);
            int stationNameWidth = G2dTextHelper.getMultiLinesWidth(g, cjkFont, nonCjkFont, lineSize * 2, station.stationName.split("\\|"));
            G2dTextHelper.drawStrMultiLines(g, cjkFont, nonCjkFont, currentX - stationNameWidth / 2, (int) (centralY + lineSize * 2.5) - lineSize * 2, lineSize * 2, 1, station.stationName.split("\\|"));
        }

        //draw route name


    }

    private void drawRouteName(Graphics2D g, int x, int y, int w, int h, Font cjkFont, Font nonCjkFont, Color routeColor, String routeName, boolean onRight) {
        int routeNameTextHeight = h / 8;
        int routeNameColorHeight = h / 12;
        int routeNameColorWidth = h / 6;
        int routeNameBlank = (int) (h * 0.05);
        int centralY = y + h / 15;

        int routeNameTextWidth = G2dTextHelper.getMultiLinesWidth(g, cjkFont, nonCjkFont, routeNameTextHeight, routeName.split("\\|"));
        int currentX = x + (onRight ? w - routeNameBlank * 2 - routeNameTextWidth - routeNameColorWidth : routeNameBlank);

        g.setColor(routeColor);
        g.fillRoundRect(currentX, centralY - routeNameColorHeight / 2, routeNameColorWidth, routeNameColorHeight, routeNameColorHeight, routeNameColorHeight);
        currentX += routeNameColorWidth;
        currentX += routeNameBlank;
        g.setColor(Color.BLACK);
        G2dTextHelper.drawStrMultiLines(g, cjkFont, nonCjkFont, currentX, centralY - routeNameTextHeight, routeNameTextHeight, 0, routeName.split("\\|"));

    }

    private void drawDoorOpen(Graphics2D g, int x, int y, int w, int h, Font cjkFont, Font nonCjkFont, boolean onRight, boolean isOpen) {
        String cjkText = isOpen ? "请在这边落车" : "请在另一边落车";
        String nonCjkText = isOpen ? "Please exit this side" : "Please exit from the opposite";
        Color blinkColor = isOpen ? Color.GREEN : Color.decode("0xffcd00");
        int doorOpenHeight = h / 7;
        int textHeight = doorOpenHeight / 10 * 8;
        int textWidth = G2dTextHelper.getMultiLinesWidth(g, cjkFont, nonCjkFont, textHeight, cjkText, nonCjkText);
        int blankWidth = doorOpenHeight / 10;
        boolean blinkState = System.currentTimeMillis() / 1000 % 2 == 0;

        int currentX = x + (onRight ? w - blankWidth * 3 - textWidth : blankWidth);
        if (!blinkState) {
            g.setColor(blinkColor);
            g.fillRoundRect(currentX, y + h / 20, textWidth + blankWidth * 2, doorOpenHeight, doorOpenHeight, doorOpenHeight);
        }
        g.setColor(Color.BLACK);
        G2dTextHelper.drawStrMultiLines(g, cjkFont, nonCjkFont, currentX + blankWidth, y + h / 20 + (doorOpenHeight - textHeight) / 2 - textHeight, textHeight, 1, cjkText, nonCjkText);
    }

    private void drawStationNameCenter(Graphics2D g, int x, int y, int w, int h, String cjkName, String nonCjkName, Font cjkFont, Font nonCjkFont) {
        boolean blinkState = System.currentTimeMillis() / 1000 % 2 == 0;

        int generalSize = (int) (h * 0.25f);
        int generalSizeSmall = (int) (generalSize * 0.8f);
        int deltaSize = generalSize - generalSizeSmall;

        int commonY = y + (h / 5 * 4) / 2 - generalSize / 2;
        int smallY = y + (h / 5 * 4) / 2 - generalSizeSmall / 2;
        int gap = h / 10;

        if (cjkName.isEmpty() && nonCjkName.isEmpty()) {
            cjkName = "未命名";
            nonCjkName = "Undefined";
        }
        if (cjkName.isEmpty() || nonCjkName.isEmpty()) {
            String line = cjkName + nonCjkName;
            g.setColor(Color.BLACK);
            g.fillOval(x + w / 10, commonY, generalSize, generalSize);
            g.setColor(blinkState ? Color.WHITE : Color.decode("0xffcd00"));
            g.fillOval(x + w / 10 + deltaSize / 2, smallY, generalSizeSmall, generalSizeSmall);

            g.setColor(Color.BLACK);
            G2dTextHelper.drawStrUnified(g,
                    (TextUtil.isCjk(line) ? cjkFont : nonCjkFont),
                    line, x + w / 10 + gap + generalSize, commonY + generalSize, generalSize, 0);
            return;
        }
        int cjkWidth = G2dTextHelper.getUnifiedStringWidth(g, cjkFont, cjkName, generalSize);
        int nonCjkWidth = G2dTextHelper.getUnifiedStringWidth(g, nonCjkFont, nonCjkName, generalSizeSmall);

        int currentX = x + w / 2 - (cjkWidth + nonCjkWidth + generalSize + gap * 2) / 2;
        g.setColor(Color.BLACK);
        currentX += G2dTextHelper.drawStrUnified(g, cjkFont, cjkName, currentX, commonY + generalSize, generalSize, 0);
        currentX += gap;

        g.setColor(Color.BLACK);
        g.fillOval(currentX, commonY, generalSize, generalSize);
        g.setColor(blinkState ? Color.WHITE : Color.decode("0xffcd00"));
        g.fillOval(currentX + deltaSize / 2, smallY, generalSizeSmall, generalSizeSmall);
        currentX += generalSize;

        g.setColor(Color.BLACK);
        currentX += gap;
        currentX += G2dTextHelper.drawStrUnified(g, cjkFont, nonCjkName, currentX, smallY + generalSizeSmall, generalSizeSmall, 0);

    }

    private void drawMindTheGap(Graphics2D g, int x, int y, int w, int h, Font cjkFont, Font nonCjkFont) {
        BufferedImage imgMindTheGapLeft, imgMindTheGapRight;
        try {
            imgMindTheGapLeft = ResourceUtil.loadImage(new ResourceLocation("fangsu:lcd/mtr_mind_the_gap_1.png"));
            imgMindTheGapRight = ResourceUtil.loadImage(new ResourceLocation("fangsu:lcd/mtr_mind_the_gap_2.png"));
        } catch (IOException e) {
            imgMindTheGapLeft = null;
            imgMindTheGapRight = null;
        }

        // 底部区域起始比例（0.0 ~ 1.0）
        final float bottomAreaStartRatio = 0.75f;
        // 文字在底部区域内的纵向偏移比例（相对于该区域高度）
        final float textTopOffsetRatio = 0.15f;

        int bottomY = y + (int) (h * bottomAreaStartRatio);
        int bottomHeight = y + h - bottomY;

        // 绘制底部背景色
        g.setColor(Color.decode("0xffcd00"));
        g.fillRect(x, bottomY, w, bottomHeight + 1); // +1 防止因取整造成的接缝

        int generalSize = (int) (h * 0.15f);
        int cjkWidth = G2dTextHelper.getUnifiedStringWidth(g, cjkFont, "请小心月台空隙", generalSize);
        int nonCjkWidth = G2dTextHelper.getUnifiedStringWidth(g, nonCjkFont, "Please mind the gap", generalSize);
        int gapWidth = (int) (h * 0.05f);
        int totalWidth = generalSize + cjkWidth + nonCjkWidth + gapWidth * 3;

        int currentX = w / 2 - totalWidth / 2;
        int currentY = bottomY + (int) (bottomHeight * textTopOffsetRatio); // 等效于 y + h * 0.825f

        g.setColor(Color.BLACK);
        if (imgMindTheGapLeft != null)
            g.drawImage(imgMindTheGapLeft, currentX, currentY, generalSize, generalSize, null);
        currentX += generalSize;
        currentX += gapWidth;
        currentX += G2dTextHelper.drawStrUnified(g, cjkFont, "请小心月台空隙", currentX, currentY + generalSize, generalSize, 0);
        currentX += gapWidth;
        currentX += G2dTextHelper.drawStrUnified(g, nonCjkFont, "Please mind the gap", currentX, currentY + generalSize, generalSize, 0);
        currentX += gapWidth;
        if (imgMindTheGapRight != null)
            g.drawImage(imgMindTheGapRight, currentX, currentY, generalSize, generalSize, null);
    }
}
