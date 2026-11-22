package com.fangsu.ticketSystem.v2.client;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.scripting.TextUtil;
import com.fangsu.ticketSystem.v2.TicketDefinitions.Kind;
import com.fangsu.ticketSystem.v2.TicketDefinitions.Product;
import com.fangsu.ticketSystem.v2.TicketDefinitions.Station;
import com.fangsu.ticketSystem.v2.TicketPackets.Message;
import com.fangsu.ui.ConfigChoiceScreen;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 按站点购买单程票：选站、张数、确认付款。 */
final class PointToPointScreen extends TicketServiceScreen {
    private enum Step { ORIGIN, DESTINATION, QUANTITY, REVIEW }
    private static final int PAGE_SIZE = 6;
    private final List<Product> products;
    private final List<Station> stations;
    private final Screen parent;
    private final Button[] stationButtons = new Button[PAGE_SIZE];
    private List<Station> results = List.of();
    private Station origin, destination;
    private Step step;
    private EditBox search;
    private Button previousPage, nextPage;
    private int page;

    PointToPointScreen(Message state) {
        this(state, "", null);
    }

    PointToPointScreen(Message state, String preferredProduct, Screen parent) {
        super(state, "point_to_point", 0xFF193D62, 0xFF185BA4, 0xFFEAF4FC);
        this.parent = parent;
        products = state.config.saleProducts(state.catalogue, 0).stream().filter(p -> p.kind == Kind.SINGLE).toList();
        request.product = products.stream().filter(p -> p.id.equals(preferredProduct)).findFirst()
                .or(() -> products.stream().findFirst()).map(p -> p.id).orElse("");
        stations = state.saleStations().stream()
                .sorted(Comparator.comparing(PointToPointScreen::stationName).thenComparing(station -> station.key))
                .toList();
        origin = state.defaultSaleOrigin();
        step = origin == null ? Step.ORIGIN : Step.DESTINATION;
    }

    private static String stationName(Station station) {
        return TextUtil.getNonExtraParts(station.name).replace('|', ' ');
    }

    private boolean choosingStation() {
        return step == Step.ORIGIN || step == Step.DESTINATION;
    }

    private int divider() {
        return panelWidth * 2 / 3;
    }

    @Override
    protected void buildServiceControls() {
        int split = divider();
        int summaryWidth = panelWidth - split - 12;
        if (products.size() > 1) control(split + 3, 18, summaryWidth - 3, 14, productLabel(), this::chooseProduct);
        control(split + 3, 57, summaryWidth - 3, 18, stationLabel(origin), () -> go(Step.ORIGIN));
        control(split + 3, 92, summaryWidth - 3, 18, stationLabel(destination), () -> go(origin == null ? Step.ORIGIN : Step.DESTINATION));
        search = null;
        if (choosingStation()) buildStationPicker(split);
        if (step == Step.QUANTITY) {
            control(20, 83, 30, 25, ComponentHelper.literal("−"), () -> changeQuantity(-1)).active = request.count > 1;
            control(split - 51, 83, 30, 25, ComponentHelper.literal("+"), () -> changeQuantity(1)).active = request.count < 6;
        }
        if (step == Step.REVIEW) control(20, 119, split - 41, 20, label("previous_step"), () -> go(Step.QUANTITY));
        if (step == Step.DESTINATION || step == Step.QUANTITY) {
            control(12, 190, panelWidth / 2 - 18, 22, label("previous_step"),
                    () -> go(step == Step.QUANTITY ? Step.DESTINATION : Step.ORIGIN));
        }
        if (step == Step.ORIGIN && parent != null) {
            control(12, 190, panelWidth / 2 - 18, 22, ComponentHelper.translatable("gui.back"), this::onClose);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void buildStationPicker(int split) {
        search = new EditBox(font, left + 17, top + 52, split - 35, 16, label("search_station"));
        search.setBordered(false);
        search.setTextColor(INK);
        search.setMaxLength(80);
        addRenderableWidget(search);
        int cellWidth = (split - 44) / 3;
        for (int i = 0; i < stationButtons.length; i++) {
            int index = i;
            stationButtons[i] = control(16 + i % 3 * (cellWidth + 4), 73 + i / 3 * 23,
                    cellWidth, 20, ComponentHelper.literal(""), () -> selectStation(results.get(page * PAGE_SIZE + index)));
        }
        previousPage = control(16, 120, 24, 18, ComponentHelper.literal("‹"), () -> pageBy(-1));
        nextPage = control(split - 42, 120, 24, 18, ComponentHelper.literal("›"), () -> pageBy(1));
        search.setResponder(text -> { page = 0; refreshStations(text); });
        refreshStations("");
    }

    private void refreshStations(String text) {
        String query = text.strip().toLowerCase(Locale.ROOT);
        results = stations.stream()
                .filter(station -> step == Step.ORIGIN || origin != null && !station.key.equals(origin.key)
                        && station.dimension.equals(origin.dimension))
                .filter(station -> stationName(station).toLowerCase(Locale.ROOT).contains(query)
                        || (!query.isEmpty() && latinInitials(station).startsWith(query)))
                .toList();
        showStationPage();
    }

    private static String latinInitials(Station station) {
        StringBuilder initials = new StringBuilder();
        for (String word : TextUtil.getNonCjkParts(station.name).toLowerCase(Locale.ROOT).split("[\\s-]+")) {
            if (!word.isEmpty()) initials.append(word.charAt(0));
        }
        return initials.toString();
    }

    private void pageBy(int offset) {
        page += offset;
        showStationPage();
    }

    private void showStationPage() {
        int first = page * PAGE_SIZE;
        for (int i = 0; i < stationButtons.length; i++) {
            Button button = stationButtons[i];
            button.visible = first + i < results.size();
            if (!button.visible) continue;
            Station station = results.get(first + i);
            button.setMessage(stationLabel(station));
            Station selected = step == Step.ORIGIN ? origin : destination;
            markSelected(button, selected != null && selected.key.equals(station.key));
        }
        previousPage.active = page > 0;
        nextPage.active = first + PAGE_SIZE < results.size();
    }

    private Component stationLabel(Station station) {
        return station == null ? label("choose_station") : ComponentHelper.literal(stationName(station));
    }

    private void selectStation(Station station) {
        if (step == Step.ORIGIN) {
            origin = station;
            if (destination != null && (station.key.equals(destination.key) || !station.dimension.equals(destination.dimension)))
                destination = null;
        } else {
            destination = station;
        }
        request.origin = origin == null ? "" : origin.key;
        request.destination = destination == null ? "" : destination.key;
        optionsChanged();
        String text = search.getValue();
        rebuildControls();
        search.setValue(text);
    }

    private void chooseProduct() {
        var choices = products.stream().map(p -> new ConfigChoiceScreen.Choice(p.id, p.name)).toList();
        minecraft.setScreen(new ConfigChoiceScreen(this, "ui.fangsu.ticketing.products", choices, id -> {
            request.product = id;
            optionsChanged();
        }));
    }

    private Component productLabel() {
        Product product = state.catalogue.products.get(request.product);
        return product == null ? label("select_product") : ComponentHelper.literal(product.name);
    }

    private void changeQuantity(int change) {
        request.count = Math.max(1, Math.min(6, request.count + change));
        optionsChanged();
        rebuildControls();
    }

    private void go(Step target) {
        step = target;
        page = 0;
        rebuildControls();
    }

    @Override
    protected Component primaryLabel() {
        if (step != Step.REVIEW) return label("next_step");
        return label("point_pay");
    }

    @Override
    protected boolean primaryAvailable() {
        return switch (step) {
            case ORIGIN -> origin != null;
            case DESTINATION -> destination != null;
            case QUANTITY -> destination != null && !request.product.isEmpty();
            case REVIEW -> super.primaryAvailable();
        };
    }

    @Override
    protected void primaryAction() {
        switch (step) {
            case ORIGIN -> go(Step.DESTINATION);
            case DESTINATION -> go(Step.QUANTITY);
            case QUANTITY -> go(Step.REVIEW);
            case REVIEW -> submit();
        }
    }

    @Override
    protected boolean showPayment() {
        return step == Step.REVIEW;
    }

    @Override
    protected int primaryColor() {
        return step == Step.REVIEW && offer != null && (System.currentTimeMillis() / 850) % 2 != 0
                ? 0xFFAA5012 : 0xFF185BA4;
    }

    @Override
    protected boolean canQuote() {
        return origin != null && destination != null && !request.product.isEmpty();
    }

    @Override
    protected Component guidance() {
        return label(switch (step) {
            case ORIGIN -> "pick_departure";
            case DESTINATION -> "pick_arrival";
            case QUANTITY -> "pick_quantity";
            case REVIEW -> "review_quote";
        });
    }

    @Override
    protected void drawHeader(GraphicContext view) {
        int x = left + panelWidth - 76;
        view.fill(x, top + 14, x + 64, top + 15, 0xFF80A7CD);
        for (int i = 0; i < Step.values().length; i++) {
            int dot = x + i * 20;
            int color = i == step.ordinal() ? 0xFFFFAD5C : i < step.ordinal() ? 0xFFD7EBFA : 0xFF80A7CD;
            view.fill(dot, top + 11, dot + 6, top + 17, color);
        }
    }

    @Override
    protected int stationLabelWidth() {
        return products.size() > 1 ? divider() - 20 : super.stationLabelWidth();
    }

    @Override
    protected void renderService(GraphicContext view) {
        int split = divider();
        box(view, 12, 45, split - 19, 97);
        line(view, label("departure"), split + 3, 46, panelWidth - split - 15, MUTED);
        line(view, label("arrival"), split + 3, 81, panelWidth - split - 15, MUTED);
        if (choosingStation()) {
            view.fill(left + 16, top + 50, left + split - 18, top + 69, 0xFFFFFFFF);
            if (!search.isFocused() && search.getValue().isEmpty())
                line(view, label("search_station"), 18, 54, split - 40, MUTED);
            if (results.isEmpty()) line(view, label("stations_empty"), 18, 88, split - 40, MUTED);
            view.drawCenteredString(font, (page + 1) + " / " + Math.max(1, (results.size() + PAGE_SIZE - 1) / PAGE_SIZE),
                    left + (split + 5) / 2, top + 124, MUTED);
            return;
        }
        line(view, label("adult_ticket"), 20, 57, split - 42, INK);
        if (step == Step.QUANTITY) {
            view.drawCenteredString(font, Integer.toString(request.count), left + (split + 5) / 2, top + 92, INK);
            line(view, productLabel(), 20, 123, split - 41, MUTED);
        } else {
            line(view, label("quantity", request.count), 20, 77, split - 42, INK);
            if (offer != null) line(view, label("card_unit_price", offer.amount / request.count), 20, 99, split - 42, INK);
        }
    }
}
