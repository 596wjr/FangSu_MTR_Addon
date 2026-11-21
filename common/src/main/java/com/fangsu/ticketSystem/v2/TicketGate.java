package com.fangsu.ticketSystem.v2;

import com.fangsu.blockEntities.BlockEntityTicketBarrier;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 刷闸和车站绑定只在服务器线程执行。 */
public final class TicketGate {
    public static String network(Map<String,String> config) { return config.getOrDefault("ticketNetwork", DEFAULT_NETWORK); }
    /** 从MTR车站数据库刷新绑定位置；不访问或强制加载对应区块。 */
    public static void refresh(net.minecraft.server.MinecraftServer server) {
        TicketWorldData data=TicketWorldData.get(server);
        Map<String,Level> worlds=new HashMap<>();for(Level world:server.getAllLevels())worlds.put(world.dimension().location().toString(),world);
        boolean changed=false;
        var iterator=data.catalogue.bindings.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();Binding binding=entry.getValue();Level world=worlds.get(binding.station.dimension);
            if(world==null)continue;
            String key=entry.getKey();BlockPos pos=BlockPos.of(Long.parseLong(key.substring(key.lastIndexOf('/')+1)));
            Station current=TicketStations.at(world,pos);
            if(current==null){iterator.remove();changed=true;}
            else if(!JSON.toJson(current).equals(JSON.toJson(binding.station))){binding.station=current;changed=true;}
        }
        if(changed)data.changed();
    }
    public static void bind(Level world, BlockPos pos, Map<String,String> config) {
        if (world.isClientSide || world.getServer() == null) return;
        if (!(world.getBlockEntity(pos) instanceof BlockEntityTicketBarrier)) return;
        TicketWorldData data = TicketWorldData.get(world.getServer());
        Station station = TicketStations.at(world, pos);
        String key = world.dimension().location() + "/" + pos.asLong();
        if (station == null) {
            if (data.catalogue.bindings.remove(key) != null) data.changed();
            return;
        }
        Binding binding = new Binding(); binding.station = station; binding.networks.add(network(config));
        if (Boolean.parseBoolean(config.getOrDefault("ticketTransfer", "false")))
            binding.networks.add(config.getOrDefault("ticketTargetNetwork", DEFAULT_NETWORK));
        if (!JSON.toJson(binding).equals(JSON.toJson(data.catalogue.bindings.get(key)))) {
            data.catalogue.bindings.put(key, binding); data.changed();
        }
    }
    public static void unbind(Level world, BlockPos pos) {
        if (world.isClientSide || world.getServer() == null) return;
        TicketWorldData data = TicketWorldData.get(world.getServer());
        if (data.catalogue.bindings.remove(world.dimension().location()+"/"+pos.asLong()) != null) data.changed();
    }
    public static boolean use(Level world, BlockPos pos, Player player, Map<String,String> config, Runnable update, int selectedLeg) {
        try {
            bind(world, pos, config);
            Station station = TicketStations.at(world, pos); require(station != null, "msg.fangsu.ticketing.gate_outside_station");
            ItemStack stack = player.getMainHandItem(); Card original = TicketCards.read(stack, station);
            require(Arrays.asList(config.getOrDefault("ticketKinds", "SINGLE,IC,ITINERARY,DAY").split(",")).contains(original.product.kind.name()), "msg.fangsu.ticketing.gate_ticket_type");
            Catalogue catalogue = TicketWorldData.get(world.getServer()).catalogue;
            Network network = catalogue.networks.get(network(config)); require(network != null, "msg.fangsu.ticketing.network_missing");
            boolean transfer=Boolean.parseBoolean(config.getOrDefault("ticketTransfer","false"));
            boolean exiting=Boolean.parseBoolean(config.getOrDefault("isExit","false"));
            TicketEngine.Result result;
            if (transfer) {
                Network target = catalogue.networks.get(config.getOrDefault("ticketTargetNetwork", ""));
                require(target != null, "msg.fangsu.ticketing.transfer_network_missing");
                Card afterExit = TicketEngine.exit(original, network.id, station).card();
                List<Integer> matches = TicketEngine.eligible(afterExit, target.id, station);
                if (selectedLeg < 0 && matches.size() > 1) {
                    require(world.getBlockEntity(pos) instanceof BlockEntityTicketBarrier, "msg.fangsu.ticketing.fangsu_gate_required");
                    TicketPackets.choose((ServerPlayer) player, pos, afterExit, matches); return false;
                }
                result = TicketEngine.transfer(original, network, target, station, selectedLeg, System.currentTimeMillis());
            } else if (exiting) {
                result = TicketEngine.exit(original, network.id, station);
            } else {
                List<Integer> matches = TicketEngine.eligible(original, network.id, station);
                if (selectedLeg < 0 && matches.size() > 1) {
                    require(world.getBlockEntity(pos) instanceof BlockEntityTicketBarrier, "msg.fangsu.ticketing.fangsu_gate_required");
                    TicketPackets.choose((ServerPlayer) player, pos, original, matches); return false;
                }
                result = TicketEngine.prepareEnter(original, network, station, selectedLeg, System.currentTimeMillis());
            }
            if (result.consumed()) stack.shrink(1); else TicketCards.write(stack, result.card());
            config.put("ticketNetwork", network.id); config.put("isOpen", "true"); update.run();
            player.getInventory().setChanged();
            String stationName=TicketStations.formatName(station);
            Component message;
            if(original.product.kind==Kind.IC){
                // IC卡沿用原版进出闸翻译，余额来自票卡自身而非玩家账户。
                message=transfer?ComponentHelper.translatable("msg.fangsu.ticketing.transferred_ic",stationName,result.fare(),result.card().balance)
                        :exiting?ComponentHelper.translatable("gui.mtr.exit_barrier",stationName,result.fare(),result.card().balance)
                        :ComponentHelper.translatable("gui.mtr.enter_barrier",stationName,result.card().balance);
            }else{
                int displayedFare=original.product.kind==Kind.DAY?0:result.fare();
                message=transfer?ComponentHelper.translatable("msg.fangsu.ticketing.transferred",stationName,displayedFare)
                        :exiting?ComponentHelper.translatable("msg.fangsu.ticketing.exited",stationName,displayedFare)
                        :ComponentHelper.translatable("msg.fangsu.ticketing.entered",stationName);
            }
            player.displayClientMessage(message,true);
            return true;
        } catch (RuntimeException ex) {
            player.displayClientMessage(ex.getMessage()==null?com.fangsu.mappings.ComponentHelper.translatable("msg.fangsu.ticketing.invalid_card"):TicketMessages.error(ex), true);
            return false;
        }
    }
    public static void selected(ServerPlayer player, BlockPos pos, int leg) {
        if (!player.getCommandSenderWorld().isLoaded(pos) || player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64) return;
        if (player.getCommandSenderWorld().getBlockEntity(pos) instanceof BlockEntityTicketBarrier barrier) barrier.ticketSelected(player, leg);
    }
    private TicketGate() { }
}
