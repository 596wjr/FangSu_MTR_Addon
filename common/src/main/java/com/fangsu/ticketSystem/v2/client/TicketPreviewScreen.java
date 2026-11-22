package com.fangsu.ticketSystem.v2.client;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ticketSystem.v2.TicketCards;
import com.fangsu.ticketSystem.v2.TicketDefinitions.Card;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

final class TicketPreviewScreen extends Screen {
    private final Card card;
    private final String raw;
    private boolean back;
    private int ticketX, ticketY, ticketWidth, ticketHeight;

    static void open(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        try {
            Card card = TicketCards.read(stack, null);
            client.setScreen(new TicketPreviewScreen(card, stack.getTag().getString(TicketCards.KEY)));
        } catch (IllegalArgumentException | com.google.gson.JsonParseException exception) {
            if (client.player != null) {
                client.player.displayClientMessage(ComponentHelper.translatable("tooltip.fangsu.ticket.invalid"), true);
            }
        }
    }

    private TicketPreviewScreen(Card card, String raw) {
        super(ComponentHelper.literal(card.product.name));
        this.card = card;
        this.raw = raw;
    }

    //#if MC_VERSION >= 12000
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    //#else
    //$$ @Override
    //$$ public void render(com.mojang.blaze3d.vertex.PoseStack graphics, int mouseX, int mouseY, float partialTick) {
    //#endif
        GraphicContext view = GraphicContext.of(graphics);
        view.fill(0, 0, width, height, 0xBC000000);
        var texture = TicketTextures.cardFace(raw, card, back);
        double scale = Math.min((width - 32.0) / texture.width, (height - 52.0) / texture.height);
        ticketWidth = Math.max(1, (int) (texture.width * scale));
        ticketHeight = Math.max(1, (int) (texture.height * scale));
        ticketX = (width - ticketWidth) / 2;
        ticketY = (height - 16 - ticketHeight) / 2;
        view.blit(texture.identifier, ticketX, ticketY, ticketWidth, ticketHeight, 0, 0,
                texture.width, texture.height, texture.width, texture.height);
        view.drawCenteredString(font, ComponentHelper.translatable("ui.fangsu.ticketing.preview_flip"),
                width / 2, ticketY + ticketHeight + 10, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        boolean inside = x >= ticketX && x < ticketX + ticketWidth && y >= ticketY && y < ticketY + ticketHeight;
        if (!inside) {
            onClose();
            return true;
        }
        if (button == 0) {
            back = !back;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
