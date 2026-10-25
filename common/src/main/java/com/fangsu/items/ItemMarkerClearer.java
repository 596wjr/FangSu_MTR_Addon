package com.fangsu.items;

import com.fangsu.turnout.marker.NodeMarkerInteractions;
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

/**
 * 节点标记清除工具：右键（方块或空气）清除当前维度的全部私有节点标记。
 * <p>
 * 主流程走 {@link NodeMarkerInteractions} 的 RIGHT_CLICK_BLOCK / RIGHT_CLICK_ITEM 事件
 * （MTR4 节点方块会吞掉右键并返回 FAIL，物品的 use/useOn 根本不会被调用）；
 * 下面的 use/useOn 只作兜底，与事件共用同一份清除逻辑。
 */
public class ItemMarkerClearer extends Item {

    public ItemMarkerClearer() {
        super(RegisterUtil.tabProps(new Item.Properties().stacksTo(1)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        RegisterUtil.addDescTooltip(tooltip, "item.fangsu.node_marker_clearer.desc");
    }

    /** 兜底路径：对空气右键（事件通常先处理并结束交互） */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        NodeMarkerInteractions.clearAndNotify(player, level);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    /** 兜底路径：对方块右键 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        NodeMarkerInteractions.clearAndNotify(context.getPlayer(), context.getLevel());
        return InteractionResult.SUCCESS;
    }
}
