package com.fangsu.mixin;
import com.fangsu.turnout.TurnoutTools;
import mtr.render.RenderTrains;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** 让原版 MTR 也把节点标记物品当作节点相关工具，显示未连轨的节点。 */
@Mixin(value=RenderTrains.class,remap=false)
public class TurnoutRailToolMixin {
    @Inject(method="isHoldingRailRelated",at=@At("RETURN"),cancellable=true,remap=false)
    private static void fangsu$markerNodes(Player player,CallbackInfoReturnable<Boolean> callback){if(TurnoutTools.showNodes(player))callback.setReturnValue(true);}
}
