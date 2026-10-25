package com.fangsu.turnout.marker;

import com.fangsu.Main;
import com.fangsu.items.ItemMarkerClearer;
import com.fangsu.items.ItemNodeMarker;
import com.fangsu.items.ModItems;
import com.fangsu.mappings.ComponentHelper;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * 节点标记的右键交互入口（标记放置 / 移除 / 清除工具）。
 * <p>
 * 为什么不能只靠 {@link Item#useOn}:MTR4 的轨道节点方块自己实现了 use()（onUse2），
 * 非刷子右键直接返回 FAIL，交互管理器拿到 FAIL 后不会再调用物品的 useOn，物品逻辑就永远不执行。
 * 这里改用 architectury 的 {@link InteractionEvent#RIGHT_CLICK_BLOCK}（对方块右键）与
 * {@link InteractionEvent#RIGHT_CLICK_ITEM}（对空气右键），在方块 use() 之前拦截。
 * <p>
 * 标记是客户端私有数据：只有客户端改 {@link NodeMarkerStore}，两端都返回 interruptTrue 结束交互，
 * 避免 MTR 的 FAIL 兜底与本模组其它路径重复处理；写库前先比较旧值，重复触发时不会二次落盘。
 */
public final class NodeMarkerInteractions {

    /** 非节点方块拒绝日志的上限（同一方块同一物品只打一次，且总量有上限，避免刷屏） */
    private static final int MAX_REJECT_LOGS = 32;
    private static final Set<String> REJECT_LOGGED = new HashSet<>();

    private NodeMarkerInteractions() {
    }

    public static void init() {
        InteractionEvent.RIGHT_CLICK_BLOCK.register(NodeMarkerInteractions::onRightClickBlock);
        // 对空气右键也要能清除标记（不点方块时走物品右键事件）
        InteractionEvent.RIGHT_CLICK_ITEM.register(NodeMarkerInteractions::onRightClickItem);
        Main.LOGGER.info("[fangsu][turnout][diag] NodeMarkerInteractions registered");
    }

    private static EventResult onRightClickBlock(Player player, InteractionHand hand, BlockPos pos, Direction face) {
        final ItemStack stack = player.getItemInHand(hand);
        //#if MC_VERSION >= 12000
        final Level level = player.level();
        //#else
        //$$ final Level level = player.level;
        //#endif

        // MTR 节点会先行消费非刷子右键，道岔物品也要进入其校验放置流程。
        if (stack.getItem() instanceof com.fangsu.items.ItemTurnout turnout
                && ItemNodeMarker.isRailNode(level.getBlockState(pos).getBlock())) {
            if (level.isClientSide) placeAtNode(turnout, player, hand, pos, face);
            return EventResult.interruptTrue();
        }

        // 清除工具：对任何方块（含 MTR 节点方块）都生效，清完直接结束交互
        if (stack.getItem() instanceof ItemMarkerClearer) {
            clearAndNotify(player, level);
            return EventResult.interruptTrue();
        }

        final MarkerColor color = markerColorOf(stack);
        // 不是标记物品就放行,不吞别人的右键
        if (color == null) return EventResult.pass();
        if (!ItemNodeMarker.isRailNode(level.getBlockState(pos).getBlock())) {
            logRejected(pos, stack, level);
            return EventResult.pass();
        }

        apply(player, level, pos, color);
        // 两端都结束交互：MTR 节点方块的 FAIL 兜底与其他路径不会重复处理同一次右键
        return EventResult.interruptTrue();
    }

    private static void placeAtNode(com.fangsu.items.ItemTurnout item,Player player,InteractionHand hand,BlockPos pos,Direction face) {
        net.minecraft.world.phys.HitResult current=net.minecraft.client.Minecraft.getInstance().hitResult;
        net.minecraft.world.phys.BlockHitResult hit=current instanceof net.minecraft.world.phys.BlockHitResult block&&block.getBlockPos().equals(pos)
                ?block:new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),face,pos,false);
        item.useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,hit));
    }

    /** 对空气右键：只处理清除工具，其它物品原样放行 */
    private static CompoundEventResult<ItemStack> onRightClickItem(Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ItemMarkerClearer)) return CompoundEventResult.pass();
        //#if MC_VERSION >= 12000
        clearAndNotify(player, player.level());
        //#else
        //$$ clearAndNotify(player, player.level);
        //#endif
        return CompoundEventResult.interruptTrue(stack);
    }

    /** 标记物品 → 颜色；其它物品返回 null */
    private static MarkerColor markerColorOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        final Item item = stack.getItem();
        if (item == ModItems.ITEM_NODE_MARKER_RED.get()) return MarkerColor.RED;
        if (item == ModItems.ITEM_NODE_MARKER_BLUE.get()) return MarkerColor.BLUE;
        return null;
    }

    /**
     * 真正改动本地标记：Shift+右键移除，否则写入（红蓝互斥，后放的覆盖先放的）。
     * 返回是否发生了实际改变——用于日志去重，也保证重复触发不会二次写文件。
     */
    public static boolean apply(Player player, Level level, BlockPos pos, MarkerColor color) {
        if (!level.isClientSide) return false;
        final NodeMarkerStore store = NodeMarkerStore.getInstance();

        if (player != null && player.isShiftKeyDown()) {
            if (!store.remove(pos)) return false;
            Main.LOGGER.info("[fangsu][turnout] marker removed pos={}", pos);
            return true;
        }

        final NodeMarker existing = store.get(pos);
        if (existing != null && existing.color() == color) return false;
        store.set(pos, color);
        Main.LOGGER.info("[fangsu][turnout] marker set {} pos={}", color, pos);
        return true;
    }

    /**
     * 清除当前维度的全部标记并返回清除数量（只应在客户端调用）。
     * 数量取自清除前的快照，因此返回值就是动作栏要显示的 N。
     */
    public static int clearCurrentDimension() {
        final NodeMarkerStore store = NodeMarkerStore.getInstance();
        final int count = store.snapshot().size();
        if (count <= 0) return 0;
        store.clearAll();
        Main.LOGGER.info("[fangsu][turnout] markers cleared count={}", count);
        return count;
    }

    /** 客户端清除并在动作栏提示结果；服务端直接返回 0（标记是客户端私有数据） */
    public static int clearAndNotify(Player player, Level level) {
        if (!level.isClientSide || player == null) return 0;
        final int count = clearCurrentDimension();
        // 1.18.2 没有 Component.translatable 静态方法，统一走 ComponentHelper
        player.displayClientMessage(count > 0
                ? ComponentHelper.translatable("ui.fangsu.turnout.clear.done", count)
                : ComponentHelper.translatable("ui.fangsu.turnout.clear.empty"), true);
        return count;
    }

    /** 手持标记物品点到非节点方块时的诊断日志（同一方块同一物品只打一次） */
    private static void logRejected(BlockPos pos, ItemStack stack, Level level) {
        if (REJECT_LOGGED.size() >= MAX_REJECT_LOGS) return;
        final String blockId = level.getBlockState(pos).getBlock().getDescriptionId();
        final String key = pos.asLong() + "|" + blockId + "|" + stack.getItem().getDescriptionId();
        if (REJECT_LOGGED.add(key)) {
            Main.LOGGER.info("[fangsu][turnout][diag] click rejected: not node block={} pos={}", blockId, pos);
        }
    }
}
