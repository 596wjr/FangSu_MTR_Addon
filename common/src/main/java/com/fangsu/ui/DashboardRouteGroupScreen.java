package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mtr.DashboardRouteGroups;
import com.fangsu.mtr.RouteOverview;
import com.fangsu.mtr.RouteStopStatus;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.BiConsumer;

public final class DashboardRouteGroupScreen extends BasicConfigScreen {
    private final Screen parent;
    private final BiConsumer<String, Integer> save;
    private final List<RouteOverview.Diagram> overview;
    private final String oldName;
    private final int oldColor;
    private String name;
    private String color;

    private DashboardRouteGroupScreen(Screen parent, String name, int color,
                                      BiConsumer<String, Integer> save, List<RouteOverview.Diagram> overview) {
        super(ComponentHelper.literal(name.replace('|', ' ')));
        this.parent = parent;
        this.oldName = name;
        this.oldColor = color & 0xFFFFFF;
        this.name = name;
        this.color = String.format("%06X", this.oldColor);
        this.save = save;
        this.overview = overview;
    }

    public static <T> void open(String name, List<DashboardRouteGroups.Route<T>> members, BiConsumer<String, Integer> save) {
        final Minecraft client = Minecraft.getInstance();
        client.setScreen(new DashboardRouteGroupScreen(client.screen, name, members.get(0).color(), save, List.of()));
    }

    public static void showOverview(String name, List<RouteOverview.Line> routes) {
        final Minecraft client = Minecraft.getInstance();
        client.setScreen(new DashboardRouteGroupScreen(client.screen, name, 0, null, RouteOverview.build(routes)));
    }

    public static void confirmDelete(String name, int count, boolean twice, Runnable delete) {
        final Minecraft client = Minecraft.getInstance();
        final Screen parent = client.screen;
        final var title = ComponentHelper.translatable("ui.fangsu.route.delete", name.replace('|', ' '));
        final var warning = ComponentHelper.translatable("ui.fangsu.route.delete_count", count);
        client.setScreen(new ConfirmScreen(accept -> {
            if (accept && twice) {
                client.setScreen(new ConfirmScreen(finalConfirmation -> {
                    if (finalConfirmation) {
                        delete.run();
                    }
                    client.setScreen(parent);
                }, title, ComponentHelper.translatable("ui.fangsu.route.delete_confirm", count)));
            } else {
                if (accept) {
                    delete.run();
                }
                client.setScreen(parent);
            }
        }, title, warning));
    }

    @Override
    protected void buildFixedWidgets() {
        final var label = ComponentHelper.translatable(save == null ? "gui.back" : "ui.fangsu.block.close_and_save");
        final net.minecraft.client.gui.components.Button.OnPress press = button -> {
            if (save != null) {
                final int colorValue = Integer.parseInt(color, 16);
                final String changedName = oldName.equals(name.trim()) ? null : name.trim();
                final Integer changedColor = oldColor == colorValue ? null : colorValue;
                if (changedName != null || changedColor != null) {
                    save.accept(changedName, changedColor);
                }
            }
            onClose();
        };
        //#if MC_VERSION >= 11903
        closeButton = addFixedWidget(net.minecraft.client.gui.components.Button.builder(label, press)
                .bounds(width / 2 - 50, height - 40, 100, 20).build());
        //#else
        //$$ closeButton = addFixedWidget(new net.minecraft.client.gui.components.Button(width / 2 - 50, height - 40, 100, 20, label, press));
        //#endif
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        int y = layout.y;
        if (save == null) {
            for (final var diagram : overview) {
                final var down = diagram.down();
                final boolean hasExpress = diagram.upExpress() != null || diagram.downExpress() != null;
                final int spacing = hasExpress ? Math.max(48, layout.contentWidth / 3) : 0;
                final int localLabelWidth = font.width((down == null ? "" : "↑ ") + diagram.up().name());
                final int secondLabelWidth = down == null ? 0 : font.width("↓ " + down.name());
                final int left = layout.areaLeft + Math.max(10, Math.max(localLabelWidth, secondLabelWidth) / 2);
                addDirectionLabel(left, y, diagram.up(), diagram.upExpress(), spacing, down == null ? "" : "↑ ");
                if (down != null) {
                    addDirectionLabel(left, y + 12, down, diagram.downExpress(), spacing, "↓ ");
                }
                y += down == null ? 18 : 30;
                for (int index = 0; index < diagram.rows().size(); index++) {
                    final var row = diagram.rows().get(index);
                    final boolean localStops = row.up() != null && row.up().status() == RouteStopStatus.NORMAL
                            || row.down() != null && row.down().status() == RouteStopStatus.NORMAL;
                    final boolean expressStops = RouteOverview.Diagram.stops(diagram.upExpress(), row.up())
                            || RouteOverview.Diagram.stops(diagram.downExpress(), row.down());
                    final var station = row.up() == null ? row.down() : row.up();
                    final var markers = new StationMarkers(left, y, spacing, 0xFF000000 | diagram.up().color(),
                            localStops, expressStops, index == 0, index == diagram.rows().size() - 1);
                    addRenderableWidget(markers);
                    addEntry(markers, y);
                    addEntry(createTextLabel(left + spacing + 14, y + 6,
                            ComponentHelper.literal(station.name().replace('|', ' ')), TextLabel.Align.LEFT,
                            localStops || expressStops ? 0xFFFFFF : 0xA0A0A0, false), y + 6);
                    y += StationMarkers.ROW_HEIGHT;
                }
                y += 12;
            }
            return;
        }
        addEntry(createTextLabel(layout.areaLeft, y, ComponentHelper.translatable("ui.fangsu.route.group_name"),
                TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        addField(layout, y + 14, name, 128, value -> name = value, value -> true);
        y += 48;
        addEntry(createTextLabel(layout.areaLeft, y, ComponentHelper.translatable("ui.fangsu.route.group_color"),
                TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        addField(layout, y + 14, color, 6, value -> color = value, value -> value.matches("[0-9a-fA-F]*"));
        validate();
    }

    private void addField(ContentLayout layout, int y, String value, int maxLength,
                          java.util.function.Consumer<String> update, java.util.function.Predicate<String> accepts) {
        final var field = new EditBox(font, layout.areaLeft, y, layout.contentWidth, 20, title);
        field.setMaxLength(maxLength);
        field.setFilter(accepts);
        field.setValue(value);
        field.setResponder(text -> {
            update.accept(text);
            validate();
        });
        addRenderableWidget(field);
        addEntry(field, y);
    }

    private void validate() {
        closeButton.active = !name.isBlank() && !name.contains("||") && color.length() == 6;
    }

    private void addDirectionLabel(int left, int y, RouteOverview.Line local, RouteOverview.Line express,
                                   int spacing, String arrow) {
        addEntry(createTextLabel(left, y, ComponentHelper.literal(arrow + local.name()),
                TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        if (express != null) {
            addEntry(createTextLabel(left + spacing, y, ComponentHelper.literal(arrow + express.name()),
                    TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        }
    }

    @Override
    protected void renderPanelBackground(GraphicContext graphics) {
        graphics.fill(getPanelLeft(), getPanelTop(), getPanelRight(), getPanelBottom(), 0xF0171717);
        graphics.drawCenteredString(font, getTitle(), width / 2, Math.max(4, getPanelTop() - 18), 0xFFFFFFFF);
    }

    private static final class StationMarkers extends TextLabel {
        private static final int ROW_HEIGHT = 22;
        private static final int WHITE = 0xFFFFFFFF;
        private final int spacing;
        private final int color;
        private final boolean local;
        private final boolean express;
        private final boolean first;
        private final boolean last;

        private StationMarkers(int x, int y, int spacing, int color, boolean local, boolean express,
                               boolean first, boolean last) {
            super(x, y, ComponentHelper.empty(), Align.LEFT, color, false);
            this.spacing = spacing;
            this.color = color;
            this.local = local;
            this.express = express;
            this.first = first;
            this.last = last;
        }

        @Override
        protected void renderLabel(GraphicContext graphics) {
            //#if MC_VERSION >= 11903
            final int x = getX();
            final int y = getY();
            //#else
            //$$ final int x = this.x;
            //$$ final int y = this.y;
            //#endif
            final int center = y + ROW_HEIGHT / 2;
            final int top = first ? center : y;
            final int bottom = last ? center + 1 : y + ROW_HEIGHT;
            graphics.fill(x - 1, top, x + 2, bottom, color);
            if (spacing > 0) {
                graphics.fill(x + spacing - 1, top, x + spacing + 2, bottom, color);
            }
            if (local && express) {
                graphics.fill(x, center - 3, x + spacing, center + 4, WHITE);
            }
            if (local) {
                drawStation(graphics, x, center);
            }
            if (express) {
                drawStation(graphics, x + spacing, center);
            }
        }

        private static void drawStation(GraphicContext graphics, int x, int y) {
            graphics.fill(x - 4, y - 2, x + 5, y + 3, WHITE);
            graphics.fill(x - 2, y - 4, x + 3, y + 5, WHITE);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
