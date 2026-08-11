package com.fangsu.items;

import com.fangsu.data.hybrid.HybridSliceAction;
import com.fangsu.data.hybrid.HybridSliceTask;
import com.fangsu.data.hybrid.RailActionsModuleExtraSupplier;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.network.HybridCreatorPackets;
import com.fangsu.utils.RegisterUtil;
import mtr.data.Rail;
import mtr.data.RailwayData;
import mtr.data.RailwayDataRailActionsModule;
import mtr.item.ItemNodeModifierSelectableBlockBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 混合构建器——在轨道上按切片间距重复放置横截面矩阵的构建工具。
 * <p>
 * 右键空气/非节点方块：打开任务列表编辑器屏幕。
 * 依次左键点击轨道两端节点：触发构建（每个任务按 order 依次挂载到 MTR 构建队列）。
 * <p>
 * 继承 {@link ItemNodeModifierSelectableBlockBase}（width=1 → radius=0），自动获得：
 * 查轨回调链——点击两端节点后基类回调 {@link #onConnect}，posStart=后点、posEnd=先点
 * （由 {@code ItemNodeModifierBase.onEndClick} 传参顺序决定），与 MTR 轨道表存储方向一致
 * （照 ItemBridgeCreator → markRailForBridge 的 {@code rails.get(posStart).get(posEnd)} 实证）。
 * MTR3 版无万向节点方块（BlockMultiDirectionNode 是 MTR4 版方速独有），无需额外兼容。
 */
public class ItemHybridCreator extends ItemNodeModifierSelectableBlockBase {

    /** NBT 键：任务列表（客户端编辑器写、服务端构建时读） */
    public static final String TAG_TASKS = "tasks";

    public ItemHybridCreator() {
        // MTR3 构造无 ItemSettings；width=1 → radius=0（宽度为 1 的桥梁构建）
        super(false, 0, 1);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        RegisterUtil.addDescTooltip(tooltip, "item.fangsu.hybrid_creator.desc");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 右键空气：开编辑器（客户端由 S2C 包开屏，此处只处理服务端）
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            HybridCreatorPackets.sendOpenScreenS2C(serverPlayer);
        }
        // 1.18.2~1.20.1 的 Item.use 返回 InteractionResultHolder<ItemStack>
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (clickCondition(context)) {
            // 客户端必须返回 SUCCESS 消费动作：ItemBlockClickingBase 的客户端分支会返回
            // Item 默认的 PASS，客户端随后预测调用 use() 并发包到服务端，
            // 触发 use() 误开编辑器（照 ANTE 的 useOn 无条件返回 SUCCESS 的思路）
            if (context.getLevel().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            return super.useOn(context);
        }
        // 点击非节点方块：开编辑器（照 ANTE CompoundCreator.useOn）
        final Level world = context.getLevel();
        if (!world.isClientSide) {
            final Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                HybridCreatorPackets.sendOpenScreenS2C(serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean onConnect(Player player, ItemStack itemStack, RailwayData railwayData, BlockPos posStart, BlockPos posEnd, int radius, int height) {
        // 直接用 vanilla NBT
        final CompoundTag tag = itemStack.getOrCreateTag();
        if (!tag.contains(TAG_TASKS)) {
            player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.no_tasks_found"), true);
            return true;
        }

        final CompoundTag tasksTag = tag.getCompound(TAG_TASKS);
        final List<HybridSliceTask> tasks = new ArrayList<>();
        for (String key : tasksTag.getAllKeys()) {
            final HybridSliceTask task = new HybridSliceTask(tasksTag.getCompound(key));
            // 防御：任务数据损坏（如手工改 NBT）时跳过，避免构建越界；
            // lumps 为 N 组（厚度）平铺 = thickness × 宽 × 高
            if (task.width < 1 || task.height < 1 || task.step <= 0 || task.thickness < 1 || task.lumps.size() != task.thickness * task.width * task.height) {
                com.fangsu.Main.LOGGER.error("[HybridCreator] 跳过非法任务 {}（{}x{} step={} lumps={}）", task.name, task.width, task.height, task.step, task.lumps.size());
                continue;
            }
            tasks.add(task);
        }
        tasks.sort(Comparator.comparingInt(task -> task.order));

        // 照 markRailForBridge 的 rails.get(posStart).get(posEnd) 取轨（posStart=后点，与存储方向一致）
        final Rail rail = getRail(railwayData, posStart, posEnd);
        if (rail == null) {
            return false; // 基类显示 gui.mtr.rail_not_found_action
        }
        // MTR3 无 reverse 语义：展开方向固定从轨道几何起点端（getPosition(0) 端）开始，
        // 与 MTR 原版桥梁构建一致，无需记录先点/后点
        // Entity.level() 方法 1.20.0+ 才有；1.18.2~1.19.4 用 public 字段 player.level（照 ModNetwork 分界）
        //#if MC_VERSION >= 12000
        final Level level = player.level();
        //#else
        //$$ final Level level = player.level;
        //#endif
        for (HybridSliceTask task : tasks) {
            // 逐个挂载；RailwayDataRailActionsModule.tick() 一次只处理队头，按 order 串行执行
            HybridSliceAction.attach(level, player, railwayData, rail, task);
        }
        return true;
    }

    /**
     * 从轨道表取 Rail 实例：优先走 mixin 接口（Fabric 端 mixin 生效）；
     * Forge 端不加载 mixin（mods.toml 无声明），回退反射 {@link RailwayData} 的
     * private {@code rails} 字段（MTR 为 official mappings，字段名即源码名）。
     * <p>
     * 轨道表单向存储「后点→先点」（照 ItemBridgeCreator.markRailForBridge 实证），
     * 直接查 posStart→posEnd；防御性再反向查一次。
     */
    private static Rail getRail(RailwayData railwayData, BlockPos posStart, BlockPos posEnd) {
        final RailwayDataRailActionsModule module = railwayData.railwayDataRailActionsModule;
        if (module instanceof RailActionsModuleExtraSupplier supplier) {
            return getRail(supplier.getRails(), posStart, posEnd);
        }
        try {
            final Field field = RailwayData.class.getDeclaredField("rails");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            final Map<BlockPos, Map<BlockPos, Rail>> rails = (Map<BlockPos, Map<BlockPos, Rail>>) field.get(railwayData);
            return getRail(rails, posStart, posEnd);
        } catch (ReflectiveOperationException e) {
            com.fangsu.Main.LOGGER.error("[HybridCreator] 读取轨道表失败", e);
            return null;
        }
    }

    private static Rail getRail(Map<BlockPos, Map<BlockPos, Rail>> rails, BlockPos posStart, BlockPos posEnd) {
        final Map<BlockPos, Rail> connections = rails.get(posStart);
        if (connections != null && connections.containsKey(posEnd)) {
            return connections.get(posEnd);
        }
        // 防御反向（正常情况单向存储，miss 即为查无此轨）
        final Map<BlockPos, Rail> reverse = rails.get(posEnd);
        return reverse == null ? null : reverse.get(posStart);
    }
}
