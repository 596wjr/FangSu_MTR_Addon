package com.fangsu.ticketSystem.v2.client;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.scripting.TextUtil;
import com.fangsu.ticketSystem.v2.TicketDefinitions.Kind;
import com.fangsu.ticketSystem.v2.TicketMessages;
import com.fangsu.ticketSystem.v2.TicketPackets.Message;
import com.fangsu.utils.GraphicContext;
import net.minecraft.network.chat.Component;

/** 补票机展示手持票卡、进站记录和本站补票结果。 */
final class FareAdjustmentScreen extends TicketServiceScreen {
    FareAdjustmentScreen(Message state) {
        super(state, "adjust_machine", 0xFF193D62, 0xFFA94C15, 0xFFFFF5E9);
        request.action = "adjust";
    }

    @Override
    protected void renderService(GraphicContext view) {
        box(view, 12, 45, panelWidth - 24, 62);
        if (heldCard == null) {
            line(view, label("hold_card"), 21, 71, panelWidth - 42, MUTED);
        } else {
            var texture = TicketTextures.cardFace(heldRaw, heldCard, false);
            view.blit(texture.identifier, left + 20, top + 54, 72, 45, 0, 0, texture.width, texture.height,
                    texture.width, texture.height);
            line(view, ComponentHelper.literal(heldCard.product.name), 102, 57, panelWidth - 123, INK);
            Component journey = heldCard.journey == null || heldCard.journey.entry == null ? label("no_entry_record")
                    : label("entry_record", TextUtil.getNonExtraParts(heldCard.journey.entry.name).replace('|', ' '));
            line(view, journey, 102, 72, panelWidth - 123, MUTED);
            if (heldCard.product.kind == Kind.SINGLE || heldCard.product.kind == Kind.ITINERARY) {
                line(view, ComponentHelper.translatable("tooltip.fangsu.ticket.fare", heldCard.allowance),
                        102, 87, panelWidth - 123, MUTED);
            }
        }
        box(view, 12, 113, panelWidth - 24, 28);
        line(view, offer == null ? label("adjust_here") : TicketMessages.text(offer.description),
                21, 123, panelWidth - 42, INK);
    }

    @Override
    protected boolean canQuote() {
        return heldCard != null;
    }

    @Override
    protected Component guidance() {
        return label(canQuote() ? "review_quote" : "hold_card");
    }
}
