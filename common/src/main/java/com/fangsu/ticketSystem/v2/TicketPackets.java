package com.fangsu.ticketSystem.v2;

import com.fangsu.Main;
import com.fangsu.blockEntities.*;
import com.fangsu.blocks.*;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 有界消息及一次性交易会话；不在共享类引用客户端Screen。 */
public final class TicketPackets {
    public static final ResourceLocation CHANNEL = new ResourceLocation("fangsu", "ticketing_v2");
    public static final int MAX_JSON = 524288;
    public static final class Message {
        public String action = "open", session = "", quote = "", error;
        public long pos, revision, requestId;
        public Station station;
        public Map<String,Integer> fares;
        public boolean admin, management;
        public int kind, leg = -1;
        public Catalogue catalogue;
        public TicketMachineConfig config;
        public TicketTransactions.Request request;
        public TicketTransactions.Quote offer;
        public Map<String,String> gate;
        public Card card;
        public List<Integer> choices;
    }
    private static final class Session {
        String id = UUID.randomUUID().toString(); BlockPos pos; String dimension; long until; boolean management; TicketTransactions.Quote quote;
        Session(BlockPos pos, Level world) { this.pos = pos; this.dimension=world.dimension().location().toString(); until = System.currentTimeMillis()+600000; }
    }
    private static final Map<MinecraftServer,Map<UUID,Session>> SESSIONS = new WeakHashMap<>();
    private static Map<UUID,Session> sessions(ServerPlayer player) {
        Map<UUID,Session> sessions = SESSIONS.computeIfAbsent(player.getServer(), s -> new HashMap<>());
        sessions.entrySet().removeIf(e -> e.getValue().until < System.currentTimeMillis()); return sessions;
    }
    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, CHANNEL, (buf, context) -> {
            final String payload = buf.readUtf(MAX_JSON);
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                Message request = null;
                try { request = JSON.fromJson(payload, Message.class); handle(player, request); }
                catch (RuntimeException ex) {
                    Message reply = new Message(); reply.action = "error"; reply.error = ex.getMessage() == null ? "msg.fangsu.ticketing.invalid_request" : ex.getMessage();
                    if(request!=null){reply.pos=request.pos;reply.session=request.session;reply.requestId=request.requestId;}
                    send(player, reply);
                    Main.LOGGER.debug("票务请求被拒绝: {}", reply.error);
                }
            });
        });
    }
    public static void send(ServerPlayer player, Message message) {
        String json = JSON.toJson(message); require(json.length() <= MAX_JSON, "msg.fangsu.ticketing.message_size");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer()); buf.writeUtf(json, MAX_JSON);
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
    }
    private static void nearby(ServerPlayer player, BlockPos pos) {
        Level world = player.getCommandSenderWorld();
        require(world.isLoaded(pos) && player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5) <= 64, "msg.fangsu.ticketing.move_closer");
    }
    public static void open(ServerPlayer player, BlockPos pos) {
        open(player, pos, player.getCommandSenderWorld().getBlockEntity(pos) instanceof BlockEntityTicketBarrier);
    }
    public static void open(ServerPlayer player, BlockPos pos, boolean management) {
        nearby(player, pos); Level world = player.getCommandSenderWorld();
        if (management) require(player.hasPermissions(2) && (player.getMainHandItem().is(com.fangsu.items.ModItems.ITEM_WRENCH.get())
                || player.getOffhandItem().is(com.fangsu.items.ModItems.ITEM_WRENCH.get())), "msg.fangsu.ticketing.admin_wrench_manage");
        if (world.getBlockEntity(pos) == null && world.getBlockState(pos).getBlock() instanceof BlockTicketMachine
                && world.getBlockState(pos).getValue(BlockTicketMachine.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
            world.setBlockEntity(new BlockEntityTicketMachine(pos, world.getBlockState(pos)));
        require(world.getBlockEntity(pos) instanceof BlockEntityTicketMachine || player.hasPermissions(2)
                && world.getBlockEntity(pos) instanceof BlockEntityTicketBarrier, "msg.fangsu.ticketing.device_permission");
        Session session = new Session(pos, world); sessions(player).put(player.getUUID(), session);
        session.management = management;
        Station at = TicketStations.at(world, pos);
        if (at != null && TicketCards.isNative(player.getMainHandItem())) TicketCards.write(player.getMainHandItem(), TicketCards.read(player.getMainHandItem(), at));
        snapshot(player, session);
    }
    private static void snapshot(ServerPlayer player, Session session) {
        TicketGate.refresh(player.getServer());
        Message reply = new Message(); reply.action = "snapshot"; reply.pos = session.pos.asLong(); reply.session = session.id;
        reply.catalogue = TicketWorldData.get(player.getServer()).catalogue; reply.admin = player.hasPermissions(2);
        reply.management = session.management;
        var be = player.getCommandSenderWorld().getBlockEntity(session.pos);
        if (be instanceof BlockEntityTicketMachine machine) {
            reply.config = machine.ticketConfig; reply.kind = machine.kind();
            reply.station = TicketStations.at(player.getCommandSenderWorld(),session.pos);
            reply.fares = new LinkedHashMap<>(); Network network=reply.catalogue.networks.get(reply.config.network);
            if(reply.station!=null && network!=null)for(Station destination:reply.catalogue.stations(reply.config.network))
                if(reply.station.dimension.equals(destination.dimension))try{reply.fares.put(destination.key,network.rule.fare(reply.station,destination));}
                catch(RuntimeException ex){Main.LOGGER.debug("车站票价不可用: {}",destination.key,ex);}
        }
        if (be instanceof BlockEntityTicketBarrier gate) reply.gate = gate.ticketSettings();
        if (TicketCards.isNative(player.getMainHandItem())) reply.card = TicketCards.read(player.getMainHandItem(), TicketStations.at(player.getCommandSenderWorld(),session.pos));
        send(player, reply);
    }
    public static void choose(ServerPlayer player, BlockPos pos, Card card, List<Integer> choices) {
        Session session = new Session(pos, player.getCommandSenderWorld()); sessions(player).put(player.getUUID(), session);
        Message reply = new Message(); reply.action = "choose"; reply.pos = pos.asLong(); reply.session = session.id; reply.card = card; reply.choices = choices;
        send(player, reply);
    }
    private static void handle(ServerPlayer player, Message message) {
        require(message != null && message.action != null, "msg.fangsu.ticketing.empty_request");
        BlockPos pos = BlockPos.of(message.pos); nearby(player, pos);
        if ("open".equals(message.action)) { open(player, pos, message.management || player.getCommandSenderWorld().getBlockEntity(pos) instanceof BlockEntityTicketBarrier); return; }
        Session session = sessions(player).get(player.getUUID());
        require(session != null && session.id.equals(message.session) && session.pos.equals(pos)
                && session.dimension.equals(player.getCommandSenderWorld().dimension().location().toString()), "msg.fangsu.ticketing.session_expired");
        var be = player.getCommandSenderWorld().getBlockEntity(pos);
        if ("leg".equals(message.action)) { require(be instanceof BlockEntityTicketBarrier, "msg.fangsu.ticketing.gate_missing"); sessions(player).remove(player.getUUID()); TicketGate.selected(player,pos,message.leg); return; }
        if ("save".equals(message.action)) {
            require(player.hasPermissions(2) && session.management, "msg.fangsu.ticketing.admin_wrench_required");
            TicketWorldData data = TicketWorldData.get(player.getServer());
            require(message.catalogue != null && message.catalogue.revision == data.catalogue.revision, "msg.fangsu.ticketing.catalogue_changed");
            validateCatalogue(message.catalogue, data.catalogue);
            if (be instanceof BlockEntityTicketMachine machine) {
                require(message.config != null, "msg.fangsu.ticketing.missing_device_config"); message.config.validate(message.catalogue, machine.kind());
                machine.configure(message.config);
            } else if (be instanceof BlockEntityTicketBarrier gate) {
                require(message.gate != null, "msg.fangsu.ticketing.missing_gate_config"); gate.configureTickets(message.gate, message.catalogue);
            } else throw new IllegalArgumentException("msg.fangsu.ticketing.device_missing");
            data.catalogue.networks = message.catalogue.networks; data.catalogue.products = message.catalogue.products; data.catalogue.unions = message.catalogue.unions; data.changed();
            if (be instanceof BlockEntityTicketBarrier gate) TicketGate.bind(player.getCommandSenderWorld(),pos,gate.ticketSettings());
            session.quote = null; snapshot(player,session); return;
        }
        require(be instanceof BlockEntityTicketMachine, "msg.fangsu.ticketing.transaction_device_required");
        BlockEntityTicketMachine machine = (BlockEntityTicketMachine) be;
        if ("quote".equals(message.action)) {
            require(message.request != null, "msg.fangsu.ticketing.missing_options"); session.quote = TicketTransactions.quote(player,machine,message.request);
            Message reply = new Message(); reply.action = "quote"; reply.offer = session.quote; reply.session = session.id; reply.pos = pos.asLong(); reply.requestId=message.requestId; send(player,reply);
        } else if ("pay".equals(message.action)) {
            TicketTransactions.Quote quote = session.quote; session.quote = null;
            require(quote != null && quote.id.equals(message.quote), "msg.fangsu.ticketing.quote_used");
            TicketTransactions.execute(player,machine,quote);
            Message reply=new Message();reply.action="paid";reply.session=session.id;reply.pos=pos.asLong();reply.requestId=message.requestId;send(player,reply);
        } else throw new IllegalArgumentException("msg.fangsu.ticketing.unknown_action");
    }
    private static void validateCatalogue(Catalogue proposed, Catalogue old) {
        require(proposed.networks != null && proposed.products != null && proposed.networks.size() <= 128 && proposed.products.size() <= 512, "msg.fangsu.ticketing.catalogue_size");
        require(proposed.networks.keySet().containsAll(old.networks.keySet()) && proposed.products.keySet().containsAll(old.products.keySet()), "msg.fangsu.ticketing.disable_entries");
        for (var entry : proposed.networks.entrySet()) {
            Network network = entry.getValue();
            require(network != null && entry.getKey().equals(network.id) && network.id.matches("[a-z0-9_.:-]{1,128}") && network.name != null
                    && !network.name.isBlank() && network.name.length() <= 128 && network.minimumBalance >= 0 && network.rule != null, "msg.fangsu.ticketing.invalid_network");
            network.rule.validate(); Network previous = old.networks.get(network.id);
            if (previous != null) network.rule.revision = previous.rule.revision + (JSON.toJson(previous.rule).equals(JSON.toJson(network.rule)) ? 0 : 1);
        }
        Network defaultNetwork = proposed.networks.get(DEFAULT_NETWORK);
        require(defaultNetwork != null && defaultNetwork.rule.mode == Pricing.MTR && defaultNetwork.enabled, "msg.fangsu.ticketing.default_network_rule");
        defaultNetwork.name = "默认MTR计费";
        require(proposed.unions != null && proposed.unions.size() <= 128 && proposed.unions.keySet().containsAll(old.unions.keySet()), "msg.fangsu.ticketing.disable_unions");
        for (var entry : proposed.unions.entrySet()) {
            NetworkUnion union = entry.getValue();
            require(union != null && entry.getKey().equals(union.id) && union.id.matches("[a-z0-9_.:-]{1,128}") && union.name != null
                    && !union.name.isBlank() && union.name.length() <= 128 && union.texture != null && union.texture.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png")
                    && union.networks != null && !union.networks.isEmpty() && union.networks.size() <= 128 && proposed.networks.keySet().containsAll(union.networks), "msg.fangsu.ticketing.invalid_union");
        }
        for (var entry : proposed.products.entrySet()) { require(entry.getValue() != null && entry.getKey().equals(entry.getValue().id), "msg.fangsu.ticketing.product_id_mismatch"); entry.getValue().validate(proposed); }
    }
    private TicketPackets() { }
}
