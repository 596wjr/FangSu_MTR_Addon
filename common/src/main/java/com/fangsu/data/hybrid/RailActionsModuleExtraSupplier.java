package com.fangsu.data.hybrid;

import mtr.data.Rail;
import mtr.data.RailwayDataRailActionsModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * 混合构建器访问 MTR3 {@link RailwayDataRailActionsModule} 私有队列的接口（照 NTE
 * mtrsteamloco RailActionsModuleExtraSupplier 蓝本），由
 * {@link com.fangsu.mixin.RailwayDataRailActionsModuleMixin} 实现。
 * <p>
 * Fabric 端 mixin 生效后 module 实例实现本接口；Forge 端不加载 mixin
 * （mods.toml 无声明），由调用处 instanceof 检测后走反射回退。
 */
public interface RailActionsModuleExtraSupplier {

    /** 构建动作队列（照 markRailForBridge 直接 add） */
    List<Rail.RailActions> getRailActions();

    /** 轨道表（取 Rail 实例用，照 markRailForBridge 的 rails.get(pos1).get(pos2)） */
    Map<BlockPos, Map<BlockPos, Rail>> getRails();

    /** 队列变更后广播给所有玩家（客户端构建列表） */
    void sendUpdateS2C();
}
