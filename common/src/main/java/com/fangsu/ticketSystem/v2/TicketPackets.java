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
        public long pos, revision;
        public boolean admin;
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
        String id = UUID.randomUUID().toString(); BlockPos pos; String dimension; long until; TicketTransactions.Quote quote;
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
                try { handle(player, JSON.fromJson(payload, Message.class)); }
                catch (RuntimeException ex) {
                    Message reply = new Message(); reply.action = "error"; reply.error = ex.getMessage() == null ? "票务请求无效" : ex.getMessage();
                    send(player, reply);
                    Main.LOGGER.debug("票务请求被拒绝: {}", reply.error);
                }
            });
        });
    }
    public static void send(ServerPlayer player, Message message) {
        String json = JSON.toJson(message); require(json.length() <= MAX_JSON, "票务数据过大，请减少目录条目");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer()); buf.writeUtf(json, MAX_JSON);
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
    }
    private static void nearby(ServerPlayer player, BlockPos pos) {
        Level world = player.getCommandSenderWorld();
        require(world.isLoaded(pos) && player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5) <= 64, "请靠近票务设备");
    }
    public static void open(ServerPlayer player, BlockPos pos) {
        nearby(player, pos); Level world = player.getCommandSenderWorld();
        if (world.getBlockEntity(pos) == null && world.getBlockState(pos).getBlock() instanceof BlockTicketMachine
                && world.getBlockState(pos).getValue(BlockTicketMachine.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
            world.setBlockEntity(new BlockEntityTicketMachine(pos, world.getBlockState(pos)));
        require(world.getBlockEntity(pos) instanceof BlockEntityTicketMachine || player.hasPermissions(2)
                && world.getBlockEntity(pos) instanceof BlockEntityTicketBarrier, "设备不存在或无管理权限");
        Session session = new Session(pos, world); sessions(player).put(player.getUUID(), session);
        Station at = TicketStations.at(world, pos);
        if (at != null && TicketCards.isNative(player.getMainHandItem())) TicketCards.write(player.getMainHandItem(), TicketCards.read(player.getMainHandItem(), at));
        snapshot(player, session);
    }
    private static void snapshot(ServerPlayer player, Session session) {
        TicketGate.refresh(player.getServer());
        Message reply = new Message(); reply.action = "snapshot"; reply.pos = session.pos.asLong(); reply.session = session.id;
        reply.catalogue = TicketWorldData.get(player.getServer()).catalogue; reply.admin = player.hasPermissions(2);
        var be = player.getCommandSenderWorld().getBlockEntity(session.pos);
        if (be instanceof BlockEntityTicketMachine machine) { reply.config = machine.ticketConfig; reply.kind = machine.kind(); }
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
        require(message != null && message.action != null, "空请求");
        BlockPos pos = BlockPos.of(message.pos); nearby(player, pos);
        if ("open".equals(message.action)) { open(player, pos); return; }
        Session session = sessions(player).get(player.getUUID());
        require(session != null && session.id.equals(message.session) && session.pos.equals(pos)
                && session.dimension.equals(player.getCommandSenderWorld().dimension().location().toString()), "会话已失效，请重新打开设备");
        var be = player.getCommandSenderWorld().getBlockEntity(pos);
        if ("leg".equals(message.action)) { require(be instanceof BlockEntityTicketBarrier, "闸机不存在"); sessions(player).remove(player.getUUID()); TicketGate.selected(player,pos,message.leg); return; }
        if ("save".equals(message.action)) {
            require(player.hasPermissions(2), "仅管理员可修改票务配置");
            TicketWorldData data = TicketWorldData.get(player.getServer());
            require(message.catalogue != null && message.catalogue.revision == data.catalogue.revision, "目录已变化，请重新打开后编辑");
            validateCatalogue(message.catalogue, data.catalogue);
            if (be instanceof BlockEntityTicketMachine machine) {
                require(message.config != null, "缺少设备配置"); message.config.validate(message.catalogue);
                machine.configure(message.config);
            } else if (be instanceof BlockEntityTicketBarrier gate) {
                require(message.gate != null, "缺少闸机配置"); gate.configureTickets(message.gate, message.catalogue);
            } else throw new IllegalArgumentException("设备不存在");
            data.catalogue.networks = message.catalogue.networks; data.catalogue.products = message.catalogue.products; data.catalogue.unions = message.catalogue.unions; data.changed();
            if (be instanceof BlockEntityTicketBarrier gate) TicketGate.bind(player.getCommandSenderWorld(),pos,gate.ticketSettings());
            session.quote = null; snapshot(player,session); return;
        }
        require(be instanceof BlockEntityTicketMachine, "仅交易设备支持该操作");
        BlockEntityTicketMachine machine = (BlockEntityTicketMachine) be;
        if ("quote".equals(message.action)) {
            require(message.request != null, "缺少交易选项"); session.quote = TicketTransactions.quote(player,machine,message.request);
            Message reply = new Message(); reply.action = "quote"; reply.offer = session.quote; reply.session = session.id; reply.pos = pos.asLong(); send(player,reply);
        } else if ("pay".equals(message.action)) {
            TicketTransactions.Quote quote = session.quote; session.quote = null;
            require(quote != null && quote.id.equals(message.quote), "报价已使用或失效");
            TicketTransactions.execute(player,machine,quote); snapshot(player,session);
        } else throw new IllegalArgumentException("未知票务操作");
    }
    private static void validateCatalogue(Catalogue proposed, Catalogue old) {
        require(proposed.networks != null && proposed.products != null && proposed.networks.size() <= 128 && proposed.products.size() <= 512, "目录条目过多");
        require(proposed.networks.keySet().containsAll(old.networks.keySet()) && proposed.products.keySet().containsAll(old.products.keySet()), "请停用条目，不要删除已有ID");
        for (var entry : proposed.networks.entrySet()) {
            Network network = entry.getValue();
            require(network != null && entry.getKey().equals(network.id) && network.id.matches("[a-z0-9_.:-]{1,128}") && network.name != null
                    && !network.name.isBlank() && network.name.length() <= 128 && network.minimumBalance >= 0 && network.rule != null, "网络配置无效");
            network.rule.validate(); Network previous = old.networks.get(network.id);
            if (previous != null) network.rule.revision = previous.rule.revision + (JSON.toJson(previous.rule).equals(JSON.toJson(network.rule)) ? 0 : 1);
        }
        Network defaultNetwork = proposed.networks.get(DEFAULT_NETWORK);
        require(defaultNetwork != null && defaultNetwork.rule.mode == Pricing.MTR && defaultNetwork.enabled, "默认MTR计费网络必须保持启用及MTR计费");
        defaultNetwork.name = "默认MTR计费";
        require(proposed.unions != null && proposed.unions.size() <= 128 && proposed.unions.keySet().containsAll(old.unions.keySet()), "请停用联合组，不要删除已有ID");
        for (var entry : proposed.unions.entrySet()) {
            NetworkUnion union = entry.getValue();
            require(union != null && entry.getKey().equals(union.id) && union.id.matches("[a-z0-9_.:-]{1,128}") && union.name != null
                    && !union.name.isBlank() && union.name.length() <= 128 && union.texture != null && union.texture.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png")
                    && union.networks != null && !union.networks.isEmpty() && union.networks.size() <= 128 && proposed.networks.keySet().containsAll(union.networks), "联合名称、贴图或成员网络无效");
        }
        for (var entry : proposed.products.entrySet()) { require(entry.getValue() != null && entry.getKey().equals(entry.getValue().id), "产品ID不一致"); entry.getValue().validate(proposed); }
    }
    private TicketPackets() { }
}
