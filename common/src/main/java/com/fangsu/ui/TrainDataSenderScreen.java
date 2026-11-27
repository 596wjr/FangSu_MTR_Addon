package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.train.data.TrainDataClient;
import com.fangsu.train.data.TrainDataSenderConfig;
import com.fangsu.train.data.TrainDataSenderConfig.BowTarget;
import com.fangsu.train.data.TrainDataSenderConfig.Entry;
import com.fangsu.train.data.TrainDataSenderConfig.Mode;
import com.fangsu.train.data.TrainDataSnapshot.PantographState;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public final class TrainDataSenderScreen extends Screen {
    private static final int TABLE_TOP = 92;
    private static final int ROW_HEIGHT = 23;
    private static final int TYPE_WIDTH = 80;
    private static final int SCOPE_WIDTH = 44;
    private static final int REMOVE_WIDTH = 22;

    private enum RowType { PANTOGRAPH, CUSTOM }

    private static final class Row {
        RowType type;
        String car = "*";
        String key = "";
        String value = "";
        BowTarget target = BowTarget.ALL;
        String bowName = "";
        PantographState bowState = PantographState.UP;

        Row(RowType type) {
            this.type = type;
        }

        Row(Entry entry) {
            this(entry.isPantograph() ? RowType.PANTOGRAPH : RowType.CUSTOM);
            car = entry.carLabel();
            if (entry.isPantograph()) {
                target = entry.bowTarget();
                if (target != BowTarget.ALL) bowName = entry.bowName();
                bowState = PantographState.valueOf(entry.value());
            } else {
                key = entry.key();
                value = entry.value();
            }
        }

        Entry toEntry() {
            if (type == RowType.CUSTOM && key.isBlank() && value.isEmpty()) return null;
            String selection = car.trim();
            boolean allCars = selection.isEmpty() || selection.equals("*");
            if (!allCars && !selection.matches("[1-9][0-9]{0,2}")) {
                throw new IllegalArgumentException("Invalid car number");
            }
            int carNumber = allCars ? 0 : Integer.parseInt(selection);
            return type == RowType.PANTOGRAPH ? Entry.pantograph(carNumber, target, bowName.trim(), bowState)
                    : new Entry(carNumber, key.trim(), value);
        }
    }

    private final BlockPos pos;
    private final List<Row> rows = new ArrayList<>();
    private final List<EditBox> fields = new ArrayList<>();
    private Set<Long> routes;
    private Mode mode;
    private String texture;
    private Row scopeRow;
    private int page;
    private int rowsPerPage;
    private boolean invalid;

    public TrainDataSenderScreen(BlockPos pos, TrainDataSenderConfig config) {
        super(label("title"));
        this.pos = pos;
        routes = config.routes();
        mode = config.mode();
        texture = config.texture();
        config.entries().forEach(entry -> rows.add(new Row(entry)));
    }

    void setRoutes(List<Long> selected) { routes = Set.copyOf(selected); }

    @Override
    protected void init() {
        fields.clear();
        int w = panelWidth();
        int x = panelLeft();
        rowsPerPage = Math.max(1, (height - TABLE_TOP - 66) / ROW_HEIGHT);
        page = Math.min(page, lastPage());
        button(x, 30, w / 2 - 2,
                routes.isEmpty() ? label("routes_all") : ComponentHelper.translatable("ui.fangsu.train_data_sender.route_count", routes.size()),
                ignored -> minecraft.setScreen(new TrainDataRouteSelectionScreen(this, routes)));
        button(x + w / 2 + 2, 30, w - w / 2 - 2, modeLabel(), button -> {
            mode = switch (mode) {
                case ANY -> Mode.STOPPED;
                case STOPPED -> Mode.MOVING;
                case MOVING -> Mode.ANY;
            };
            button.setMessage(modeLabel());
        });
        field(x, 54, w, "texture", texture, 256, text -> texture = text);

        int first = page * rowsPerPage;
        for (int index = first; index < Math.min(rows.size(), first + rowsPerPage); index++) {
            buildRow(rows.get(index), x, TABLE_TOP + (index - first) * ROW_HEIGHT);
        }

        int footer = height - 54;
        button(x, footer, 25, ComponentHelper.literal("<"), ignored -> changePage(-1)).active = page > 0;
        button(x + 29, footer, 25, ComponentHelper.literal(">"), ignored -> changePage(1)).active = page < lastPage();
        button(x + w - 164, footer, 80, label("add_pantograph"),
                ignored -> addRow(RowType.PANTOGRAPH)).active = rows.size() < TrainDataSenderConfig.LIMIT;
        button(x + w - 80, footer, 80, label("add_custom"),
                ignored -> addRow(RowType.CUSTOM)).active = rows.size() < TrainDataSenderConfig.LIMIT;
        button(x, height - 28, w, ComponentHelper.translatable("ui.fangsu.block.close_and_save"), ignored -> save());
    }

    private void buildRow(Row row, int x, int y) {
        cycle(x, y, TYPE_WIDTH, "row_type", List.of(RowType.PANTOGRAPH, RowType.CUSTOM), row.type,
                type -> label(type == RowType.PANTOGRAPH ? "pantograph" : "custom"), type -> {
                    row.type = type;
                    scopeRow = null;
                    rebuild();
                });
        int middle = x + middleOffset();
        int value = x + valueOffset();
        if (scopeRow == row) {
            if (row.type == RowType.PANTOGRAPH) {
                cycle(middle, y, middleWidth(), "scope", List.of(BowTarget.ALL, BowTarget.BOW, BowTarget.GROUP),
                        row.target, TrainDataSenderScreen::targetLabel, target -> {
                            row.target = target;
                            rebuild();
                        });
                if (row.target != BowTarget.ALL) {
                    field(value, y, valueWidth(), row.target == BowTarget.GROUP ? "group_name" : "bow_id",
                            row.bowName, 64, text -> row.bowName = text);
                }
            } else {
                field(middle, y, middleWidth(), "car", row.car, 3, text -> row.car = text);
            }
        } else if (row.type == RowType.PANTOGRAPH) {
            field(middle, y, middleWidth(), "car", row.car, 3, text -> row.car = text);
            cycle(value, y, valueWidth(), "value", List.of(PantographState.UP, PantographState.DOWN), row.bowState,
                    state -> label(state == PantographState.UP ? "up" : "down"), state -> {
                        row.bowState = state;
                        invalid = false;
                    });
        } else {
            field(middle, y, middleWidth(), "field", row.key, 64, text -> row.key = text);
            field(value, y, valueWidth(), "value", row.value, 512, text -> row.value = text);
        }

        int right = x + panelWidth();
        button(right - REMOVE_WIDTH - SCOPE_WIDTH - 4, y, SCOPE_WIDTH,
                scopeLabel(row), ignored -> {
                    scopeRow = scopeRow == row ? null : row;
                    rebuild();
                });
        button(right - REMOVE_WIDTH, y, REMOVE_WIDTH, ComponentHelper.literal("×"), ignored -> {
            rows.remove(row);
            if (scopeRow == row) scopeRow = null;
            rebuild();
        });
    }

    private Component scopeLabel(Row row) {
        if (scopeRow == row) return label("done");
        if (row.type == RowType.PANTOGRAPH && row.target != BowTarget.ALL) return targetLabel(row.target);
        if (row.type == RowType.CUSTOM && !row.car.isBlank() && !row.car.equals("*")) {
            return ComponentHelper.literal("#" + row.car);
        }
        return label("scope");
    }

    private int panelWidth() { return Math.min(530, width - 24); }

    private int panelLeft() { return (width - panelWidth()) / 2; }

    private int middleOffset() { return TYPE_WIDTH + 4; }

    private int middleWidth() { return (panelWidth() - TYPE_WIDTH - SCOPE_WIDTH - REMOVE_WIDTH - 16) * 2 / 5; }

    private int valueOffset() { return middleOffset() + middleWidth() + 4; }

    private int valueWidth() { return panelWidth() - valueOffset() - SCOPE_WIDTH - REMOVE_WIDTH - 8; }

    private int lastPage() { return Math.max(0, (rows.size() - 1) / rowsPerPage); }

    private Component modeLabel() {
        return switch (mode) {
            case ANY -> label("any_speed");
            case STOPPED -> ComponentHelper.translatable("gui.mtr.stopped_only");
            case MOVING -> ComponentHelper.translatable("gui.mtr.moving_only");
        };
    }

    private static Component targetLabel(BowTarget target) {
        return switch (target) {
            case ALL -> label("all_bows");
            case BOW -> label("bow_id");
            case GROUP -> label("group_name");
        };
    }

    private void addRow(RowType type) {
        rows.add(new Row(type));
        scopeRow = null;
        page = lastPage();
        rebuild();
    }

    private void changePage(int step) {
        scopeRow = null;
        page = Math.max(0, Math.min(lastPage(), page + step));
        rebuild();
    }

    private void rebuild() {
        invalid = false;
        clearWidgets();
        init();
    }

    private void save() {
        List<Entry> entries = new ArrayList<>();
        try {
            for (Row row : rows) {
                Entry entry = row.toEntry();
                if (entry != null) entries.add(entry);
            }
            TrainDataClient.save(pos, new TrainDataSenderConfig(routes, mode, texture, entries));
            onClose();
        } catch (IllegalArgumentException exception) {
            invalid = true;
        }
    }

    private void field(int x, int y, int w, String name, String initial, int max, Consumer<String> update) {
        Component title = label(name);
        EditBox field = new EditBox(font, x, y, w, 20, title);
        field.setMaxLength(max);
        field.setValue(initial);
        field.setSuggestion(initial.isEmpty() ? title.getString() : null);
        field.setResponder(text -> {
            field.setSuggestion(text.isEmpty() ? title.getString() : null);
            invalid = false;
            update.accept(text);
        });
        fields.add(addRenderableWidget(field));
    }

    private <T> void cycle(int x, int y, int w, String name, List<T> values, T initial,
                           Function<T, Component> text, Consumer<T> change) {
        addRenderableWidget(CycleButton.builder(text).withValues(values).withInitialValue(initial).displayOnlyValue()
                .create(x, y, w, 20, label(name), (button, selected) -> change.accept(selected)));
    }

    private Button button(int x, int y, int w, Component message, Consumer<Button> action) {
        //#if MC_VERSION >= 11903
        return addRenderableWidget(Button.builder(message, action::accept).bounds(x, y, w, 20).build());
        //#else
        //$$ return addRenderableWidget(new Button(x, y, w, 20, message, action::accept));
        //#endif
    }

    private static Component label(String key) {
        return ComponentHelper.translatable("ui.fangsu.train_data_sender." + key);
    }

    //#if MC_VERSION < 12003
    @Override
    public void tick() {
        fields.forEach(EditBox::tick);
    }
    //#endif

    @Override
    public boolean isPauseScreen() { return false; }

    //#if MC_VERSION >= 12000
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        GraphicContext context = GraphicContext.of(graphics);
    //#else
    //$$ @Override
    //$$ public void render(com.mojang.blaze3d.vertex.PoseStack graphics, int mouseX, int mouseY, float delta) {
    //$$     GraphicContext context = GraphicContext.of(graphics);
    //#endif
        int x = panelLeft();
        int w = panelWidth();
        context.fill(0, 0, width, height, 0xEF171C23);
        context.fill(x, 77, x + w, 90, 0xFF293743);
        context.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        context.drawString(font, label("row_type"), x + 3, 79, 0xE5EEF5, false);
        context.drawString(font, label("key_or_car"), x + middleOffset() + 3, 79, 0xE5EEF5, false);
        context.drawString(font, label("value"), x + valueOffset() + 3, 79, 0xE5EEF5, false);
        context.drawCenteredString(font, ComponentHelper.literal((page + 1) + " / " + (lastPage() + 1)),
                x + 62 + (w - 230) / 2, height - 47, 0xCAD7E3);
        if (invalid) context.drawCenteredString(font, label("invalid"), width / 2, height - 66, 0xFF8585);
        super.render(graphics, mouseX, mouseY, delta);
    }
}
