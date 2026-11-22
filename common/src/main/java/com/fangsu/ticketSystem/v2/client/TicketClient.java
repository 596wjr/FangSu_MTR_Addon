package com.fangsu.ticketSystem.v2.client;

import com.fangsu.client.ClientHooks;
import com.fangsu.ticketSystem.v2.*;
import com.fangsu.ui.ConfigChoiceScreen;
import com.fangsu.mappings.ComponentHelper;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

public final class TicketClient {
    private static long requestId;
    static long nextRequestId() { return ++requestId; }
    public static void init() {
        ClientHooks.OPEN_TICKETING = TicketClient::open;
        ClientHooks.OPEN_TICKET_PREVIEW = TicketPreviewScreen::open;
        ClientHooks.REMOVE_TICKET_TEXTURE = id -> Minecraft.getInstance().execute(() -> {TicketTextures.remove(id);TicketTextures.remove(id+"/led");});
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, TicketPackets.CHANNEL, (buf, context) -> {
            String json=buf.readUtf(TicketPackets.MAX_JSON);
            context.queue(() -> receive(JSON.fromJson(json,TicketPackets.Message.class)));
        });
        TicketItemRenderer.init();
    }
    public static void send(TicketPackets.Message message) {
        FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer()); buf.writeUtf(JSON.toJson(message),TicketPackets.MAX_JSON);
        NetworkManager.sendToServer(TicketPackets.CHANNEL,buf);
    }
    public static void open(BlockPos pos) {
        TicketPackets.Message message=new TicketPackets.Message();message.pos=pos.asLong(); send(message);
    }
    private static void receive(TicketPackets.Message reply) {
        Minecraft client=Minecraft.getInstance();
        if ("error".equals(reply.action)) {
            reply.error=TicketMessages.text(reply.error).getString();
            if(client.screen instanceof TicketShopScreen shop) shop.response(reply);
            else if(client.screen instanceof TicketServiceScreen service) service.response(reply);
            else if(client.screen instanceof TicketAdminScreen admin) admin.error(reply.error);
            else if(client.player!=null) client.player.displayClientMessage(ComponentHelper.literal(reply.error),false);
        } else if("paid".equals(reply.action)) {
            // 玩家已经关掉页面时，成功回包也不能重新打开交易界面。
            if(client.screen instanceof TicketShopScreen shop && shop.matches(reply))shop.response(reply);
            else if(client.screen instanceof TicketServiceScreen service) service.response(reply);
        } else if("snapshot".equals(reply.action)) {
            if(client.screen instanceof TicketShopScreen shop && shop.matches(reply)) shop.response(reply);
            else if(client.screen instanceof TicketAdminScreen admin && admin.matches(reply)) admin.saved(reply);
            else if (reply.management) client.setScreen(new TicketAdminScreen(reply, null));
            else client.setScreen(switch (reply.kind) {
                case 1 -> new CardVendingScreen(reply);
                case 2 -> new FareAdjustmentScreen(reply);
                default -> saleScreen(reply);
            });
        } else if("quote".equals(reply.action)) {
            if(client.screen instanceof TicketShopScreen shop && shop.matches(reply)) shop.response(reply);
            else if(client.screen instanceof TicketServiceScreen service) service.response(reply);
        } else if("choose".equals(reply.action)) {
            List<ConfigChoiceScreen.Choice> choices=new ArrayList<>();
            for(int index:reply.choices) {
                Leg leg=reply.card.product.legs.get(index);
                choices.add(new ConfigChoiceScreen.Choice(Integer.toString(index),ComponentHelper.translatable("ui.fangsu.ticketing.leg",index+1,leg.network,leg.allowance).getString()));
            }
            client.setScreen(new ConfigChoiceScreen(null,"ui.fangsu.ticketing.select_leg",choices,value -> {
                TicketPackets.Message message=new TicketPackets.Message();message.action="leg";message.session=reply.session;message.pos=reply.pos;message.leg=Integer.parseInt(value);send(message);
            }));
        }
    }
    private static net.minecraft.client.gui.screens.Screen saleScreen(TicketPackets.Message reply) {
        var products = reply.config.saleProducts(reply.catalogue, 0);
        return !products.isEmpty() && products.stream().allMatch(Product::requiresStations)
                ? new PointToPointScreen(reply) : new TicketShopScreen(reply);
    }
    private TicketClient() { }
}
