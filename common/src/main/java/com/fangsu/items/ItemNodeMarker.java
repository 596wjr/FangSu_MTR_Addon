package com.fangsu.items;

import com.fangsu.blocks.BlockMultiDirectionNode;
import com.fangsu.turnout.marker.MarkerColor;
import com.fangsu.turnout.marker.NodeMarkerInteractions;
import com.fangsu.turnout.marker.NodeMarkerStore;
import com.fangsu.utils.RegisterUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 节点标记物品：右键轨道节点在该节点上方留一个私有彩色圆盘标记，Shift+右键移除。
 * <p>
 * 标记是客户端私有数据（{@link NodeMarkerStore}），服务端不存任何东西，也不需要网络包。
 * 主流程走 {@link NodeMarkerInteractions} 的 RIGHT_CLICK_BLOCK 事件（MTR4 节点方块会自己吞掉
 * 右键并返回 FAIL，物品 useOn 根本不会被调用）；下面的 useOn 只作兜底，两边共用同一份逻辑。
 */
public class ItemNodeMarker extends Item {

    /** 本物品放置的标记颜色 */
    private final MarkerColor color;
    /** 物品介绍 lang 键（红/蓝各一行） */
    private final String descKey;

    public ItemNodeMarker(MarkerColor color, String descKey) {
        super(RegisterUtil.tabProps(new Item.Properties().stacksTo(1)));
        this.color = color;
        this.descKey = descKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        RegisterUtil.addDescTooltip(tooltip, descKey);
    }

    /**
     * 兜底路径：交互事件（{@link NodeMarkerInteractions}）已覆盖主流程，某些平台/加载顺序下
     * 物品 useOn 仍可能先被调用，这里复用同一份逻辑，重复触发不会二次写库。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        if (!isRailNode(level.getBlockState(pos).getBlock())) return InteractionResult.PASS;

        NodeMarkerInteractions.apply(context.getPlayer(), level, pos, color);
        return InteractionResult.SUCCESS;
    }

    /**
     * 目标方块是否为可标记的轨道节点：MTR 原版 {@code BlockNode} 全家族
     * （方速万向节点 {@link BlockMultiDirectionNode} 也继承自它），用 instanceof 判断，
     * 不依赖具体注册名与注册顺序。
     */
    public static boolean isRailNode(Block block) {
        return block instanceof mtr.block.BlockNode || block instanceof BlockMultiDirectionNode;
    }
}
