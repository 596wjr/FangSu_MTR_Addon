package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 各栏独立搜索和滚动；搜索只过滤显示，不修改实际选择。 */
public abstract class BaseSelectionScreen extends Screen {
    protected final int column;
    protected final int maxSelect;
    protected List<List<SelectionItem>> items;
    protected final List<SelectionItem> selectedItems = new ArrayList<>();
    protected final List<String> selected = new ArrayList<>();
    protected List<Component> titles;

    private final List<Integer> scroll = new ArrayList<>();
    private final List<String> queries = new ArrayList<>();
    private final List<EditBox> searchFields = new ArrayList<>();
    private final List<ColumnView> views = new ArrayList<>();
    private Button closeButton;
    private int dragScrollIndex = -1;
    private double dragThumbOffset;

    protected BaseSelectionScreen(Component title, int column, int maxSelect) {
        super(title);
        this.column = column;
        this.maxSelect = maxSelect;
        for (int i = 0; i < column; i++) selected.add(null);
        for (int i = 0; i <= column; i++) {
            scroll.add(0);
            queries.add("");
            views.add(null);
        }
    }

    @Override
    protected void init() {
        super.init();
        dragScrollIndex = -1;
        updateColumn();
        searchFields.clear();
        for (int i = 0; i <= column; i++) {
            final int index = i;
            Component placeholder = ComponentHelper.translatable("ui.fangsu.common.search");
            EditBox field = new EditBox(font, columnX(i), 42, columnWidth(), 18, placeholder);
            field.setMaxLength(256);
            field.setValue(queries.get(i));
            field.setSuggestion(queries.get(i).isEmpty() ? placeholder.getString() : null);
            field.setResponder(value -> {
                queries.set(index, value);
                field.setSuggestion(value.isEmpty() ? placeholder.getString() : null);
                scroll.set(index, 0);
                views.set(index, null);
                if (dragScrollIndex == index) dragScrollIndex = -1;
            });
            searchFields.add(addRenderableWidget(field));
            views.set(i, null);
        }
        //#if MC_VERSION >= 11903
        closeButton = addRenderableWidget(Button.builder(ComponentHelper.translatable("ui.fangsu.block.close_and_save"), button -> onClose())
                .bounds(width / 2 - 75, height - 30, 150, 20).build());
        //#else
        //$$ closeButton = addRenderableWidget(new Button(width / 2 - 75, height - 30, 150, 20,
        //$$         ComponentHelper.translatable("ui.fangsu.block.close_and_save"), button -> onClose()));
        //#endif
    }

    //#if MC_VERSION < 12003
    @Override
    public void tick() {
        super.tick();
        for (EditBox field : searchFields) field.tick();
    }
    //#endif

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
        g.drawString(font, title, 10, 8, 0xFFFFFF, false);
        for (int i = 0; i <= column; i++) renderColumn(g, i, mouseX, mouseY);
        for (EditBox field : searchFields) field.render(g.asMinecraft(), mouseX, mouseY, partialTick);
        closeButton.render(g.asMinecraft(), mouseX, mouseY, partialTick);
    }

    public void renderBackground(GraphicContext g) {
        g.fill(0, 0, width, height, 0xFF202020);
    }

    private void renderColumn(GraphicContext g, int index, int mouseX, int mouseY) {
        int x = columnX(index), width = columnWidth();
        int yTop = listTop(), yBottom = listBottom();
        if (titles != null && titles.size() > index) {
            g.drawCenteredString(font, titles.get(index), x + width / 2, 27, 0xFFFFFF);
        }
        g.fill(x, yTop, x + width, yBottom, 0xAA000000);
        ColumnView view = getView(index);
        int offset = clampScroll(index, view);
        g.enableScissor(x, yTop, x + width - 8, yBottom);
        if (view.rows.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(ComponentHelper.translatable("ui.fangsu.common.no_results"), Math.max(1, width - 16));
            int y = yTop + 6;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 5, y, 0xAAAAAA, false);
                y += font.lineHeight;
            }
        }
        for (Row row : view.rows) {
            int y = yTop + row.top - offset;
            if (y + row.height <= yTop || y >= yBottom) continue;
            int boxHeight = row.height - 3;
            boolean hovered = inside(mouseX, mouseY, x + 2, Math.max(y, yTop), x + width - 8, Math.min(y + boxHeight, yBottom));
            int color = isItemSelected(index, row.item) ? 0x66FFFFFF : hovered ? 0x44FFFFFF : 0x22FFFFFF;
            g.fill(x + 2, y, x + width - 8, y + boxHeight, color);
            int textX = x + (row.item.color == null ? 5 : 10), textY = y + 3;
            for (FormattedCharSequence line : row.lines) {
                g.drawString(font, line, textX, textY, 0xFFFFFF, false);
                textY += font.lineHeight;
            }
            if (row.item.color != null) g.fill(x + 4, y + 3, x + 7, textY, row.item.color | 0xFF000000);
        }
        g.disableScissor();
        Scrollbar bar = scrollbar(index, view);
        if (bar != null) {
            g.fill(bar.left, yTop, bar.right, yBottom, 0x30FFFFFF);
            g.fill(bar.left, bar.top, bar.right, bar.bottom, 0x99FFFFFF);
        }
    }

    /** 渲染、点击和滚动共用同一行高；大列表的换行只在内容或搜索改变后重算。 */
    private ColumnView getView(int index) {
        List<SelectionItem> source = getColumnItems(index);
        int width = columnWidth();
        String query = queries.get(index).strip().toLowerCase(Locale.ROOT);
        ColumnView cached = views.get(index);
        if (cached != null && cached.width == width && cached.query.equals(query) && sameItems(cached.source, source)) return cached;
        List<Row> rows = new ArrayList<>();
        String[] terms = query.isEmpty() ? new String[0] : query.split("\\s+");
        int top = 4;
        for (SelectionItem item : source) {
            if (item == null || !matches(item, terms)) continue;
            List<FormattedCharSequence> lines = font.split(FormattedText.of(item.text), Math.max(1, width - (item.color == null ? 16 : 21)));
            int height = Math.max(1, lines.size()) * font.lineHeight + 8;
            rows.add(new Row(item, lines, top, height));
            top += height;
        }
        ColumnView view = new ColumnView(width, query, new ArrayList<>(source), rows, top + 4);
        views.set(index, view);
        return view;
    }

    private boolean sameItems(List<SelectionItem> a, List<SelectionItem> b) {
        if (a.size() != b.size()) return false;
        // SelectionItem 不可变；按实例比较可发现只换了文本但保留 value 的替换项。
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private boolean matches(SelectionItem item, String[] terms) {
        for (String term : terms) if (!item.searchText.contains(term)) return false;
        return true;
    }

    private List<SelectionItem> getColumnItems(int index) {
        if (index == column) return selectedItems;
        return items != null && index < items.size() && items.get(index) != null ? items.get(index) : List.of();
    }

    /** 子类可覆写主模型单选、附件多选等行为。 */
    protected void onItemActivated(int index, SelectionItem item) {
        if (index < column - 1) {
            selected.set(index, item.value);
            for (int i = index + 1; i < column; i++) {
                selected.set(i, null);
                scroll.set(i, 0);
            }
            updateColumn();
        } else if (index == column - 1) {
            if (maxSelect == 1) selectedItems.clear();
            if (!selectedItems.contains(item) && selectedItems.size() < maxSelect) selectedItems.add(item);
        } else {
            selectedItems.remove(item);
        }
    }

    protected boolean isItemSelected(int index, SelectionItem item) {
        return index < column - 1 ? Objects.equals(item.value, selected.get(index))
                : index == column - 1 && selectedItems.contains(item);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int index = columnAt(mouseX, mouseY);
        if (index < 0 || button != 0) return false;
        // 旧版 EditBox 通过 setFocus 公开焦点控制，1.19.4 起改为 setFocused。
        //#if MC_VERSION >= 11904
        for (EditBox field : searchFields) field.setFocused(false);
        //#else
        //$$ for (EditBox field : searchFields) field.setFocus(false);
        //#endif
        setFocused(null);
        ColumnView view = getView(index);
        clampScroll(index, view);
        Scrollbar bar = scrollbar(index, view);
        if (bar != null && mouseX >= bar.left && mouseX < bar.right) {
            if (mouseY >= bar.top && mouseY < bar.bottom) {
                dragScrollIndex = index;
                dragThumbOffset = mouseY - bar.top;
            } else {
                int direction = mouseY < bar.top ? -1 : 1;
                scroll.set(index, scroll.get(index) + direction * Math.max(1, visibleHeight() - 12));
                clampScroll(index, view);
            }
            return true;
        }
        if (mouseX >= columnX(index) + columnWidth() - 8) return true;
        double contentY = mouseY - listTop() + scroll.get(index);
        for (Row row : view.rows) {
            if (contentY >= row.top && contentY < row.top + row.height - 3) {
                onItemActivated(index, row.item);
                for (int i = 0; i <= column; i++) clampScroll(i, getView(i));
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragScrollIndex >= 0 && button == 0) {
            dragScrollIndex = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragScrollIndex >= 0 && button == 0) {
            ColumnView view = getView(dragScrollIndex);
            Scrollbar bar = scrollbar(dragScrollIndex, view);
            if (bar != null) {
                int travel = visibleHeight() - (bar.bottom - bar.top);
                double ratio = travel <= 0 ? 0 : (mouseY - listTop() - dragThumbOffset) / travel;
                scroll.set(dragScrollIndex, (int) Math.round(Math.max(0, Math.min(1, ratio)) * maxScroll(view)));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    //#if MC_VERSION < 12003
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
    //#else
    //$$ @Override
    //$$ public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double delta) {
    //#endif
        int index = columnAt(mouseX, mouseY);
        if (index >= 0) {
            ColumnView view = getView(index);
            scroll.set(index, clampScroll(index, view) - (int) Math.round(delta * 24));
            clampScroll(index, view);
            return true;
        }
        //#if MC_VERSION < 12003
        return super.mouseScrolled(mouseX, mouseY, delta);
        //#else
        //$$ return super.mouseScrolled(mouseX, mouseY, horizontalAmount, delta);
        //#endif
    }

    private Scrollbar scrollbar(int index, ColumnView view) {
        int max = maxScroll(view);
        if (max <= 0) return null;
        int visible = visibleHeight();
        int thumb = Math.min(visible, Math.max(12, (int) ((long) visible * visible / view.contentHeight)));
        int top = listTop() + (int) ((long) clampScroll(index, view) * (visible - thumb) / max);
        int right = columnX(index) + columnWidth() - 2;
        return new Scrollbar(right - 4, right, top, top + thumb);
    }

    private int clampScroll(int index, ColumnView view) {
        int clamped = Math.max(0, Math.min(scroll.get(index), maxScroll(view)));
        scroll.set(index, clamped);
        return clamped;
    }

    private int maxScroll(ColumnView view) { return Math.max(0, view.contentHeight - visibleHeight()); }
    private int listTop() { return 66; }
    private int listBottom() { return Math.max(listTop() + 1, height - 42); }
    private int visibleHeight() { return listBottom() - listTop(); }
    private int margin() { return Math.max(8, width / 20); }
    private int columnWidth() { return Math.max(1, (width - margin() * 2 - 6 * column) / (column + 1)); }
    private int columnX(int index) { return margin() + index * (columnWidth() + 6); }
    protected int heightPercent(double p) { return (int) (height * p); }

    private int columnAt(double mouseX, double mouseY) {
        if (mouseY < listTop() || mouseY >= listBottom()) return -1;
        for (int i = 0; i <= column; i++) {
            int x = columnX(i);
            if (mouseX >= x && mouseX < x + columnWidth()) return i;
        }
        return -1;
    }

    private boolean inside(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public abstract void updateColumn();

    private record Row(SelectionItem item, List<FormattedCharSequence> lines, int top, int height) { }
    private record ColumnView(int width, String query, List<SelectionItem> source, List<Row> rows, int contentHeight) { }
    private record Scrollbar(int left, int right, int top, int bottom) { }

    public static final class SelectionItem {
        private final String text;
        private final String value;
        private final Integer color;
        private final String searchText;

        public SelectionItem(String text, String value, Integer color) { this(text, value, color, ""); }

        /** 附加搜索字段（例如来源路径）不影响显示文本和保存值。 */
        public SelectionItem(String text, String value, Integer color, String searchText) {
            this.text = Objects.requireNonNullElse(text, "");
            this.value = value;
            this.color = color;
            this.searchText = (this.text + " " + Objects.requireNonNullElse(value, "") + " "
                    + Objects.requireNonNullElse(searchText, "")).toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean equals(Object obj) { return obj instanceof SelectionItem o && Objects.equals(o.value, value); }
        @Override
        public int hashCode() { return Objects.hashCode(value); }
        public String text() { return text; }
        public String value() { return value; }
        public Integer color() { return color; }
    }
}
