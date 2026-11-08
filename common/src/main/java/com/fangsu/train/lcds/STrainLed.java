package com.fangsu.train.lcds;

import com.fangsu.mtr.DrawableRoute;
import com.fangsu.scripting.TextUtil;
import com.fangsu.train.DoorOpenRecord;
import com.fangsu.train.LcdBase;
import com.fangsu.train.LcdInfo;
import com.fangsu.train.TrainStatus;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** S-Train 印刷底板 + 独立自发光 LED。实例和 state 均归本列车所有。 */
public final class STrainLed extends LcdBase {
    private static final Color RED = new Color(255, 35, 40);
    private static final Color GREEN = new Color(35, 255, 65);
    private Font font;
    private long fontVersion = -1;

    private static final class Board {
        final List<String> names;
        final boolean mirrored;
        final BufferedImage image;
        final int color;
        final long resourceVersion = ResourceUtil.getReloadVersion();
        Board(List<String> names, boolean mirrored, BufferedImage image, int color) {
            this.names = names;
            this.mirrored = mirrored;
            this.image = image;
            this.color = color;
        }
        int position(int index) { return mirrored ? names.size() - 1 - index : index; }
    }

    @Override
    public boolean canDrawWithoutRoute() {
        return true;
    }

    @Override
    public void draw(Graphics2D graphics, TrainStatus status, LcdInfo info, Map<String, Object> state,
                     String side, int x, int y, int w, int h, Runnable callback) {
        final Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.clipRect(x, y, w, h);
            g.translate(x, y);
            // 两版入口都可能复用纹理；灯灭时必须清除上一帧，不能留下亮灯残影。
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            final boolean light = side.endsWith("_light");
            final boolean left = side.contains("left");
            final String key = "s_train_board_" + (left ? "left" : "right");
            if (font == null || fontVersion != ResourceUtil.getReloadVersion()) {
                font = ResourceUtil.loadFont(new ResourceLocation("mtr:font/noto-serif-cjk-tc-semibold.ttf"));
                fontVersion = ResourceUtil.getReloadVersion();
            }
            Board board = (Board) state.get(key);
            if (board != null && (board.resourceVersion != fontVersion
                    || board.image.getWidth() != w || board.image.getHeight() != h)) {
                board = createBoard(board.names, board.mirrored, w, h, new Color(board.color, true));
                state.put(key, board);
            }
            // 无线路、运行结束或换线期间保留最后的印刷线路；灯层已清空，不能沿用上一帧亮灯。
            if (status.drawableRoute == null || status.currentRoute == null
                    || status.trainStatus == 0 || status.trainStatus == 5 || status.trainStatus == 6) {
                if (!light) {
                    if (board != null) {
                        g.drawImage(board.image, 0, 0, null);
                    } else {
                        // 从未加载过线路的新列车才显示空白底板。
                        g.setColor(new Color(238, 235, 222));
                        g.fillRect(0, 0, w, h);
                    }
                }
                return;
            }
            List<DrawableRoute.DrawableRouteStation> stations = status.drawableRoute.getStations(status.getThisRoutePlatformsNextIndexGlobal());
            List<String> names = new ArrayList<>();
            for (DrawableRoute.DrawableRouteStation station : stations) names.add(station.stationName);
            boolean reversed = false;
            if (board != null && !board.names.equals(names)) {
                List<String> backwards = new ArrayList<>(names);
                Collections.reverse(backwards);
                reversed = board.names.equals(backwards);
                if (!reversed) board = null;
            }
            // 相同站序或完整反向站序沿用原印刷排列；真正换线/改站名才重建底板。
            if (board == null || board.image.getWidth() != w || board.image.getHeight() != h
                    || board.color != status.drawableRoute.routeColor.getRGB()) {
                board = createBoard(names, left == status.isReverse, w, h, status.drawableRoute.routeColor);
                state.put(key, board);
                reversed = false;
            }
            if (!light) {
                g.drawImage(board.image, 0, 0, null);
                return;
            }
            // 等待发车、出入库均不点灯；仅正线运行和到站时启用站灯、箭头及落车提示。
            if (status.trainStatus != 3 && status.trainStatus != 4) return;
            final boolean blink = (System.currentTimeMillis() / 500 % 2) == 0;
            // 站台轨道上仍在行驶不能算到站，开门/停车后才切换到站灯态。
            final boolean arrived = status.doorValue() > 0.01
                    || (status.trainStatus == 4 && Math.abs(status.speed()) < 0.01);
            int current = -1;
            for (int i = 0; i < stations.size(); i++) {
                int passing = stations.get(i).passingStatus;
                int fixed = reversed ? stations.size() - 1 - i : i;
                int position = board.position(fixed);
                if (passing == 2) current = i;
                if (passing == 3 || (passing == 2 && (arrived || blink))) {
                    g.setColor(RED);
                    lamp(g, stationX(position, stations.size(), w), h * .58, lampRadius(stations.size(), w, h), true);
                }
            }
            // 到站闪「本站→下站」，运行常亮「上站→下站」。终点不画越界箭头。
            int from = arrived ? current : current - 1;
            int to = arrived ? current + 1 : current;
            if (current >= 0 && from >= 0 && to < stations.size() && (!arrived || blink)) {
                int a = board.position(reversed ? stations.size() - 1 - from : from);
                int b = board.position(reversed ? stations.size() - 1 - to : to);
                g.setColor(GREEN);
                double direction = Math.signum(b - a);
                double r = arrowRadius(stations.size(), w, h);
                arrow(g, arrowX(stationX(a, stations.size(), w), stationX(b, stations.size(), w), direction),
                        h * .58, direction, r, true);
            }
            // 开门提示使用 TrainStatus 已换算到物理侧的观测/预测；未知时不猜左右。
            boolean known = false, onThisSide = false;
            for (int car = 0; car < status.doorOpenSides.length; car++) {
                DoorOpenRecord.DoorOpenSide open = status.getDoorOpenSide(car);
                if (open == DoorOpenRecord.DoorOpenSide.UNKNOWN || open == DoorOpenRecord.DoorOpenSide.NONE) continue;
                known = true;
                onThisSide = open == DoorOpenRecord.DoorOpenSide.BOTH
                        || (left ? open == DoorOpenRecord.DoorOpenSide.LEFT : open == DoorOpenRecord.DoorOpenSide.RIGHT);
                break;
            }
            if (known && (arrived || blink)) {
                g.setColor(onThisSide ? GREEN : RED);
                // 素材右上角是同一行的两枚圆灯：左为另一边，右为这边。
                exitLamp(g, w, h, onThisSide, true);
            }
        } finally {
            g.dispose();
            callback.run();
        }
    }

    private Board createBoard(List<String> names, boolean mirrored, int w, int h, Color routeColor) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Board board = new Board(new ArrayList<>(names), mirrored, image, routeColor.getRGB());
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(238, 235, 222));
            g.fillRect(0, 0, w, h);
            g.setColor(routeColor);
            g.setStroke(new BasicStroke((float) (h * .055)));
            if (names.size() > 1) g.drawLine((int) stationX(0, names.size(), w), (int) (h * .58),
                    (int) stationX(names.size() - 1, names.size(), w), (int) (h * .58));
            for (int i = 0; i < names.size(); i++) {
                int p = board.position(i);
                double cx = stationX(p, names.size(), w);
                double cell = w * .88 / Math.max(1, names.size());
                g.setColor(new Color(40, 40, 35));
                withStretch(g, TextUtil.getCjkMatching(names.get(i), true), cx - cell * .46, h * .31, cell * .92, h * .105, h * .1);
                withStretch(g, TextUtil.getCjkMatching(names.get(i), false), cx - cell * .46, h * .425, cell * .92, h * .07, h * .062);
                lamp(g, cx, h * .58, lampRadius(names.size(), w, h), false);
                if (p + 1 < names.size()) {
                    double nextX = stationX(p + 1, names.size(), w);
                    double r = arrowRadius(names.size(), w, h);
                    // 素材中每段的 1/3 处朝右、2/3 处朝左，未点亮的黑边白底箭头常驻底板。
                    g.setColor(Color.BLACK);
                    arrow(g, arrowX(cx, nextX, 1), h * .58, 1, r, false);
                    arrow(g, arrowX(cx, nextX, -1), h * .58, -1, r, false);
                }
            }
            // 按素材并排放在右上角：圆灯在左，中英文印刷文字在右；文字不随灯闪烁。
            drawExitLegend(g, w, h, false);
            drawExitLegend(g, w, h, true);
        } finally { g.dispose(); }
        return board;
    }

    private void drawExitLegend(Graphics2D g, int w, int h, boolean thisSide) {
        g.setColor(new Color(105, 95, 88));
        exitLamp(g, w, h, thisSide, false);
        double textX = w * (thisSide ? .911 : .796);
        double textW = w * (thisSide ? .081 : .094);
        withStretch(g, thisSide ? "請在這邊落車" : "請在另一邊落車",
                textX, h * .052, textW, h * .065, h * .055);
        withStretch(g, thisSide ? "Please exit from this side" : "Please exit from the other side",
                textX, h * .122, textW, h * .044, h * .036);
    }

    /** 底板和发光层共用坐标，确保圆灯始终准确覆盖印刷灯位。 */
    private static void exitLamp(Graphics2D g, int w, int h, boolean thisSide, boolean light) {
        lamp(g, w * (thisSide ? .9 : .785), h * .105, h * .028, light);
    }

    /** 每个控件在自己的矩形内水平压缩；裁剪和变换绝不泄漏到邻站。 */
    private void withStretch(Graphics2D parent, String text, double x, double y, double w, double h, double size) {
        if (text == null || text.isEmpty() || w <= 0 || h <= 0) return;
        Graphics2D g = (Graphics2D) parent.create();
        try {
            g.clip(new java.awt.geom.Rectangle2D.Double(x, y, w, h));
            g.setFont(font.deriveFont((float) size));
            FontMetrics fm = g.getFontMetrics();
            double scale = Math.min(1, w / Math.max(1, fm.stringWidth(text)));
            g.translate(x + (w - fm.stringWidth(text) * scale) / 2, y);
            g.scale(scale, 1);
            g.drawString(text, 0, (float) ((h - fm.getHeight()) / 2 + fm.getAscent()));
        } finally { g.dispose(); }
    }

    private static double stationX(int index, int count, int width) {
        return count <= 1 ? width * .5 : width * (.06 + .88 * index / (count - 1));
    }
    private static double lampRadius(int count, int w, int h) {
        return Math.min(h * .034, w * .88 / Math.max(1, count) * .17);
    }
    private static double arrowRadius(int count, int w, int h) {
        return Math.min(h * .024, w * .88 / Math.max(1, count) * .065);
    }
    /** 底板与灯层共用三等分灯位，反向行车只切换点亮的箭头。 */
    private static double arrowX(double a, double b, double direction) {
        double left = Math.min(a, b);
        return left + Math.abs(b - a) * (direction > 0 ? 1.0 / 3 : 2.0 / 3);
    }
    private static void lamp(Graphics2D g, double x, double y, double r, boolean light) {
        double outer = r + 2;
        drawIndicator(g, new java.awt.geom.Ellipse2D.Double(x - outer, y - outer, outer * 2, outer * 2), 2, light);
    }
    private static void arrow(Graphics2D g, double x, double y, double direction, double r, boolean light) {
        java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
        // 底板和灯层共用带矩形箭杆的封闭形状。
        p.moveTo(x - direction * r, y - r * .28);
        p.lineTo(x, y - r * .28);
        p.lineTo(x, y - r * .8);
        p.lineTo(x + direction * r, y);
        p.lineTo(x, y + r * .8);
        p.lineTo(x, y + r * .28);
        p.lineTo(x - direction * r, y + r * .28);
        p.closePath();
        drawIndicator(g, p, Math.min(.8, r * .12), light);
    }

    /** 底板为黑边白底，发光层只绘制同一个内区，绝不覆盖黑边。 */
    private static void drawIndicator(Graphics2D g, Shape outline, double borderWidth, boolean light) {
        java.awt.geom.Area fill = new java.awt.geom.Area(outline);
        Shape border = new BasicStroke((float) (borderWidth * 2), BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER).createStrokedShape(outline);
        fill.subtract(new java.awt.geom.Area(border));
        if (light) {
            g.fill(fill);
        } else {
            Color previous = g.getColor();
            g.setColor(Color.BLACK);
            g.fill(outline);
            g.setColor(Color.WHITE);
            g.fill(fill);
            g.setColor(previous);
        }
    }
}
