package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class DashboardRoutePanel {
    public interface Host {
        boolean fangsu$drawRoutePanel(Canvas canvas, int mouseX, int mouseY);
    }

    public interface Canvas {
        void fill(int left, int top, int right, int bottom, int color);
        void text(Component label, int x, int y, int color);
        void clip(int left, int top, int right, int bottom, Runnable draw);
        void icon(Icon icon, int x, int y, boolean hovered);
    }

    public enum Icon {
        EDIT("edit"), DELETE("delete");

        private final String textureName;

        Icon(String textureName) {
            this.textureName = textureName;
        }

        public String textureName() {
            return textureName;
        }
    }

    public record Action(Component label, int width, Runnable run, Icon icon) {
        public Action(Component label, int width, Runnable run) {
            this(label, width, run, null);
        }

        public Action(Icon icon, Runnable run) {
            this(null, 20, run, icon);
        }

        private int topOffset() {
            return icon == null ? 2 : 0;
        }

        private int bottomOffset() {
            return ROW_HEIGHT - topOffset();
        }
    }
    public record DiagramStop(boolean hasExpress, boolean local, boolean express, boolean first, boolean last) {}

    public record Row(Component title, Component subtitle, int depth, int color, boolean actionsOnHover,
                      List<Action> actions, Runnable open, Runnable rightClick, DiagramStop diagram, int groupIndex) {
        public Row(Component title, Component subtitle, int depth, int color, boolean actionsOnHover,
                   List<Action> actions, Runnable open, Runnable rightClick, DiagramStop diagram) {
            this(title, subtitle, depth, color, actionsOnHover, actions, open, rightClick, diagram, -1);
        }

        public Row(Component title, Component subtitle, int depth, int color, boolean actionsOnHover,
                   List<Action> actions, Runnable open, Runnable rightClick) {
            this(title, subtitle, depth, color, actionsOnHover, actions, open, rightClick, null);
        }
    }

    private static final int ROW_HEIGHT = 20;
    private static final int TOP_PADDING = 3;
    private static final int HINT_HEIGHT = 15;
    private int firstRow;
    private long scrollStart = System.nanoTime();

    public void reset() {
        firstRow = 0;
        scrollStart = System.nanoTime();
    }

    public void render(Canvas canvas, List<Row> rows, Component hint, int left, int top, int right, int bottom,
                       int mouseX, int mouseY) {
        final int rowStart = rowStart(top, hint != null);
        final int capacity = capacity(rowStart, bottom);
        clamp(rows.size(), capacity);
        canvas.fill(left, top, right, bottom, 0xFF16191D);
        canvas.fill(left, top, right, top + 1, 0xFF394049);
        if (hint != null) {
            canvas.text(fit(hint, right - left - 12), left + 6, top + 3, 0xFFE3C179);
        }
        for (int visible = 0; visible < capacity && firstRow + visible < rows.size(); visible++) {
            final int index = firstRow + visible;
            final Row row = rows.get(index);
            final int y = rowStart + visible * ROW_HEIGHT;
            final boolean hover = inside(mouseX, mouseY, left, y, right, y + ROW_HEIGHT);
            final int indent = left + 7 + row.depth() * 11;
            if (row.diagram() != null) {
                drawDiagramStop(canvas, row, indent + 5, y, right);
                continue;
            }
            final int textX;
            if (row.groupIndex() >= 0) {
                textX = drawTreeRow(canvas, rows, index, left, y, right, hover);
            } else {
                final int fill = hover ? 0xFF353C47 : row.depth() == 0 ? 0xFF262C34 : 0xFF20252D;
                canvas.fill(left + 3, y, right - 3, y + ROW_HEIGHT - 1, fill);
                canvas.fill(indent, y + 1, indent + 3, y + ROW_HEIGHT - 2, row.color() | 0xFF000000);
                textX = indent + 8;
            }

            int actionX = right - 7;
            if (!row.actionsOnHover() || hover) {
                for (final Action action : row.actions()) {
                    final int x = actionX - action.width();
                    final int topY = y + action.topOffset();
                    final boolean overAction = inside(mouseX, mouseY, x, topY, actionX, y + action.bottomOffset());
                    if (action.icon() != null) {
                        canvas.icon(action.icon(), x, topY, overAction);
                    } else {
                        canvas.fill(x, topY, actionX, y + action.bottomOffset(), overAction ? 0xFF526383 : 0xFF394858);
                        final var label = fit(action.label(), action.width() - 6);
                        final int textWidth = Minecraft.getInstance().font.width(label);
                        canvas.text(label, x + (action.width() - textWidth) / 2, y + 6, 0xFFE6E9EE);
                    }
                    actionX = x - 3;
                }
            }

            drawScrollingText(canvas, row.title(), textX, y + (row.subtitle() == null ? 6 : 1), actionX - 4, 0xFFE5E9EE);
            if (row.subtitle() != null) {
                if (row.actions().isEmpty() && row.open() == null) {
                    drawScrollingText(canvas, row.subtitle(), textX, y + 11, actionX - 4, 0xFFADB7C4);
                } else {
                    canvas.text(fit(row.subtitle(), actionX - textX - 4), textX, y + 11, 0xFFADB7C4);
                }
            }
        }
        if (rows.size() > capacity && capacity > 0) {
            final int height = Math.max(8, capacity * ROW_HEIGHT * capacity / rows.size());
            final int y = rowStart + (capacity * ROW_HEIGHT - height) * firstRow / (rows.size() - capacity);
            canvas.fill(right - 2, y, right, y + height, 0xFF68798C);
        }
    }

    private void drawScrollingText(Canvas canvas, Component label, int x, int y, int right, int color) {
        if (right <= x) return;
        final var font = Minecraft.getInstance().font;
        final int width = font.width(label);
        if (width <= right - x) {
            canvas.text(label, x, y, color);
            return;
        }
        final int period = width + font.width(ComponentHelper.literal("  ").withStyle(label.getStyle()));
        final int offset = (int) ((System.nanoTime() - scrollStart) / 40_000_000L % period);
        canvas.clip(x, y, right, y + font.lineHeight + 1, () -> {
            canvas.text(label, x - offset, y, color);
            canvas.text(label, x - offset + period, y, color);
        });
    }

    private static int drawTreeRow(Canvas canvas, List<Row> rows, int index, int left, int y, int right, boolean hover) {
        final Row row = rows.get(index);
        final int group = row.groupIndex();
        final boolean last = index + 1 == rows.size() || rows.get(index + 1).groupIndex() != group;
        final int bottom = y + ROW_HEIGHT - (last ? 2 : 0);
        final int background = group % 2 == 0
                ? (hover ? 0xFF1C1C1C : 0xFF101010) : (hover ? 0xFF353535 : 0xFF292929);
        canvas.fill(left + 3, y, right - 3, bottom, background);
        canvas.fill(left + 5, y, left + 9, bottom, row.color() | 0xFF000000);

        final int depth = row.depth();
        final int branchX = left + 18 + (depth - 1) * 12;
        final int center = y + ROW_HEIGHT / 2;
        final int branchColor = 0xFFA6A6A6;
        if (depth == 2 && hasSiblingBelow(rows, index, 1)) {
            canvas.fill(branchX - 12, y, branchX - 11, bottom, branchColor);
        }
        if (depth > 0) {
            canvas.fill(branchX, y, branchX + 1, hasSiblingBelow(rows, index, depth) ? bottom : center + 1, branchColor);
            canvas.fill(branchX, center, branchX + 8, center + 1, branchColor);
        }
        return depth == 0 ? left + 15 : branchX + 12;
    }

    private static boolean hasSiblingBelow(List<Row> rows, int index, int depth) {
        final int group = rows.get(index).groupIndex();
        for (int i = index + 1; i < rows.size(); i++) {
            final Row next = rows.get(i);
            if (next.groupIndex() != group || next.depth() < depth) return false;
            if (next.depth() == depth) return true;
        }
        return false;
    }

    private static void drawDiagramStop(Canvas canvas, Row row, int x, int y, int right) {
        final DiagramStop stop = row.diagram();
        final int center = y + ROW_HEIGHT / 2;
        final int top = stop.first() ? center : y;
        final int bottom = stop.last() ? center + 1 : y + ROW_HEIGHT;
        final int expressX = x + (stop.hasExpress() ? 24 : 0);
        final int color = row.color() | 0xFF000000;
        canvas.fill(x, top, x + 2, bottom, color);
        if (stop.hasExpress()) canvas.fill(expressX, top, expressX + 2, bottom, color);
        if (stop.local() && stop.express()) canvas.fill(x, center - 2, expressX + 2, center + 3, 0xFFE5E9EE);
        if (stop.local()) drawStation(canvas, x, center);
        if (stop.express()) drawStation(canvas, expressX, center);
        canvas.text(fit(row.title(), right - expressX - 13), expressX + 9, center - 4,
                stop.local() || stop.express() ? 0xFFE5E9EE : 0xFF919BA7);
    }

    private static void drawStation(Canvas canvas, int x, int y) {
        canvas.fill(x - 3, y - 2, x + 5, y + 3, 0xFFE5E9EE);
        canvas.fill(x - 1, y - 4, x + 3, y + 5, 0xFFE5E9EE);
    }

    public boolean click(List<Row> rows, boolean hasHint, double x, double y, int button, int left, int top, int right, int bottom) {
        if (!inside(x, y, left, top, right, bottom)) {
            return false;
        }
        final int rowStart = rowStart(top, hasHint);
        final int capacity = capacity(rowStart, bottom);
        clamp(rows.size(), capacity);
        if (y < rowStart) {
            return true;
        }
        final int visible = ((int) y - rowStart) / ROW_HEIGHT;
        if (visible >= capacity || firstRow + visible >= rows.size()) {
            return true;
        }
        final Row row = rows.get(firstRow + visible);
        final int rowY = rowStart + visible * ROW_HEIGHT;
        if (button == 0) {
            int actionX = right - 7;
            for (final Action action : row.actions()) {
                if (inside(x, y, actionX - action.width(), rowY + action.topOffset(), actionX, rowY + action.bottomOffset())) {
                    action.run().run();
                    return true;
                }
                actionX -= action.width() + 3;
            }
            if (row.open() != null) row.open().run();
        } else if (button == 1 && row.rightClick() != null) {
            row.rightClick().run();
        }
        return true;
    }

    public boolean scroll(int size, boolean hasHint, double x, double y, double amount, int left, int top, int right, int bottom) {
        if (!inside(x, y, left, top, right, bottom)) return false;
        firstRow -= (int) Math.signum(amount) * 2;
        clamp(size, capacity(rowStart(top, hasHint), bottom));
        return true;
    }

    private void clamp(int size, int capacity) {
        firstRow = Math.max(0, Math.min(firstRow, size - capacity));
    }

    private static int rowStart(int top, boolean hasHint) {
        return top + (hasHint ? HINT_HEIGHT : TOP_PADDING);
    }

    private static int capacity(int rowStart, int bottom) {
        return Math.max(0, (bottom - rowStart) / ROW_HEIGHT);
    }

    private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private static Component fit(Component label, int width) {
        final var font = Minecraft.getInstance().font;
        if (width <= 0) return com.fangsu.mappings.ComponentHelper.empty();
        if (font.width(label) <= width) return label;
        return com.fangsu.mappings.ComponentHelper.literal(
                font.plainSubstrByWidth(label.getString(), Math.max(0, width - font.width("…"))) + "…").withStyle(label.getStyle());
    }
}
