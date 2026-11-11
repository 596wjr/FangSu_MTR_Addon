package com.fangsu.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** 创造模式允许覆盖冲突，世界边界、区块和保护权限仍由服务器核对。 */
public final class PlatformPlacementPolicy {
    public static boolean force(ServerPlayer player){return player.isCreative();}
    public static boolean canEdit(ServerPlayer player){return !player.isSpectator()&&player.getAbilities().mayBuild;}
    public static boolean accessible(ServerLevel level,ServerPlayer player,BlockPos pos){return canEdit(player)&&!level.isOutsideBuildHeight(pos)&&level.hasChunkAt(pos)&&level.mayInteract(player,pos);}
    private PlatformPlacementPolicy(){ }
}
