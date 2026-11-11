package com.fangsu.items;

import com.fangsu.utils.RegisterUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

/** 站台施工、站台参数与屏蔽门单元编辑各用一个物品，选轨由独立节点工具负责。 */
public class ItemPlatformTool extends Item {
    public enum Role { BUILD, CONFIGURE, SCREEN_DOOR }
    public final Role role;
    public ItemPlatformTool(Role role) {
        super(RegisterUtil.tabProps(new Item.Properties().stacksTo(1)));
        this.role=role;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        if(level.isClientSide&&hand==InteractionHand.MAIN_HAND)com.fangsu.platform.CurvedPlatformClient.useTool(role,player);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if(context.getLevel().isClientSide&&context.getHand()==InteractionHand.MAIN_HAND)com.fangsu.platform.CurvedPlatformClient.useToolOn(role,context.getPlayer(),context.getClickedPos());
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) {
        RegisterUtil.addDescTooltip(tooltip,switch(role){case BUILD->"item.fangsu.curved_platform_tool.desc";case CONFIGURE->"item.fangsu.platform_configurator.desc";case SCREEN_DOOR->"item.fangsu.screendoor_configurator.desc";});
    }
}
