package com.fangsu.ticketSystem.v2.client;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ticketSystem.v2.TicketDefinitions.Kind;
import com.fangsu.ticketSystem.v2.TicketDefinitions.Product;
import com.fangsu.ticketSystem.v2.TicketPackets.Message;
import com.fangsu.ui.ConfigChoiceScreen;
import com.fangsu.utils.GraphicContext;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 购卡和充值分别展示商品与充值金额。 */
final class CardVendingScreen extends TicketServiceScreen {
    private final List<Product> products;
    private Product selected;
    private int quantity = 1;

    CardVendingScreen(Message state) {
        super(state, "card_machine", 0xFF193D62, 0xFF0B629D, 0xFFEAF5FD);
        products = state.config.saleProducts(state.catalogue, state.kind);
        if (!products.isEmpty()) select(products.get(0));
    }

    private boolean buying() {
        return "buy".equals(request.action);
    }

    private int divider() {
        return (panelWidth - 6) * 3 / 5;
    }

    @Override
    protected void buildServiceControls() {
        int half = (panelWidth - 30) / 2;
        markSelected(control(12, 44, half, 20, label("buy_cards"), () -> changeService("buy")), buying());
        markSelected(control(18 + half, 44, panelWidth - 30 - half, 20,
                label("recharge"), () -> changeService("recharge")), !buying());
        int sectionWidth = divider() - 25;
        if (buying()) {
            control(19, 91, sectionWidth, 20, label("select_product"), this::chooseProduct).active = !products.isEmpty();
            control(19, 116, 20, 19, ComponentHelper.literal("−"), () -> changeQuantity(-1)).active = quantity > 1;
            control(divider() - 26, 116, 20, 19, ComponentHelper.literal("+"), () -> changeQuantity(1)).active = quantity < 6;
        } else {
            int buttonWidth = (sectionWidth - 6) / 2;
            int[] amounts = {10, 20, 50, 100};
            for (int i = 0; i < amounts.length; i++) {
                int amount = amounts[i];
                markSelected(control(19 + (i % 2) * (buttonWidth + 6), 92 + (i / 2) * 22, buttonWidth, 19,
                        label("amount", amount), () -> {
                            request.recharge = amount;
                            optionsChanged();
                            rebuildControls();
                        }), amount == request.recharge);
            }
        }
    }

    private void changeService(String action) {
        request.action = action;
        request.count = buying() ? quantity : 1;
        optionsChanged();
        rebuildControls();
    }

    private void changeQuantity(int difference) {
        quantity = Math.max(1, Math.min(6, quantity + difference));
        request.count = quantity;
        optionsChanged();
        rebuildControls();
    }

    private void chooseProduct() {
        var choices = products.stream().map(product -> new ConfigChoiceScreen.Choice(product.id, product.name)).toList();
        minecraft.setScreen(new ConfigChoiceScreen(this, "ui.fangsu.ticketing.products", choices, id ->
                products.stream().filter(product -> product.id.equals(id)).findFirst().ifPresent(this::select)));
    }

    private void select(Product product) {
        selected = product;
        request.product = product.id;
        optionsChanged();
    }

    @Override
    protected void renderService(GraphicContext view) {
        int split = divider();
        int detailsX = split + 13;
        int detailsWidth = panelWidth - detailsX - 20;
        box(view, 12, 71, split - 11, 70);
        box(view, split + 7, 71, panelWidth - split - 19, 70);
        if (buying()) {
            line(view, selected == null ? label("no_products") : ComponentHelper.literal(selected.name), 19, 78, split - 26, INK);
            view.drawCenteredString(font, label("quantity", quantity), left + (split + 12) / 2, top + 122, INK);
            if (selected == null) return;
            line(view, label("card_unit_price", selected.price), detailsX, 83, detailsWidth, INK);
            Component detail = selected.kind == Kind.IC ? label("initial_balance", selected.initialBalance)
                    : ComponentHelper.translatable("tooltip.fangsu.ticket.duration", selected.durationHours());
            line(view, detail, detailsX, 108, detailsWidth, MUTED);
        } else {
            line(view, label("top_up_amount"), 19, 78, split - 24, INK);
            if (!hasIcCard()) {
                line(view, label("hold_ic"), detailsX, 83, detailsWidth, MUTED);
                return;
            }
            line(view, ComponentHelper.literal(heldCard.product.name), detailsX, 82, detailsWidth, INK);
            line(view, ComponentHelper.translatable("tooltip.fangsu.ticket.balance", heldCard.balance), detailsX, 101, detailsWidth, MUTED);
            line(view, label("top_up_amount_value", request.recharge), detailsX, 119, detailsWidth, INK);
        }
    }

    private boolean hasIcCard() {
        return heldCard != null && heldCard.product.kind == Kind.IC;
    }

    @Override
    protected boolean canQuote() {
        return buying() ? selected != null : hasIcCard();
    }

    @Override
    protected Component guidance() {
        if (canQuote()) return label("review_quote");
        return label(buying() ? "no_products" : "hold_ic");
    }
}
