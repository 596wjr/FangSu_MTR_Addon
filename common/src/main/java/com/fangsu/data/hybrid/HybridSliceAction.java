package com.fangsu.data.hybrid;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import mtr.block.BlockNode;
import mtr.data.Rail;
import mtr.data.RailwayData;
import mtr.data.RailwayDataRailActionsModule;
import mtr.packet.PacketTrainDataGuiServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 混合构建器的切片构建动作，挂载到 MTR3 的 {@link RailwayDataRailActionsModule} 队列执行
 * （MTR3 版对应 MTR4 的 RailAction 架构：继承 {@link Rail.RailActions} 覆写 build()）。
 * <p>
 * 切片 = 轨道的横截面：矩阵宽（列 j）方向 <b>垂直于轨道</b>（水平法向），
 * 行方向 = 高度（向上）。沿轨道按间隔重复放置完整矩阵；每个方块位置按
 * 「宽度为 1 的桥梁构建」逻辑放置——仿照 MTR3 {@code Rail.RailActions.createBridge}
 * 宽度=1（radius=0）时的单采样点放置逻辑，但删除「把轨道中心格
 * （placePos != pos）改成空气」这一步。
 * <p>
 * 厚度 = 每组连续切片的数量：每组 N 个切片，组内第 k 片用第 k 个独立矩阵
 * （{@code sliceIndex}，画布可切换编辑），组间空隙 = 间隔格（留空/0 = 无缝
 * 123123 循环铺满）。组内第 k 片位置沿厚度方向（向里 +t / 向外 -t）距主片 k 格，
 * 方向由任务的 {@code thickDirection} 决定（配置屏的 ·点/×叉按钮）。
 * <p>
 * 特别急的弯道上外侧列会被拉开断开：构建按「相邻切片区间」细分——无缝模式
 * （间隔 0/1）下按取整后的实际方块位置判断，任何一列<b>隔格</b>（某方向差 >1）
 * 就细分（转弯处保证连贯）；留缝模式（间隔 ≥1）下<b>不细分</b>，用户设的间隔完全作数。
 * 每次在区间中点补建切片并一分为二，直到收敛（见 {@link #needsSplitting}）。
 * <p>
 * 斜对角错位（两个方向同时差 1，角对角留斜缝）是斜线取整阶梯的固有结果，细分中点与
 * 端点取整相同、无法消除，由<b>补角</b>闭合：切片内同行相邻列、以及相邻切片同格同列，
 * 取整后斜对角时补一个阶梯连接块（见 {@link #patchCorner}），保证斜线/弯道处隧道壁
 * 连续、转角不缺角（否则隧道在该处部分宽度过小）。
 * <p>
 * 台阶用创建桥的 isTopHalf 采样点判定（斜坡上 TOP/BOTTOM 交替、顶面半格步进，平滑斜坡）：
 * half=top 创建桥原始逻辑、half=bottom 结果翻转（已验证正常）、half=double 判定为
 * 上半砖位置时替换为双半砖（见 {@link #computePlacement}）。
 * 方向性方块（facing/axis 属性）按编辑器参考系旋转（左=西、右=东、向里=北），见 {@link #rotateState}。
 * <p>
 * MTR3 差异：getPosition 无 reverse 参数（沿轨道 0..length 恒为「起点→终点」），
 * 展开方向固定从轨道几何起点端开始（与 MTR 原版桥梁构建一致）；
 * 无 getDescription/getColor（不覆写 writePacket，动作列表显示为默认「桥梁建设」）。
 */
public class HybridSliceAction extends Rail.RailActions {

    private final HybridSliceTask task;
    /** 构建级混合方案列表（物品 NBT 顶层，不随任务数据存储） */
    private final List<HybridScheme> schemes;
    /** 父类 RailActions 的 rail 字段是 private，build() 里取不到，这里自己存一份 */
    private final Rail rail;
    /** 切片位置序列（升序）：每组 = 厚度 N 个连续切片（组内第 k 片用第 k 个独立矩阵），
     *  组间空隙 = interval 格；留空/0 = 无缝循环铺满 */
    private final SlicePos[] slices;
    /** 组间空隙格数（interval null/0 = 无缝）：starts 生成与切线差分共用；null 拆箱防御 */
    private final double gap;
    private final int width;
    private final int height;
    private final double length;
    private final Level level;
    private final UUID uuid;
    private final String playerName;
    /**
     * 已放置的「格 + 状态」：同格同状态视为重复（细分补建切片与相邻切片落同格时只放一个）；
     * 同格不同状态（如 BOTTOM 与 TOP 半砖叠同一格）都放——斜坡上相邻切片的半砖 half 不同
     * 但位置同格时，上下半砖同格堆叠成整块是合法结果，不能按格去重
     */
    private final Map<BlockPos, BlockState> placedCells = new HashMap<>();
    /** 细分工作队列：元素为相邻切片弧长区间（含两端片组内索引），细分到外侧列连上为止 */
    private final ArrayDeque<Segment> queue = new ArrayDeque<>();
    /** 上一片切片的逐格放置位置（跨切片补角用；null = 该格无方块） */
    private BlockPos[][] prevGrid;
    /** 上一片切片的弧长位置（跨切片补角的前向门控：细分循环会反向重建旧切片，反向时不补） */
    private double prevTrackPos = -1;
    /** 初始区间数（进度显示基数；细分新增的区间不计数） */
    private final int totalSegments;
    private int processedSegments = 0;
    private boolean singleSliceBuilt = false;
    /** 区间弧长小于此值强制收敛（防御浮点不收敛） */
    private static final double MIN_SEGMENT = 0.05;

    public HybridSliceAction(Level level, Player player, Rail rail, HybridSliceTask task, List<HybridScheme> schemes) {
        super(level, player, Rail.RailActionType.BRIDGE, rail, 0, 0, null);
        this.level = level;
        this.uuid = player.getUUID();
        this.playerName = player.getName().getString();
        this.task = task;
        this.schemes = schemes;
        this.rail = rail;
        width = task.width;
        height = task.height;
        length = rail.getLength();

        // 切片位置序列：留空/间隔 0 = 无缝循环铺满（原「只建一块」语义废弃，与读取旧 NBT
        // 无 interval 键时按 0.0 的行为一致）。每组 = 厚度 N 个连续切片（组内第 k 片内容 =
        // 第 k 个独立矩阵），组间空隙 = interval 格（0/留空 = 无空隙，123123 循环铺满）。
        // 组周期 = 空隙 + 组厚；thickness=1 时 period=interval+1，与原行为完全一致
        gap = task.interval == null ? 0.0 : Math.max(task.interval, 0);
        final double period = gap + Math.max(task.thickness, 1);
        final int dir = task.thickDirection ? 1 : -1;
        final List<SlicePos> list = new ArrayList<>();
        list.add(new SlicePos(task.start, 0));
        addThicknessSlices(list, task.start, dir);
        double temp = task.start;
        while (temp + period < length) {
            temp += period;
            list.add(new SlicePos(temp, 0));
            addThicknessSlices(list, temp, dir);
        }
        // 向外延伸时组内位置递减：排序保证细分区间升序（补角的前向门控依赖弧长递增）
        list.sort(Comparator.comparingDouble(p -> p.pos));
        slices = list.toArray(new SlicePos[0]);
        // 相邻切片组成细分区间：急弯处外侧列断开时在区间中点补建切片（见 build()/needsSplitting）
        for (int k = 0; k < slices.length - 1; k++) {
            queue.addLast(new Segment(slices[k].pos, slices[k].index, slices[k + 1].pos, slices[k + 1].index));
        }
        totalSegments = queue.size();
    }

    /** 向厚度方向补一组的厚度片位置：第 k 片沿方向距主片 k 格（k = 组内索引 = 矩阵下标）。
     *  越界方向感知早停——位置单调，超出后只会更越界，防巨型厚度（坏 NBT）空转挂起 */
    private void addThicknessSlices(List<SlicePos> list, double main, int dir) {
        for (int k = 1; k < task.thickness; k++) {
            final double pos = main + dir * k;
            if (dir > 0 ? pos > length : pos < 0) break;
            list.add(new SlicePos(pos, k));
        }
    }

    @Override
    public boolean build() {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 2) {
            // 轨道太短（无相邻对）：只建起点切片（含其厚度片）
            if (queue.isEmpty()) {
                if (!singleSliceBuilt) {
                    buildSlice(slices[0].pos, slices[0].index);
                    singleSliceBuilt = true;
                }
                sendProgressMessage(100);
                return true;
            }
            final Segment seg = queue.peekFirst();
            buildSlice(seg.s1, seg.idx1);
            if (!needsSplitting(seg.s1, seg.idx1, seg.s2, seg.idx2) || seg.s2 - seg.s1 <= MIN_SEGMENT) {
                // 外侧已连上：完成该区间
                queue.pollFirst();
                processedSegments++;
                buildSlice(seg.s2, seg.idx2);
            } else {
                // 特别急的弯道：列间距不连贯 → 在中点补建切片（内容用区间前端片，
                // 组内复制、不破坏 1,2,3 序列），区间一分为二继续细分直到收敛
                final double mid = (seg.s1 + seg.s2) / 2;
                buildSlice(mid, seg.idx1);
                queue.pollFirst();
                processedSegments++;
                queue.addLast(new Segment(seg.s1, seg.idx1, mid, seg.idx1));
                queue.addLast(new Segment(mid, seg.idx1, seg.s2, seg.idx2));
            }
            sendProgressMessage(Math.min(99F, 100F * processedSegments / totalSegments));
        }
        return false;
    }

    /**
     * 在轨道位置 {@code trackPos} 放置一个完整切片矩阵（宽 × 高横截面）。
     * {@code sliceIndex} = 组内索引，决定用哪一组的独立矩阵（厚度片各片内容不同）。
     */
    private void buildSlice(double trackPos, int sliceIndex) {
        final Vec3 center = rail.getPosition(trackPos);
        final Vec3 tangent = computeTangent(trackPos);
        final double tLen = Math.sqrt(tangent.x * tangent.x + tangent.z * tangent.z);
        // 水平法向 n = 切线水平投影旋转 90°：(tz, 0, -tx)；切向 t（方向性方块参考系）。
        // 竖直轨道（水平投影≈0）时法向取 X 轴、切向取 X 轴
        final double nx = tLen > 1e-6 ? tangent.z / tLen : 1.0;
        final double nz = tLen > 1e-6 ? -tangent.x / tLen : 0.0;
        final double tx = tLen > 1e-6 ? tangent.x / tLen : 1.0;
        final double tz = tLen > 1e-6 ? tangent.z / tLen : 0.0;
        // 行反转 + 中心行对齐：画布顶部行 i=0 放最高处；中心行 (height-1)/2
        // （画布上红色轨道线标记所在行）对齐轨道线高度。
        // 整体再上移 1 格（实测虚拟轨道线对齐偏低 1 格）
        final double centerRow = (height - 1) / 2.0 + 1;
        final int groupBase = sliceIndex * width * height;
        final BlockPos[][] grid = new BlockPos[height][width];
        final BlockState[][] states = new BlockState[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                final HybridSliceTask.HybridCreatorLump lump = task.lumps.get(groupBase + i * width + j);
                // 普通格返回自身方块、方案格按权重确定性随机抽选；null = 空/悬空引用/无效方案 → 跳过
                BlockState state = resolveState(lump);
                if (state == null) continue;
                final double columnOffset = j - (width - 1) / 2.0;
                state = rotateState(state, nx, nz, tx, tz);
                final CellPlacement placement = computePlacement(lump, state, center.add(nx * columnOffset, centerRow - i, nz * columnOffset));
                grid[i][j] = placement.pos;
                states[i][j] = placement.state;
                placeBlock(placement.pos, placement.state, lump.replacement);
                // 内部补角：同行相邻列取整后斜对角 → 补阶梯连接块。斜缝贯穿整个墙高
                // （x/z 的 floor 只随列变、与行无关），每行都要补，只补一格会留缝
                if (j > 0 && grid[i][j - 1] != null && Math.abs(grid[i][j].getX() - grid[i][j - 1].getX()) == 1
                        && Math.abs(grid[i][j].getZ() - grid[i][j - 1].getZ()) == 1) {
                    patchCorner(grid[i][j - 1], grid[i][j], states[i][j - 1], task.lumps.get(groupBase + i * width + j - 1).replacement, tx, tz);
                }
            }
        }
        // 跨切片补角：与上一片同格同列斜对角 → 补阶梯连接块。
        // 弧长前向门控：细分循环反向重建旧切片时不补（防对角两个角都被填、
        // 局部加厚挤压弯道内侧）；留缝模式（中心距 >1.5）不补，用户设的间隔作数
        if (prevGrid != null && trackPos - prevTrackPos > 1e-9) {
            final double gap = center.distanceTo(rail.getPosition(prevTrackPos));
            if (gap > 0 && gap <= 1.5) {
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        final BlockPos prev = prevGrid[i][j];
                        if (prev == null || grid[i][j] == null) continue;
                        final int dx = Math.abs(grid[i][j].getX() - prev.getX());
                        final int dy = Math.abs(grid[i][j].getY() - prev.getY());
                        final int dz = Math.abs(grid[i][j].getZ() - prev.getZ());
                        // 斜对角（两方向各差 1）；隔格（某方向差 >1）由细分处理，不在此列
                        if (dx + dy + dz == 2 && dx <= 1 && dy <= 1 && dz <= 1) {
                            patchCorner(prev, grid[i][j], states[i][j], task.lumps.get(groupBase + i * width + j).replacement, tx, tz);
                        }
                    }
                }
            }
        }
        prevGrid = grid;
        prevTrackPos = trackPos;
    }

    /**
     * 解析一个格子最终要放置的方块：
     * <ul>
     *   <li>普通格（schemeIndex < 0）：返回格内自身方块（可能为 null → 调用方跳过）</li>
     *   <li>混合方案格：按权重随机抽选一个条目。确定性随机——种子由「轨道位置取整 + 切片索引 +
     *       格坐标」构成：同格每次构建结果一致（细分重建/重复构建稳定），不同格/不同轨道各异</li>
     *   <li>悬空引用（方案已删/索引越界）、空方案、全零权重 → null（调用方跳过该格）</li>
     * </ul>
     */
    private BlockState resolveState(HybridSliceTask.HybridCreatorLump lump) {
        if (lump.schemeIndex < 0) return lump.blockState;
        if (lump.schemeIndex >= schemes.size()) return null; // 悬空引用（数据损坏/方案被删）
        final HybridScheme scheme = schemes.get(lump.schemeIndex);
        long total = 0;
        for (HybridScheme.SchemeEntry entry : scheme.entries) {
            if (entry.blockState != null && entry.weight > 0) total += entry.weight;
        }
        if (total <= 0) return null; // 空方案或全零权重
        // 每格一个独立 Random 实例：格间互不影响；不固定种子，重复放置/重新构建
        // 同一格结果不同（不做确定性缓存——用户要求每次随机）
        long remaining = new java.util.Random().nextInt((int) Math.min(total, Integer.MAX_VALUE));
        for (HybridScheme.SchemeEntry entry : scheme.entries) {
            if (entry.blockState == null || entry.weight <= 0) continue;
            remaining -= entry.weight;
            if (remaining < 0) return entry.blockState;
        }
        return null; // 防御：total > 0 时不可达
    }

    /**
     * 相邻两个切片是否「不连贯」（需细分补缝）：
     * <ul>
     *   <li>中心列间距 > 1.5 格（用户设了间隔，interval ≥1）：<b>不细分</b>——
     *       间隔是用户刻意留的，细分补建的切片会把间隔填掉（间隔不作数），
     *       所以留缝模式下间隔完全作数，外侧断开是间隔的固有结果</li>
     *   <li>中心列间距 ≤ 1.5 格（interval 0/1 的无缝或近无缝模式）：按<b>取整后的实际
     *       方块位置</b>判断——任何一列两端切片的方块经 floor 取整（与
     *       {@link #computePlacement} 的 {@code RailwayData.newBlockPos} 一致）后同格或面接触
     *       （只有一个方向差 1，其余为 0）视为连续；隔格（某方向差 >1）视为断开，
     *       细分到转弯处方块真正贴紧。斜对角错位（两个方向同时差 1，角对角留斜缝）
     *       不细分——斜线取整阶梯的固有结果，细分中点与端点取整相同、无法消除，
     *       由补角（见 {@link #patchCorner}）在放置时闭合</li>
     * </ul>
     */
    private boolean needsSplitting(double s1, int idx1, double s2, int idx2) {
        final Vec3 c1 = rail.getPosition(s1);
        final Vec3 c2 = rail.getPosition(s2);
        final double centerGap = c1.distanceTo(c2);
        if (centerGap <= 0 || centerGap > 1.5) return false;
        // 无缝模式：模拟 computePlacement 的 floor 取整（含 centerRow 偏移——高度为偶数时
        // centerRow 带 .5，判断必须与放置一致），判断各列取整后的方块是否贴紧
        final double centerRow = (height - 1) / 2.0 + 1;
        final double[] n1 = horizontalNormal(s1);
        final double[] n2 = horizontalNormal(s2);
        final int groupBase = idx1 * width * height;
        for (int j = 0; j < width; j++) {
            // 该列是否含台阶方块（用区间前端片的矩阵）：半砖只占半格高，斜坡上相邻切片
            // 几何 y 差 ≥ 0.5 时半砖会错开半格留缝（取整判断把它当「垂直堆叠连续」，
            // 实际半砖之间空半格），需按几何 y 差细分到同格重叠
            boolean hasSlab = false;
            for (int i = 0; i < height && !hasSlab; i++) {
                final HybridSliceTask.HybridCreatorLump lump = task.lumps.get(groupBase + i * width + j);
                if (lump.schemeIndex >= 0 && lump.schemeIndex < schemes.size()) {
                    // 混合方案格：任一条目含台阶 → 保守判定该列为台阶列（细分逻辑照常生效，
                    // 半砖条目与普通方块混排时也不会在半格处留缝）
                    for (HybridScheme.SchemeEntry entry : schemes.get(lump.schemeIndex).entries) {
                        if (entry.blockState != null && entry.blockState.getBlock() instanceof SlabBlock) {
                            hasSlab = true;
                            break;
                        }
                    }
                } else if (lump.blockState != null && lump.blockState.getBlock() instanceof SlabBlock) {
                    hasSlab = true;
                }
            }
            if (hasSlab && Math.abs(c2.y - c1.y) >= 0.5) return true;
            final double off = j - (width - 1) / 2.0;
            final int x1 = (int) Math.floor(c1.x + n1[0] * off);
            final int z1 = (int) Math.floor(c1.z + n1[1] * off);
            final int x2 = (int) Math.floor(c2.x + n2[0] * off);
            final int z2 = (int) Math.floor(c2.z + n2[1] * off);
            final int y1 = (int) Math.floor(c1.y + centerRow);
            final int y2 = (int) Math.floor(c2.y + centerRow);
            final int dx = Math.abs(x2 - x1);
            final int dy = Math.abs(y2 - y1);
            final int dz = Math.abs(z2 - z1);
            // 隔格（某方向差 >1）算断开 → 细分补缝；斜对角（各方向差 ≤1 但两方向同时
            // 差 1）是斜线取整阶梯的固有结果，细分无法消除，由补角闭合（见 patchCorner）
            if (dx > 1 || dy > 1 || dz > 1) return true;
        }
        return false;
    }

    /** 轨道在 {@code trackPos} 处的水平法向（单位向量），竖直轨道时取 X 轴 */
    private double[] horizontalNormal(double trackPos) {
        final Vec3 tangent = computeTangent(trackPos);
        final double tLen = Math.sqrt(tangent.x * tangent.x + tangent.z * tangent.z);
        if (tLen <= 1e-6) return new double[]{1.0, 0.0};
        return new double[]{tangent.z / tLen, -tangent.x / tLen};
    }

    /**
     * 计算轨道在 {@code trackPos} 处的切线向量（前后差分，端点 clamp 到 [0, length]）。
     */
    private Vec3 computeTangent(double trackPos) {
        // 差分步长 = 组内切片间距（间隔+1，clamp 到 0.5~2.0 保证方向稳定）；用 gap 字段防御 interval 留空拆箱
        final double delta = Math.min(Math.max(gap + 1, 0.5), 2.0);
        final double s2 = Math.min(trackPos + delta, length);
        final double s1 = Math.max(trackPos - delta, 0);
        final Vec3 p2 = rail.getPosition(s2);
        final Vec3 p1 = rail.getPosition(s1);
        return new Vec3(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z);
    }

    /**
     * 把编辑器画布坐标系的朝向映射到世界坐标系。
     * <p>
     * 编辑器参考系：左=西(-X)、右=东(+X)、向里=北(-Z)（画布红线上方为北/向里）。
     * 构建时「右」= 水平法向 n=(nx,nz)，「向里」= 轨道切向 t=(tx,tz)（水平投影）。
     * 映射公式：世界方向 = dx·n + dz·t，其中 (dx,dz) 是编辑器方向在画布基下的坐标
     * （东=(1,0)、西=(-1,0)、北=(0,1)、南=(0,-1)）。取 |wx|>=|wz| 判东西，否则判南北。
     * UP/DOWN 不旋转；轴属性 X→法向的轴、Z→切向的轴、Y 不变。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private BlockState rotateState(BlockState state, double nx, double nz, double tx, double tz) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty directionProperty) {
                final Direction value = state.getValue(directionProperty);
                if (value == Direction.UP || value == Direction.DOWN) continue;
                state = state.setValue(directionProperty, rotateDirection(value, nx, nz, tx, tz));
            } else if ("axis".equals(property.getName()) && property instanceof EnumProperty) {
                // 轴属性是 EnumProperty<Direction.Axis>（如原木/活塞杆），raw 访问绕过泛型
                final Object value = state.getValue((EnumProperty) property);
                if (value instanceof Direction.Axis axis && axis != Direction.Axis.Y) {
                    state = state.setValue((EnumProperty) property, rotateAxis(axis, nx, nz, tx, tz));
                }
            }
        }
        return state;
    }

    /**
     * 编辑器水平方向 → 世界方向：世界 = dx·n + dz·t（东=(1,0)、西=(-1,0)、北=(0,1)、南=(0,-1)）。
     */
    private Direction rotateDirection(Direction value, double nx, double nz, double tx, double tz) {
        final double wx, wz;
        switch (value) {
            case EAST -> {
                wx = nx;
                wz = nz;
            }
            case WEST -> {
                wx = -nx;
                wz = -nz;
            }
            case SOUTH -> {
                wx = -tx;
                wz = -tz;
            }
            default -> {
                // NORTH（向里=北）：世界方向 = +t
                wx = tx;
                wz = tz;
            }
        }
        return Math.abs(wx) >= Math.abs(wz) ? (wx >= 0 ? Direction.EAST : Direction.WEST)
                : (wz >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /**
     * 轴旋转：编辑器 X 轴 = 水平法向 n、Z 轴 = 切向 t、Y 不变；结果按水平分量绝对值判轴。
     */
    private Direction.Axis rotateAxis(Direction.Axis axis, double nx, double nz, double tx, double tz) {
        final double ax, az;
        switch (axis) {
            case X -> {
                ax = nx;
                az = nz;
            }
            case Z -> {
                ax = tx;
                az = tz;
            }
            default -> {
                return Direction.Axis.Y;
            }
        }
        return Math.abs(ax) >= Math.abs(az) ? Direction.Axis.X : Direction.Axis.Z;
    }

    /**
     * 计算单采样点的放置位置与最终方块状态（宽度=1 的桥梁构建 createBridge radius=0 逻辑，
     * 去除「轨道中心格置空气」；不放置，放置见 {@link #placeBlock}）。
     * <p>
     * 台阶用创建桥的 isTopHalf 采样点判定（顶面贴采样点下方最近半格 → 斜坡上
     * TOP/BOTTOM 交替、顶面半格步进，平滑斜坡），类型差异在判定结果上：
     * <ul>
     *   <li>half=top（上半砖）：创建桥原始逻辑——isTopHalf=true → BOTTOM@blockPos、
     *       false → TOP@below</li>
     *   <li>half=bottom（下半砖）：创建桥结果翻转（已验证正常，保持）</li>
     *   <li>half=double（双半砖）：类似上半砖，仅当判定为「上半砖位置」
     *       （isTopHalf=false，应放 TOP@below）时替换为 DOUBLE@below；判定为下半砖
     *       位置时保持 BOTTOM</li>
     *   <li>非台阶：{@code @ below}（顶面 = floor，贴线）</li>
     * </ul>
     *
     * @param state 已按参考系旋转后的方块状态
     */
    private CellPlacement computePlacement(HybridSliceTask.HybridCreatorLump lump, BlockState state, Vec3 vector) {
        final boolean isSlab = state.getBlock() instanceof SlabBlock;
        final BlockPos blockPos = RailwayData.newBlockPos(vector.x, vector.y, vector.z);
        final boolean isTopHalf = vector.y - Math.floor(vector.y) >= 0.5;

        final BlockPos placePos;
        final BlockState placeState;
        if (!isSlab) {
            // 非台阶：顶面贴线（照旧）
            placePos = blockPos.below();
            placeState = state;
        } else if (state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
            // ── 下半砖（保持已验证正常的逻辑）：创建桥结果翻转 ──
            final BlockPos placePos0;
            final BlockState placeState0;
            if (isTopHalf) {
                placePos0 = blockPos;
                placeState0 = state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            } else {
                placePos0 = blockPos.below();
                placeState0 = state.setValue(SlabBlock.TYPE, SlabType.TOP);
            }
            final boolean topHalfResult = placeState0.getValue(SlabBlock.TYPE) == SlabType.TOP;
            placePos = topHalfResult ? placePos0 : placePos0.below();
            placeState = placeState0.setValue(SlabBlock.TYPE, topHalfResult ? SlabType.BOTTOM : SlabType.TOP);
        } else if (state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            // ── 双半砖：类似上半砖的创建桥判定，判定为上半砖位置时替换为双半砖 ──
            if (isTopHalf) {
                placePos = blockPos;
                placeState = state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            } else {
                placePos = blockPos.below();
                placeState = state;
            }
        } else {
            // ── 上半砖：创建桥原始逻辑（顶面贴采样点下方最近半格，斜坡平滑）──
            if (isTopHalf) {
                placePos = blockPos;
                placeState = state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            } else {
                placePos = blockPos.below();
                placeState = state;
            }
        }
        return new CellPlacement(placePos, placeState);
    }

    /**
     * 放置一个方块到指定格：同格同状态去重 → 替换/空气检查 → setBlockAndUpdate。
     * 切片格子与补角块共用（替换模式下轨道节点同样跳过）。
     */
    private void placeBlock(BlockPos placePos, BlockState placeState, boolean replacement) {
        // 同格同状态去重：细分补建切片与相邻切片落同格且状态相同时只放一个
        final BlockState prev = placedCells.get(placePos);
        if (placeState.equals(prev)) return;
        final BlockState existing = level.getBlockState(placePos);
        if (replacement) {
            // 替换模式：除轨道节点（MTR 节点）外全部替换，包括方块实体
            // （MTR3 版无万向节点方块，无需像 MTR4 版那样额外判 BlockMultiDirectionNode）
            final net.minecraft.world.level.block.Block block = existing.getBlock();
            if (block instanceof BlockNode) return;
        } else if (!existing.isAir() || level.getBlockEntity(placePos) != null) {
            // 普通模式：只放空气处
            return;
        }
        level.setBlockAndUpdate(placePos, placeState);
        placedCells.put(placePos, placeState);
        // 注意：不执行 createBridge 的「placePos != pos 时 setBlockState(pos, Air)」
        // （用户要求去除：轨道中心格的原方块保留）
    }

    /**
     * 补角：两个取整后的方块位置斜对角（两方向各差 1，中间缺角）时，
     * 在两格之间补一个阶梯连接块（状态克隆自源列，半砖类型原样 → 同高度连续）。
     * 斜线/弯道取整阶梯的固有结果，细分中点与端点取整相同无法消除，只能补块闭合。
     * <ul>
     *   <li>单水平方向差：补 a 沿该方向的邻格</li>
     *   <li>x/z 同时差 1：按轨道水平投影主导轴裁决补块方向（与 {@link #rotateDirection}
     *       同约定），保证壁沿轨道方向伸展</li>
     *   <li>补块不与任何已放置格争抢（placedCells 已含目标格则跳过；后续真实格若落
     *       同格，由 placeBlock 的去重/替换规则决定赢家）</li>
     * </ul>
     */
    private void patchCorner(BlockPos a, BlockPos b, BlockState state, boolean replacement, double tx, double tz) {
        final int dx = Integer.compare(b.getX() - a.getX(), 0);
        final int dz = Integer.compare(b.getZ() - a.getZ(), 0);
        final BlockPos target;
        if (dx != 0 && dz == 0) {
            target = a.offset(dx, 0, 0);
        } else if (dx == 0 && dz != 0) {
            target = a.offset(0, 0, dz);
        } else if (dx != 0) {
            // x/z 同时差 1：切线水平投影主导轴裁决（|tx| >= |tz| 先 x）
            target = Math.abs(tx) >= Math.abs(tz) ? a.offset(dx, 0, 0) : a.offset(0, 0, dz);
        } else {
            // 仅 y 差不是斜对角（面接触无需补）；调用方判据已排除
            return;
        }
        if (placedCells.containsKey(target)) return;
        placeBlock(target, state, replacement);
    }

    private void sendProgressMessage(float percentage) {
        final Player player = level.getPlayerByUUID(uuid);
        if (player != null) {
            player.displayClientMessage(ComponentHelper.translatable("gui.fangsu.hybrid_creator.percentage_complete", percentage), true);
        }
    }

    // MTR3 无 getDescription()/getColor()（MTR4 版覆写点）；writePacket 继承父类默认实现，
    // 构建动作在 MTR 动作列表中显示为「桥梁建设」（gui.mtr.rail_action_bridge）

    /**
     * 将构建动作挂载到 MTR3 的 RailwayDataRailActionsModule 队列。
     * <p>
     * 优先走 mixin 接口（Fabric 端）；Forge 端不加载 mixin（mods.toml 无声明），
     * 回退到反射操作 {@code railActions} 字段 + 静态广播。
     */
    public static void attach(Level level, Player player, RailwayData railwayData, Rail rail, HybridSliceTask task, List<HybridScheme> schemes) {
        final HybridSliceAction action = new HybridSliceAction(level, player, rail, task, schemes);
        final RailwayDataRailActionsModule module = railwayData.railwayDataRailActionsModule;
        if (module instanceof RailActionsModuleExtraSupplier supplier) {
            supplier.getRailActions().add(action);
            supplier.sendUpdateS2C();
        } else {
            reflectAdd(module, level, action);
        }
    }

    private static void reflectAdd(RailwayDataRailActionsModule module, Level level, Rail.RailActions railAction) {
        try {
            final java.lang.reflect.Field field = RailwayDataRailActionsModule.class.getDeclaredField("railActions");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            final List<Rail.RailActions> actions = (List<Rail.RailActions>) field.get(module);
            actions.add(railAction);
            PacketTrainDataGuiServer.updateRailActionsS2C(level, actions);
        } catch (ReflectiveOperationException e) {
            Main.LOGGER.error("[HybridCreator] 挂载构建动作失败", e);
        }
    }

    /** 单采样点计算出的放置结果：目标格 + 最终方块状态（补角克隆时也用） */
    private record CellPlacement(BlockPos pos, BlockState state) {}

    /** 切片位置 + 组内索引（决定用哪一组独立矩阵，0 = 主片） */
    private record SlicePos(double pos, int index) {}

    /** 细分区间：两端切片位置 + 组内索引；细分补建中点片用区间前端片的索引（idx1） */
    private record Segment(double s1, int idx1, double s2, int idx2) {}
}
