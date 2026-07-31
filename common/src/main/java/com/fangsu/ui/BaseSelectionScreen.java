package com.fangsu.ui;

import com.fangsu.utils.GraphicContext;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class BaseSelectionScreen extends Screen {

    /* ===================== 基本配置 ===================== */

    protected final int column;
    protected final int maxSelect;

    protected List<List<SelectionItem>> items;
    protected final List<SelectionItem> selectedItems;
    protected final List<String> selected;

    private final List<Integer> scroll;

    private MouseClickInfo mouseClickInfo;
    private MouseScrollInfo mouseScrollInfo;

    private SelectionItem pendingAdd;
    private SelectionItem pendingRemove;

    /* ===================== 滚动条拖动状态 ===================== */

    private int dragScrollIndex = -1;
    private int dragThumbOffset = 0;
    private int dragThumbSize = 0;

    protected List<Component> titles;

    /* ===================== 构造 ===================== */

    protected BaseSelectionScreen(Component title, int column, int maxSelect) {
        super(title);
        this.column = column;
        this.maxSelect = maxSelect;

        this.selected = new ArrayList<>();
        for (int i = 0; i < column; i++) {
            selected.add(null);
        }

        this.selectedItems = new ArrayList<>();

        this.scroll = new ArrayList<>();
        for (int i = 0; i <= column; i++) {
            scroll.add(0);
        }
    }

    /* ===================== 生命周期 ===================== */

    @Override
    protected void init() {
        super.init();
        updateColumn();
    }

    /* ===================== 渲染入口 ===================== */

    //#if MC_VERSION >= 12000
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        GraphicContext g = GraphicContext.of(guiGraphics);
        //#else
        //$$ @Override
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     GraphicContext g = GraphicContext.of(poseStack);
        //#endif
        renderBackground(g);
        renderTitle(g);

        int columnWidth = calcColumnWidth();
        int columnGap = calcColumnGap();

        scrollbarHitInfo[0] = -1; // 重置滚动条点击信息

        for (int i = 0; i <= column; i++) {


            renderColumn(g, i, columnWidth, columnGap, mouseX, mouseY);
        }

        // 渲染循环结束后
        if (pendingAdd != null) {
            if (maxSelect == 1) {
                selectedItems.clear();
            }
            if (!selectedItems.contains(pendingAdd) && selectedItems.size() < maxSelect) {
                selectedItems.add(pendingAdd);
            }
            pendingAdd = null;
        }

        if (pendingRemove != null) {
            selectedItems.remove(pendingRemove);
            pendingRemove = null;
        }

    }

    /* ===================== 渲染分解 ===================== */

    public void renderBackground(GraphicContext g) {
//        g.fill(0, 0, width, height, 0xFF101010);
        //#if MC_VERSION >= 12000
        renderDirtBackground(g.asMinecraft());
        //#else
        //$$ // 1.18.2 renderDirtBackground takes int (color), not PoseStack
        //#endif
    }


    private void renderTitle(GraphicContext g) {
        g.drawString(font, title, 10, 2, 0xFFFFFF, false);
    }

    private void renderColumn(
            GraphicContext g,
            int index,
            int columnWidth,
            int columnGap,
            int mouseX,
            int mouseY
    ) {
        int x = calcColumnX(index, columnWidth, columnGap);
        int yTop = heightPercent(0.2);
        int yBottom = heightPercent(0.7);

        if (titles != null) {
            if (titles.size() > index) {
                g.drawCenteredString(font, titles.get(index), x + columnWidth / 2, (int) (yTop - font.lineHeight * 1.5), 0xFFFFFF);
            }
        }

        g.fill(x, yTop, x + columnWidth, yBottom, 0xaa000000);

        List<SelectionItem> columnItems = getColumnItems(index);
        if (columnItems == null || columnItems.isEmpty()) {
            return;
        }

        g.enableScissor(x, yTop, x + columnWidth, yBottom);

        int y = (int) (yTop - scroll.get(index) + font.lineHeight * 0.5);
        int totalHeight = 0;

        for (SelectionItem item : columnItems) {
            totalHeight += renderItem(
                    g, item, index, x, y, columnWidth, mouseX, mouseY
            );
            y += getItemHeight(item, columnWidth);
        }

        handleScroll(index, x, columnWidth, totalHeight);
        g.disableScissor();

        renderScrollbar(g, index, x + columnWidth - 4, yTop, yBottom - yTop, totalHeight);
    }

    private int renderItem(
            GraphicContext g,
            SelectionItem item,
            int columnIndex,
            int x,
            int y,
            int columnWidth,
            int mouseX,
            int mouseY
    ) {
        List<FormattedCharSequence> lines = splitItemText(item, columnWidth);
        int textHeight = lines.size() * font.lineHeight;
        int boxHeight = (int) (textHeight + font.lineHeight * 0.5);

        boolean selectedFlag = isItemSelected(columnIndex, item);
        boolean hovered = isMouseOverItem(mouseX, mouseY, x, y, columnWidth, boxHeight);

        renderItemBackground(g, x, y, columnWidth, boxHeight, selectedFlag, hovered);
        renderItemText(g, item, lines, x, y, columnWidth);
        renderItemColorBar(g, item, x, y, columnWidth, textHeight);

        handleClick(columnIndex, item, x, y, columnWidth, boxHeight);

        return boxHeight;
    }

    /* ===================== Item 子逻辑 ===================== */

    private void renderItemBackground(
            GraphicContext g,
            int x,
            int y,
            int width,
            int height,
            boolean selected,
            boolean hovered
    ) {
        int color = selected ? 0x55ffffff : hovered ? 0x44ffffff : 0x33ffffff;
        g.fill(
                (int) (x + width * 0.025),
                y,
                (int) (x + width * 0.975),
                y + height,
                color
        );
    }

    private void renderItemText(
            GraphicContext g,
            SelectionItem item,
            List<FormattedCharSequence> lines,
            int x,
            int y,
            int width
    ) {
        int textX = (int) (x + width * (item.color == null ? 0.05 : 0.1));
        int textY = (int) (y + font.lineHeight * 0.25);

        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, textX, textY, 0xffffffff, false);
            textY += font.lineHeight;
        }
    }

    private void renderItemColorBar(
            GraphicContext g,
            SelectionItem item,
            int x,
            int y,
            int columnWidth,
            int textHeight
    ) {
        if (item.color == null) return;

        g.fill(
                (int) (x + columnWidth * 0.05),
                (int) (y + font.lineHeight * 0.25),
                (int) (x + columnWidth * 0.075),
                (int) (y + font.lineHeight * 0.25 + textHeight),
                item.color
        );
    }

    /* ===================== 输入处理 ===================== */

    private void handleClick(
            int index,
            SelectionItem item,
            int x,
            int y,
            int width,
            int height
    ) {
        if (mouseClickInfo == null) return;

        if (!isMouseOverItem(
                mouseClickInfo.mouseX,
                mouseClickInfo.mouseY,
                x, y, width, height
        )) return;

        if (index <= column - 2) {
            selected.set(index, item.value);
            for (int i = selected.size() - 1; i > index; i--) {
                selected.set(i, null);
            }
            updateColumn();
        } else if (index == column - 1) {
            pendingAdd = item; // 延迟处理
        } else {
            pendingRemove = item; // 延迟处理
        }

        mouseClickInfo = null;
    }


    private int calcVisibleHeight() {
        return heightPercent(0.7) - heightPercent(0.2);
    }

    /**
     * 内容实际占用的总高度 = item总高度 + 顶部初始偏移(font.lineHeight * 0.5)
     */
    private int calcContentHeight(int totalHeight) {
        return totalHeight + (int) (font.lineHeight * 0.5);
    }

    /**
     * scroll 为正数表示已向下滚动的像素量。
     * 范围: [0, maxScroll]，0=顶部，maxScroll=最底部。
     */
    private int calcMaxScroll(int totalHeight) {
        return Math.max(0, calcContentHeight(totalHeight) - calcVisibleHeight());
    }

    private void handleScroll(int index, int x, int width, int totalHeight) {
        if (mouseScrollInfo == null) return;

        if (mouseScrollInfo.mouseX < x || mouseScrollInfo.mouseX > x + width) return;
        if (mouseScrollInfo.mouseY < heightPercent(0.2) || mouseScrollInfo.mouseY > heightPercent(0.7)) return;

        int maxScroll = calcMaxScroll(totalHeight);
        if (maxScroll <= 0) return;

        int value = scroll.get(index) - (int) (mouseScrollInfo.delta * 8);
        value = Math.max(0, Math.min(value, maxScroll));
        scroll.set(index, value);

        mouseScrollInfo = null;
    }
    /* ===================== 滚动条 ===================== */

    private void renderScrollbar(GraphicContext g, int index, int barX, int barY, int barHeight, int totalHeight) {
        int maxScroll = calcMaxScroll(totalHeight);
        if (maxScroll <= 0) return;

        int scrollbarWidth = 4;
        int scrollbarColor = 0x99FFFFFF;
        int scrollbarBgColor = 0x30FFFFFF;

        int sbLeft = barX - scrollbarWidth;
        int sbRight = barX;

        // 滚动条背景
        g.fill(sbLeft, barY, sbRight, barY + barHeight, scrollbarBgColor);

        // 滑块
        int visibleHeight = calcVisibleHeight();
        int contentHeight = calcContentHeight(totalHeight);
        float scrollRatio = (float) scroll.get(index) / maxScroll;
        int thumbHeight = Math.max(10, (int) (barHeight * (float) visibleHeight / contentHeight));
        int thumbY = barY + (int) (scrollRatio * (barHeight - thumbHeight));
        g.fill(sbLeft, thumbY, sbRight, thumbY + thumbHeight, scrollbarColor);

        // 保存当前列的滚动条信息供点击检测
        scrollbarHitInfo[0] = index;
        scrollbarHitInfo[1] = sbLeft;
        scrollbarHitInfo[2] = sbRight;
        scrollbarHitInfo[3] = barY;
        scrollbarHitInfo[4] = barY + barHeight;
        scrollbarHitInfo[5] = thumbY;
        scrollbarHitInfo[6] = thumbY + thumbHeight;
    }

    private final int[] scrollbarHitInfo = new int[7];

    private boolean tryScrollbarClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int col = scrollbarHitInfo[0];
        if (col < 0) return false;
        int sbLeft = scrollbarHitInfo[1];
        int sbRight = scrollbarHitInfo[2];
        int sbTop = scrollbarHitInfo[3];
        int sbBottom = scrollbarHitInfo[4];
        int thumbTop = scrollbarHitInfo[5];
        int thumbBottom = scrollbarHitInfo[6];

        if (mouseX < sbLeft || mouseX > sbRight || mouseY < sbTop || mouseY > sbBottom) return false;

        // 检查是否点击了滑块
        if (mouseY >= thumbTop && mouseY <= thumbBottom) {
            dragScrollIndex = col;
            dragThumbOffset = (int) (mouseY - thumbTop);
            dragThumbSize = thumbBottom - thumbTop;
        } else {
            // 点击滑块上方或下方的轨道区域 -> 翻一页
            int columnWidth = calcColumnWidth();
            List<SelectionItem> columnItems = getColumnItems(col);
            int totalHeight = 0;
            if (columnItems != null) {
                for (SelectionItem item : columnItems) {
                    totalHeight += getItemHeight(item, columnWidth);
                }
            }
            int maxScroll = calcMaxScroll(totalHeight);
            if (maxScroll > 0) {
                int page = calcVisibleHeight() / 2;
                int currentScroll = scroll.get(col);
                int target;
                if (mouseY < thumbTop) {
                    target = currentScroll - page; // 向上翻页
                } else {
                    target = currentScroll + page; // 向下翻页
                }
                target = Math.max(0, Math.min(target, maxScroll));
                scroll.set(col, target);
            }
        }
        return true;
    }
    /* ===================== 工具方法 ===================== */

    private boolean isItemSelected(int index, SelectionItem item) {
        return index < column && item.value.equals(selected.get(index));
    }

    private boolean isMouseOverItem(
            double mx, double my,
            int x, int y,
            int width, int height
    ) {
        return mx > x + width * 0.025 &&
                mx < x + width * 0.975 &&
                my > y &&
                my < y + height;
    }

    private List<FormattedCharSequence> splitItemText(SelectionItem item, int width) {
        int maxWidth = (int) (width * (item.color == null ? 0.9 : 0.85));
        return font.split(FormattedText.of(item.text), maxWidth);
    }

    private int getItemHeight(SelectionItem item, int width) {
        return splitItemText(item, width).size() * font.lineHeight + font.lineHeight;
    }

    private List<SelectionItem> getColumnItems(int index) {
        if (items == null) return null;
        if (index < column) return items.get(index);
        if (index == column) return selectedItems;
        return null;
    }

    private int calcColumnWidth() {
        return (int) (width * 0.775 / (column + 1));
    }

    private int calcColumnGap() {
        return (int) (width * 0.025 / column);
    }

    private int calcColumnX(int index, int width, int gap) {
        return (int) (this.width * 0.1 + index * (width + gap));
    }

    protected int heightPercent(double p) {
        return (int) (height * p);
    }

    /* ===================== 输入 ===================== */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tryScrollbarClick(mouseX, mouseY, button)) {
            return true;
        }
        mouseClickInfo = new MouseClickInfo(mouseX, mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragScrollIndex = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragScrollIndex >= 0) {
            int columnWidth = calcColumnWidth();
            int yTop = heightPercent(0.2);
            int barHeight = heightPercent(0.7) - yTop;

            List<SelectionItem> columnItems = getColumnItems(dragScrollIndex);
            int totalHeight = 0;
            if (columnItems != null) {
                for (SelectionItem item : columnItems) {
                    totalHeight += getItemHeight(item, columnWidth);
                }
            }

            int maxScroll = calcMaxScroll(totalHeight);
            if (maxScroll > 0) {
                float ratio = (float) (mouseY - yTop - dragThumbOffset) / (barHeight - dragThumbSize);
                ratio = Math.max(0, Math.min(1, ratio));
                scroll.set(dragScrollIndex, (int) (ratio * maxScroll));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        mouseScrollInfo = new MouseScrollInfo(mouseX, mouseY, delta);
        // 检查是否在任意列区域内，返回true以消耗事件
        int columnWidth = calcColumnWidth();
        int columnGap = calcColumnGap();
        for (int i = 0; i <= column; i++) {
            int x = calcColumnX(i, columnWidth, columnGap);
            if (mouseX >= x && mouseX <= x + columnWidth &&
                    mouseY >= heightPercent(0.2) && mouseY <= heightPercent(0.7)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /* ===================== 抽象 ===================== */

    public abstract void updateColumn();

    /* ===================== 内部结构 ===================== */

    private record MouseClickInfo(double mouseX, double mouseY, int button) {
    }

    private record MouseScrollInfo(double mouseX, double mouseY, double delta) {
    }

    public static final class SelectionItem {
        private final String text;
        private final String value;
        private final Integer color;

        public SelectionItem(String text, String value, Integer color) {
            this.text = text;
            this.value = value;
            this.color = color;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SelectionItem o && Objects.equals(o.value, value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        public String text() {
            return text;
        }

        public String value() {
            return value;
        }

        public Integer color() {
            return color;
        }
    }
}
