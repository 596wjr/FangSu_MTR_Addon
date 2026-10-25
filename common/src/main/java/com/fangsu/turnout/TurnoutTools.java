package com.fangsu.turnout;
import com.fangsu.items.ItemNodeMarker;
import com.fangsu.items.ItemTurnout;
import com.fangsu.items.ItemMarkerClearer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
/** 原版 MTR 节点渲染与方速节点线框共用手持判断，主副手均有效。 */
public final class TurnoutTools {
    private TurnoutTools() { }
    private static boolean relevant(ItemStack stack){return !stack.isEmpty()&&(stack.getItem() instanceof ItemNodeMarker||stack.getItem() instanceof ItemTurnout||stack.getItem() instanceof ItemMarkerClearer);}
    public static boolean showNodes(Player player){return player!=null&&(relevant(player.getMainHandItem())||relevant(player.getOffhandItem()));}
}
