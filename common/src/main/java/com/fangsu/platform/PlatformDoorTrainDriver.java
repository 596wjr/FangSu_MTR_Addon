package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityAutoPlatformComponent;
import dev.architectury.event.events.common.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** MTR3 TrainMixin 已在服务端驱动门扇；同组固定玻璃与吊板只同步指示状态。 */
public final class PlatformDoorTrainDriver {
    private static final Map<ServerLevel,Set<BlockPos>> HOSTS=new HashMap<>();private static boolean installed;
    public static void observe(ServerLevel level,BlockPos pos){HOSTS.computeIfAbsent(level,k->new HashSet<>()).add(pos.immutable());}
    public static void register(){if(installed)return;installed=true;
        LifecycleEvent.SERVER_STOPPED.register(server->HOSTS.keySet().removeIf(w->w.getServer()==server));
        TickEvent.SERVER_POST.register(server->{for(var row:new ArrayList<>(HOSTS.entrySet())){ServerLevel level=row.getKey();if(level.getServer()!=server)continue;Map<UUID,Boolean> open=new HashMap<>();
            row.getValue().removeIf(p->!level.hasChunkAt(p)||!(level.getBlockEntity(p) instanceof BlockEntityAutoPlatformComponent));
            for(BlockPos p:row.getValue())if(level.getBlockEntity(p) instanceof BlockEntityAutoPlatformComponent host&&host.group!=null&&host.components().stream().anyMatch(c->c.kind().equals("door")||c.kind().equals("door_proxy")))open.merge(host.group,host.getDoorTarget(),Boolean::logicalOr);
            for(BlockPos p:row.getValue())if(level.getBlockEntity(p) instanceof BlockEntityAutoPlatformComponent host&&host.components().stream().noneMatch(c->c.kind().equals("door")||c.kind().equals("door_proxy"))){host.setDoorTarget(open.getOrDefault(host.group,false));host.setDoorValue(0);}
            if(row.getValue().isEmpty())HOSTS.remove(level);
        }});
    }
    private PlatformDoorTrainDriver(){ }
}
