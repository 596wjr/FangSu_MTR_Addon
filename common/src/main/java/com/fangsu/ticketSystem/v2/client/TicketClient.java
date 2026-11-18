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
    public static void init() {
        ClientHooks.OPEN_TICKETING = TicketClient::open;
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
            if(client.screen instanceof TicketShopScreen shop) shop.response(reply);
            else if(client.screen instanceof TicketAdminScreen admin) admin.error(reply.error);
            else if(client.player!=null) client.player.displayClientMessage(ComponentHelper.literal(reply.error),false);
        } else if("snapshot".equals(reply.action)) {
            if(client.screen instanceof TicketShopScreen shop && shop.matches(reply)) shop.response(reply);
            else if(client.screen instanceof TicketAdminScreen admin && admin.matches(reply)) admin.saved(reply);
            else client.setScreen(reply.gate!=null ? new TicketAdminScreen(reply,null) : new TicketShopScreen(reply));
        } else if("quote".equals(reply.action)) {
            if(client.screen instanceof TicketShopScreen shop && shop.matches(reply)) shop.response(reply);
        } else if("choose".equals(reply.action)) {
            List<ConfigChoiceScreen.Choice> choices=new ArrayList<>();
            for(int index:reply.choices) {
                Leg leg=reply.card.product.legs.get(index);
                choices.add(new ConfigChoiceScreen.Choice(Integer.toString(index),"权益 "+(index+1)+" · "+leg.network+" · "+leg.allowance+"元"));
            }
            client.setScreen(new ConfigChoiceScreen(null,"选择联程权益",choices,value -> {
                TicketPackets.Message message=new TicketPackets.Message();message.action="leg";message.session=reply.session;message.pos=reply.pos;message.leg=Integer.parseInt(value);send(message);
            }));
        }
    }
    private TicketClient() { }
}
