package com.fangsu.mixin;

import com.fangsu.data.hybrid.RailActionsModuleExtraSupplier;
import mtr.data.Rail;
import mtr.data.RailwayData;
import mtr.data.RailwayDataModuleBase;
import mtr.data.RailwayDataRailActionsModule;
import mtr.packet.PacketTrainDataGuiServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 暴露 MTR3 {@link RailwayDataRailActionsModule} 的私有队列/轨道表/世界，供混合构建器挂载构建动作
 * （照 NTE mtrsteamloco 的 RailwayDataRailActionsModuleMixin + RailActionsModuleExtraSupplier 蓝本）。
 * <p>
 * MTR 为 mojmap 环境，字段名即源码名，故 remap=false。
 * Forge 端不加载 mixin（mods.toml 无声明），由
 * {@link com.fangsu.data.hybrid.HybridSliceAction#attach} 的 instanceof 检测走反射回退。
 */
@Mixin(value = RailwayDataRailActionsModule.class, remap = false)
public class RailwayDataRailActionsModuleMixin extends RailwayDataModuleBase implements RailActionsModuleExtraSupplier {

	public RailwayDataRailActionsModuleMixin(RailwayData railwayData, Level world, Map<BlockPos, Map<BlockPos, Rail>> rails) {
		super(railwayData, world, rails);
	}

	@Shadow
	private List<Rail.RailActions> railActions = new ArrayList<>();

	@Override
	public List<Rail.RailActions> getRailActions() {
		return railActions;
	}

	@Override
	public Map<BlockPos, Map<BlockPos, Rail>> getRails() {
		return rails;
	}

	@Override
	public void sendUpdateS2C() {
		PacketTrainDataGuiServer.updateRailActionsS2C(world, railActions);
	}
}
