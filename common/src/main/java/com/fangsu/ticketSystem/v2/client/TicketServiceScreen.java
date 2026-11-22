package com.fangsu.ticketSystem.v2.client;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.scripting.TextUtil;
import com.fangsu.ticketSystem.v2.TicketCards;
import com.fangsu.ticketSystem.v2.TicketDefinitions;
import com.fangsu.ticketSystem.v2.TicketDefinitions.Card;
import com.fangsu.ticketSystem.v2.TicketPackets.Message;
import com.fangsu.ticketSystem.v2.TicketTransactions.Quote;
import com.fangsu.ticketSystem.v2.TicketTransactions.Request;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** 售卡与补票共用报价确认流程，各自绘制业务区域。 */
abstract class TicketServiceScreen extends Screen {
    protected static final int INK = 0xFF193650, MUTED = 0xFF48627A;
    protected final Message state;
    protected final Request request = new Request();
    protected int left, top, panelWidth;
    protected Card heldCard;
    protected String heldRaw;
    protected Quote offer;
    private final int header, accent, surface;
    private Button submit, payment;
    private ItemStack held = ItemStack.EMPTY;
    private Component status;
    private long sequence, expires, quoteSentAt;
    private long quoteAt = 1;
    private boolean quoting, paying;

    protected TicketServiceScreen(Message state, String title, int header, int accent, int surface) {
        super(label(title));
        this.state = state;
        this.header = header;
        this.accent = accent;
        this.surface = surface;
        request.payment = state.config.mtrPayment ? "mtr" : "item";
    }

    @Override
    protected final void init() {
        panelWidth = Math.min(418, width - 20);
        left = (width - panelWidth) / 2;
        top = (height - 222) / 2;
        buildServiceControls();
        payment = control(12, 190, panelWidth / 2 - 18, 22, paymentLabel(), this::switchPayment);
        submit = control(panelWidth / 2, 190, panelWidth / 2 - 12, 22, primaryLabel(), this::primaryAction);
        payment.visible = showPayment();
        syncHand();
        updateControls();
    }

    protected void buildServiceControls() {
    }
    protected abstract void renderService(GraphicContext view);
    protected abstract boolean canQuote();
    protected abstract Component guidance();
    protected Component primaryLabel() { return label("confirm_payment"); }
    protected boolean primaryAvailable() { return offer != null; }
    protected void primaryAction() { submit(); }
    protected boolean showPayment() { return true; }
    protected int primaryColor() { return accent; }
    protected void drawHeader(GraphicContext view) { }
    protected int stationLabelWidth() { return panelWidth - 24; }

    protected static Component label(String key, Object... args) {
        return ComponentHelper.translatable("ui.fangsu.ticketing." + key, args);
    }

    protected final Button control(int x, int y, int w, int h, Component text, Runnable click) {
        return addRenderableWidget(new ServiceButton(left + x, top + y, w, h, text, ignored -> {
            if (!paying) click.run();
        }));
    }

    protected final void markSelected(Button button, boolean selected) {
        ((ServiceButton) button).selected = selected;
        button.active = !selected;
    }

    protected final void box(GraphicContext view, int x, int y, int w, int h) {
        view.fill(left + x, top + y, left + x + w, top + y + h, 0xFFCCDDE7);
        view.fill(left + x + 1, top + y + 1, left + x + w - 1, top + y + h - 1, surface);
    }

    protected final void line(GraphicContext view, Component text, int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) view.drawString(font, text, left + x, top + y, color, false);
        else view.drawString(font, fit(text, maxWidth), left + x, top + y, color, false);
    }

    private String fit(Component text, int maxWidth) {
        String value = text.getString();
        return font.width(value) <= maxWidth ? value
                : font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    protected final void rebuildControls() {
        clearWidgets();
        setFocused(null);
        init();
    }

    protected final void optionsChanged() {
        sequence = TicketClient.nextRequestId();
        offer = null;
        status = null;
        quoting = false;
        quoteAt = System.currentTimeMillis() + 120;
        updateControls();
    }

    private Component paymentLabel() {
        if (!state.config.mtrPayment && !state.config.itemPayment) return label("payment_unavailable");
        return label("mtr".equals(request.payment) ? "mtr_balance" : "item_currency");
    }

    private void switchPayment() {
        request.payment = "mtr".equals(request.payment) ? "item" : "mtr";
        optionsChanged();
    }

    private void updateControls() {
        if (submit == null) return;
        submit.active = primaryAvailable() && !paying;
        submit.setMessage(primaryLabel());
        payment.active = !paying && state.config.mtrPayment && state.config.itemPayment;
        payment.setMessage(paymentLabel());
    }

    private void syncHand() {
        if (minecraft == null || minecraft.player == null) return;
        ItemStack current = minecraft.player.getMainHandItem();
        if (current.getItem() == held.getItem() && current.getCount() == held.getCount()
                && Objects.equals(current.getTag(), held.getTag())) return;
        held = current.copy();
        heldCard = null;
        heldRaw = null;
        if (TicketCards.isNative(held)) {
            try {
                heldCard = TicketCards.read(held, state.station);
                heldRaw = TicketDefinitions.JSON.toJson(heldCard);
            } catch (RuntimeException ignored) {
                // 损坏或无法读取的票卡不可提交。
            }
        }
        if (!"buy".equals(request.action)) optionsChanged();
    }

    protected final void submit() {
        syncHand();
        if (!submit.active || offer == null || !canQuote()) return;
        if (System.currentTimeMillis() >= expires) {
            optionsChanged();
            return;
        }
        Message message = message("pay");
        message.quote = offer.id;
        offer = null;
        paying = true;
        status = label("paying");
        updateControls();
        TicketClient.send(message);
    }

    private void requestQuote(long now) {
        Message message = message("quote");
        message.request = request;
        quoting = true;
        quoteAt = 0;
        quoteSentAt = now;
        expires = now + 10000;
        status = label("updating");
        updateControls();
        TicketClient.send(message);
    }

    private Message message(String action) {
        Message message = new Message();
        message.action = action;
        message.pos = state.pos;
        message.session = state.session;
        sequence = TicketClient.nextRequestId();
        message.requestId = sequence;
        return message;
    }

    final boolean matches(Message reply) {
        return state.pos == reply.pos && state.session.equals(reply.session);
    }

    final void response(Message reply) {
        if (!matches(reply) || reply.requestId != sequence) return;
        switch (reply.action) {
            case "quote" -> {
                if (!quoting) return;
                offer = reply.offer;
                quoting = false;
                expires = quoteSentAt + 25000;
                status = null;
            }
            case "paid" -> {
                if (paying) minecraft.setScreen(null);
                return;
            }
            case "error" -> {
                quoting = false;
                paying = false;
                offer = null;
                status = ComponentHelper.literal(reply.error);
                quoteAt = System.currentTimeMillis() + 5000;
            }
            default -> { return; }
        }
        updateControls();
    }

    @Override
    public final void tick() {
        if (paying) return;
        syncHand();
        long now = System.currentTimeMillis();
        if ((quoting || offer != null) && now >= expires) {
            boolean timedOut = quoting;
            optionsChanged();
            if (timedOut) {
                status = label("quote_timeout");
                quoteAt = now + 2000;
            }
        }
        if (quoteAt > 0 && now >= quoteAt && canQuote() && (state.config.mtrPayment || state.config.itemPayment))
            requestQuote(now);
    }

    @Override
    public void removed() {
        super.removed();
        if (quoting) optionsChanged();
    }

    //#if MC_VERSION >= 12000
    @Override
    public final void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    //#else
    //$$ @Override
    //$$ public final void render(com.mojang.blaze3d.vertex.PoseStack graphics, int mouseX, int mouseY, float delta) {
    //#endif
        GraphicContext view = GraphicContext.of(graphics);
        view.fill(0, 0, width, height, 0x580E2030);
        view.fill(left + 3, top + 4, left + panelWidth + 3, top + 226, 0x540E2030);
        view.fill(left - 1, top - 1, left + panelWidth + 1, top + 223, 0xFF9AB0C1);
        view.fill(left, top, left + panelWidth, top + 222, 0xFFF7FBFE);
        view.fill(left, top, left + panelWidth, top + 36, header);
        view.fill(left, top + 34, left + panelWidth, top + 37, accent);
        line(view, getTitle().copy().withStyle(style -> style.withBold(true)), 12, 8, panelWidth - 24, 0xFFFFFFFF);
        if (state.station != null) line(view, label("origin", TextUtil.getNonExtraParts(state.station.name).replace('|', ' ')),
                12, 22, stationLabelWidth(), 0xFFD5E8F5);
        drawHeader(view);
        renderService(view);
        view.fill(left, top + 149, left + panelWidth, top + 222, 0xFFE8F2F9);
        view.fill(left, top + 149, left + panelWidth, top + 150, 0xFFBAD1E0);
        Component summary = status == null ? guidance() : status;
        if (offer != null) {
            summary = label("mtr".equals(offer.payment) ? "summary_mtr" : "summary_items", offer.amount, offer.paymentCount);
            Component detail = "item".equals(offer.payment) ? label("no_change", state.config.currency) : label("confirm");
            line(view, detail, 12, 174, panelWidth - 24, MUTED);
        }
        var summaryLines = font.split(summary, panelWidth - 24);
        int maxLines = offer == null ? 2 : 1;
        for (int i = 0; i < Math.min(summaryLines.size(), maxLines); i++) {
            view.drawString(font, summaryLines.get(i), left + 12, top + 157 + i * 12, INK, false);
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public final boolean isPauseScreen() {
        return false;
    }

    private final class ServiceButton extends Button {
        private boolean selected;

        private ServiceButton(int x, int y, int w, int h, Component text, OnPress press) {
            //#if MC_VERSION >= 11903
            super(x, y, w, h, text, press, DEFAULT_NARRATION);
            //#else
            //$$ super(x, y, w, h, text, press);
            //#endif
        }

        //#if MC_VERSION >= 12000
        @Override public void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        //#elseif MC_VERSION >= 11904
        //$$ @Override public void renderWidget(com.mojang.blaze3d.vertex.PoseStack graphics, int mouseX, int mouseY, float delta) {
        //#else
        //$$ @Override public void renderButton(com.mojang.blaze3d.vertex.PoseStack graphics, int mouseX, int mouseY, float delta) {
        //#endif
            GraphicContext view = GraphicContext.of(graphics);
            //#if MC_VERSION >= 11903
            int x = getX(), y = getY();
            //#else
            //$$ int x = this.x, y = this.y;
            //#endif
            boolean enabled = active && !paying;
            boolean primary = this == submit && enabled;
            boolean hover = enabled && isHoveredOrFocused();
            int border = primary ? primaryColor() : selected || hover ? accent : 0xFFB0C8D9;
            int background = primary ? border : selected || hover ? surface : enabled ? 0xFFFFFFFF : 0xFFF0F6FA;
            view.fill(x, y, x + width, y + height, border);
            view.fill(x + 1, y + 1, x + width - 1, y + height - 1, background);
            String text = fit(getMessage(), width - 10);
            view.drawString(font, text, x + (width - font.width(text)) / 2, y + (height - 8) / 2,
                    primary ? 0xFFFFFFFF : enabled || selected ? INK : MUTED, false);
        }
    }
}
