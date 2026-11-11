package com.fangsu.items;

import com.fangsu.utils.RegisterUtil;
import mtr.data.RailwayData;
import mtr.item.ItemNodeModifierSelectableBlockBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

/** 继承与混合构建器相同的基类，让 MTR 在手持时显示轨道节点。只选择两个端点，不创建轨道。 */
public final class ItemPlatformRailSelector extends ItemNodeModifierSelectableBlockBase {
    public ItemPlatformRailSelector(){super(false,0,1);}
    //#if MC_VERSION < 11903
    //$$ @Override public void fillItemCategory(net.minecraft.world.item.CreativeModeTab tab,net.minecraft.core.NonNullList<ItemStack> items){
    //$$     if(tab==com.fangsu.creativeTabs.ModCreativeTabs.FANGSU_MAIN||tab==net.minecraft.world.item.CreativeModeTab.TAB_SEARCH)items.add(new ItemStack(this));
    //$$ }
    //#endif
    @Override public InteractionResult useOn(UseOnContext context) {
        if(context.getLevel().isClientSide&&context.getHand()==InteractionHand.MAIN_HAND)com.fangsu.platform.CurvedPlatformClient.click(context.getPlayer(),context.getClickedPos());
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        if(level.isClientSide&&hand==InteractionHand.MAIN_HAND&&player.isShiftKeyDown())com.fangsu.platform.CurvedPlatformClient.clearSelection(player);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) {
        RegisterUtil.addDescTooltip(tooltip,"item.fangsu.platform_rail_selector.desc");
    }
    @Override protected boolean onConnect(Player player,ItemStack stack,RailwayData data,BlockPos start,BlockPos end,int radius,int height){return true;}
}
