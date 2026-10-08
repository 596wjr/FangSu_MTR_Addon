package com.fangsu.util;

import com.fangsu.Main;
import com.fangsu.blockEntities.BlockEntityMultiDirectionNode;
import com.fangsu.data.hybrid.RailActionsModuleExtraSupplier;
import com.fangsu.mappings.rail.RailPoseExtra;
import com.fangsu.mtr.RailAngleExtra;
import com.fangsu.mtr.RailCalculator;
import com.fangsu.mtr.rail.RailPoseExtraHolder;
import com.fangsu.mtr.rail.RailTiltSupport;
import mtr.block.BlockNode;
import mtr.data.Rail;
import mtr.data.RailAngle;
import mtr.data.RailType;
import mtr.data.RailwayData;
import mtr.data.TransportMode;
import mtr.packet.PacketTrainDataGuiServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

/**
 * 万向节点连接几何与建轨工具（MTR3 版）。
 * <p>
 * 结构对应 MTR4 版的 {@code com.fangsu.util.NodeConnector}，但底层换成 MTR3 的
 * {@link Rail} / {@link RailAngle} / {@link RailwayData} API：
 * <ul>
 *   <li>{@link #straightAngle(BlockPos, BlockPos)} — 两端点水平连线角度（直线轨道方向）</li>
 *   <li>{@link #maxRadiusTangentAngle(BlockPos, double, BlockPos)} — 未绑定端点的最大半径圆弧切向角
 *       （委托 {@link RailCalculator#calculateMaxRadiusAngle}）</li>
 *   <li>{@link #getDirectionDegrees(Level, BlockPos)} / {@link #isBonded(Level, BlockPos)} — 读取节点状态</li>
 *   <li>{@link #findConnectedEndpoints(BlockPos)} — 客户端查找连接到节点位置的其他端点</li>
 *   <li>{@link #createAndSendRail} — 服务端按任意角度构建并派发铁轨（对应 MTR3 原版
 *       {@code ItemRailModifier.onConnect} 的建轨流程，但跳过 {@code isValid()} 的枚举引用相等判定）</li>
 *   <li>{@link #rebuildRailsAtNode} — 服务端按节点当前绑定角度重建连接到该节点的轨道
 *       （世界重载 / 角度改绑后修正几何）</li>
 * </ul>
 */
public final class NodeConnector {

    /**
     * 几何有效性阈值：{@code Rail} 构造在无解时会退化为全 0 参数，此时轨道长度为 0。
     */
    public static final double LENGTH_EPSILON = 1.0E-4;

    /**
     * 22.5° 栅格（MTR 原生 16 方向步进）。
     */
    private static final double ANGLE_GRID = 22.5D;

    private NodeConnector() {
    }

    // ==================== 基础几何 ====================

    /**
     * 水平面上两端点的连线角度（度，0=东, 90=南, 180=西, 270=北）。用于直线轨道方向绑定。
     */
    public static double straightAngle(BlockPos a, BlockPos b) {
        final double dx = b.getX() - a.getX();
        final double dz = b.getZ() - a.getZ();
        return normalizeDegrees(Math.toDegrees(Math.atan2(dz, dx)));
    }

    /**
     * 归一化角度到 [0, 360)。
     */
    public static double normalizeDegrees(double deg) {
        deg = deg % 360.0;
        if (deg < 0) deg += 360.0;
        return deg;
    }

    /**
     * 计算“最大半径圆弧”在未绑定端点处的切向角。
     * <p>
     * 几何：固定端点 fixed 的切向为 fixedAngle，求过 free 点且在该处与方向相切的圆（唯一解），
     * 返回该圆在 free 点处的切向角。这是满足平滑连接的“最大半径”圆弧。
     * <p>
     * 直接委托 NTE 移植的 {@link RailCalculator#calculateMaxRadiusAngle}，避免另写一份算法。
     *
     * @param fixed      已绑定端点位置
     * @param fixedAngle 已绑定端点方向（度）
     * @param free       未绑定端点位置
     * @return free 点处的切向角（度）；无解时返回 {@code null}
     */
    public static Double maxRadiusTangentAngle(BlockPos fixed, double fixedAngle, BlockPos free) {
        return RailCalculator.calculateMaxRadiusAngle(
                fixed.getX(), fixed.getZ(), free.getX(), free.getZ(), Math.toRadians(fixedAngle));
    }

    /**
     * 把“度”转成 {@link RailAngle}。
     * <p>
     * 落在 22.5° 栅格上的角度仍取真实枚举常量（保持 MTR 内部基于引用的判断语义），
     * 其余角度用 {@link RailAngleExtra} 伪造任意角度实例。
     */
    public static RailAngle angleFor(double degrees) {
        final double normalized = RailAngleExtra.normalizeDegrees(degrees);
        final double grid = normalized / ANGLE_GRID;
        if (Math.abs(grid - Math.rint(grid)) < 1.0E-6D) {
            return RailAngle.fromAngle((float) normalized);
        }
        return RailAngleExtra.fromDegrees(normalized);
    }

    /**
     * 按 MTR 原版语义，把节点存储方向对齐到连线方向：
     * 起点方向若与连线“同向”则保持不变，否则取反；终点方向若与连线“同向”则取反（终点朝向来路）。
     */
    public static double[] alignToConnection(BlockPos posStart, double startDegrees, BlockPos posEnd, double endDegrees) {
        return alignToConnection(posStart, startDegrees, posEnd, endDegrees, straightAngle(posStart, posEnd));
    }

    /**
     * 与 {@link #alignToConnection(BlockPos, double, BlockPos, double)} 相同，但<b>显式给出</b>
     * 用于朝向比较的弦向角。
     * <p>
     * 存在的意义：节点有平移时，真正的连线是<b>两个锚点</b>（方块坐标 + 节点平移）之间的连线，
     * 服务端重建轨道用的就是那条连线（见 {@link #refreshOneDirection} 的 {@code anchorDegrees}）。
     * 界面做「这个姿态能不能建出轨道」的预检时若不带上平移，就会与服务端实际用的对齐角不一致，
     * 出现「界面说没问题、服务端建不出来」或反过来。这里把对齐角参数化，让两条路径共用同一段算术。
     *
     * @param chordDegrees 用于朝向比较的弦向角（度）；节点有平移时传锚点连线的方位角
     */
    public static double[] alignToConnection(BlockPos posStart, double startDegrees, BlockPos posEnd, double endDegrees,
                                             double chordDegrees) {
        final double angleDifference = chordDegrees;
        final double start = normalizeDegrees(startDegrees
                + (RailAngle.similarFacing((float) angleDifference, (float) startDegrees) ? 0 : 180));
        final double end = normalizeDegrees(endDegrees
                + (RailAngle.similarFacing((float) angleDifference, (float) endDegrees) ? 180 : 0));
        return new double[]{start, end};
    }

    // ==================== 节点状态读写 ====================

    /**
     * 该位置是否为我们注册的万向节点。
     */
    public static boolean isMultiDirectionNode(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof com.fangsu.blocks.BlockMultiDirectionNode;
    }

    /**
     * 读取节点方向的度数。万向节点取 BE 存储值；普通 MTR 节点取 blockstate 角度。
     */
    public static double getDirectionDegrees(Level level, BlockPos pos) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityMultiDirectionNode node) {
            return node.getDirectionDegrees();
        }
        return BlockNode.getAngle(level.getBlockState(pos));
    }

    /**
     * 读取节点方向。万向节点取 BE 的任意角度；普通 MTR 节点取 blockstate 角度（22.5° 栅格枚举）。
     */
    public static RailAngle railAngleAt(Level level, BlockPos pos) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityMultiDirectionNode node) {
            return node.getRailAngle();
        }
        if (level.getBlockState(pos).getBlock() instanceof BlockNode) {
            return RailAngle.fromAngle(BlockNode.getAngle(level.getBlockState(pos)));
        }
        return null;
    }

    /**
     * 方向是否已绑定。
     * <p>
     * 万向节点读 BE NBT；普通节点恒 true —— blockstate 角度即其绑定方向（与原版节点语义一致），
     * 连接时视为已绑定、保留角度。
     */
    public static boolean isBonded(Level level, BlockPos pos) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityMultiDirectionNode node) {
            return node.isDirectionBonded();
        }
        return true;
    }

    /**
     * 读取连接状态。万向节点读 blockstate IS_CONNECTED（由 MTR 连接器 / 轨道删除逻辑维护）；
     * 普通节点同样读 blockstate。
     */
    public static boolean isConnectedAt(Level level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockNode && state.hasProperty(BlockNode.IS_CONNECTED)) {
            return state.getValue(BlockNode.IS_CONNECTED);
        }
        return false;
    }

    /**
     * 把万向节点绑定到指定角度（普通节点忽略）。
     */
    public static void bindNode(Level level, BlockPos pos, double degrees) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityMultiDirectionNode node) {
            node.bind(degrees);
        }
    }

    /**
     * 标记节点已连接：blockstate IS_CONNECTED = true，并同步万向节点 BE 的缓存状态。
     * <p>
     * 仅改 blockstate 而不重建方块（{@code setBlockAndUpdate} 对同一 EntityBlock 会保留方块实体）。
     */
    public static void markConnected(Level level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockNode && state.hasProperty(BlockNode.IS_CONNECTED)
                && !state.getValue(BlockNode.IS_CONNECTED)) {
            level.setBlockAndUpdate(pos, state.setValue(BlockNode.IS_CONNECTED, true));
        }
        syncConnectedCache(level, pos);
    }

    /**
     * 把 blockstate 的连接状态同步进万向节点 BE 的缓存 / NBT（普通节点为空操作）。
     */
    public static void syncConnectedCache(Level level, BlockPos pos) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityMultiDirectionNode node) {
            node.syncConnectedCache();
        }
    }

    // ==================== 节点锚点平移 ====================

    /**
     * 读取某方块位置处的节点锚点平移 {@code {x, y, z}}（格）。
     * <p>
     * 万向节点取方块实体里的值；普通 MTR 节点或任意非节点方块返回全零（原版行为）。
     */
    public static double[] readNodeOffset(Level level, BlockPos pos) {
        final BlockEntityMultiDirectionNode node = multiDirectionNodeAt(level, pos);
        if (node == null) {
            return new double[]{0.0D, 0.0D, 0.0D};
        }
        return new double[]{node.getOffsetX(), node.getOffsetY(), node.getOffsetZ()};
    }

    /**
     * 读取某方块位置处的万向节点方块实体；非万向节点（普通 MTR 节点或别的方块）返回 {@code null}。
     * <p>
     * 客户端 / 服务端都安全：只做一次 {@code getBlockEntity} 查询，不发包、不改世界、不加载区块。
     * 姿态读取（平移 / 俯仰 / 翻滚 / 外轨超高开关 / 半轨距）共用本方法，避免每个量各查一次方块实体。
     */
    private static BlockEntityMultiDirectionNode multiDirectionNodeAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        final BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof BlockEntityMultiDirectionNode node ? node : null;
    }

    /**
     * 端点的滚转贡献（外轨超高开关的唯一作用点）。
     * <p>
     * <b>开关关闭时返回 0</b>，于是 {@link RailPoseExtra#hasRoll()} 为 false，
     * 几何内核的中心线抬升 {@code 半轨距·|sin(roll)|} 整体消失，轨道回到无超高的水平截面。
     * 节点自身仍保留 {@code rollDeg}（模型照常倾斜、界面照常显示），只是不再参与轨道几何。
     * <p>
     * <b>俯仰（纵坡）不经过本方法</b>：纵坡是独立功能，无论开关如何都照常进入
     * {@code RailPoseExtra.pitch1Degrees/pitch2Degrees}（它另有自己的节点帧 → 轨道帧换算，
     * 见 {@link #readRailPose}）。
     */
    private static double rollContribution(BlockEntityMultiDirectionNode node) {
        return rollContribution(node.isSuperelevationEnabled(), node.getRollDegrees());
    }

    /**
     * {@link #rollContribution(BlockEntityMultiDirectionNode)} 的纯函数版本（供
     * {@link #railPose} 与数值探针复用，不依赖方块实体实例）。
     *
     * @param superelevationEnabled 该端点的外轨超高开关
     * @param rollDegrees           该端点的原始翻滚角（度）
     * @return 开关关闭时恒 {@code +0.0}，否则原样返回翻滚角
     */
    static double rollContribution(boolean superelevationEnabled, double rollDegrees) {
        return superelevationEnabled ? rollDegrees : 0.0D;
    }

    /** 供探针断言「纯函数版本与方块实体版本一致」使用。 */
    static double rollContributionOf(BlockEntityMultiDirectionNode node) {
        return rollContribution(node);
    }

    // ==================== 翻滚角的节点帧 → 轨道帧换算 ====================
    //
    // 注：俯仰（纵坡）走的是<b>另一套</b>换算，见下方「俯仰角的节点帧 → 轨道帧换算」。
    // 翻滚只要 ±1 的帧符号就够：几何内核的中心线抬升 半轨距·|sin(roll)| 对符号不敏感（抬哪一侧），
    // 参数方向那一步由渲染层的 railFrameAngle 补上。而俯仰被内核<b>直接</b>当作参数系的端点切线
    // 消费，没有 railFrameAngle 那一步，所以它必须自己做完整投影（pSign × dot，见 pitchFrameAngle）。
    // 两个特性因此<b>刻意分成两个函数</b>：改其中一个之前先读清楚另一个，别把两者合并。
    //
    // 问题：节点的翻滚角是「节点自身方向 d 的右手侧抬高」，但轨道几何 / 渲染的翻滚参数是
    // 定义在**轨道参数系**上的（渲染帧约定：roll > 0 抬升 Rail.position1 → Rail.position2
    // 的右手侧）。而 MTR 的 Rail.position1/position2 是**玩家建轨顺序**，也就是说轨道参数方向
    // 与「节点的方向」毫无关系，同一条连线换个方向建、或换个端点先点，参数方向就可能整个反过来。
    // 于是同一个节点的同一个翻滚角，会在两条相邻轨道上落到相反的一侧（V 形扭结）。
    //
    // 解法：把节点方向投影到轨道方向上，用投影的符号把角度换算到轨道帧。定义
    //     dx/dz = 从 Rail.position1 指向 Rail.position2 的水平向量（= 建轨方向 A，两端同用）
    //     s     = sign(dot(d, A)) —— d 与 A 同向 → +1，反向 → -1
    // 依据：渲染帧抬升的是 A 的右手侧；在 position1 端 A 是**出发**方向、在 position2 端 A 是
    // **到达**方向，两者都是「轨道参数增大方向」，所以两端都用同一个 A，不需要再翻符号。
    //
    // 不变量：本换算只是对单个端点值乘 ±1，
    //   - 正是 ±1 所以「是否为 0」「|·| 的大小比较」全部不变 → 下面的半轨距权威判据不受影响；
    //   - 内核的中心线抬升 半轨距·|sin(roll)| 对符号不敏感 → 几何抬升量不变；
    //   - 只有渲染帧的「抬哪一侧」会跟着变，这正是本换算的目的。
    //
    // MTR3 与 MTR4 的差异（见 parameterSign）：MTR3 的轨道参数方向恒等于
    // position1 → position2 的弦向，因此这里的 frameSign 与 pitchFrameAngle 里的 pSign
    // 在 MTR3 上不会互相抵消或叠加出错误符号 —— pSign 恒为 +1，见 parameterSign 的注释。

    /** 方向投影判定的死区：{@code |dot|} 小于它时视为「节点方向与轨道方向近乎垂直」，不翻转符号。 */
    private static final double FRAME_SIGN_DEADBAND = 1.0E-3D;

    /** 上述死区回退是否已经报告过（一次性日志，避免刷屏）。 */
    private static boolean warnedFrameSignDeadband = false;

    /**
     * 单端点帧符号（<b>只服务翻滚</b>；俯仰用的是 {@link #pitchFrameAngle}，不要拿本方法算俯仰）：
     * 把「节点自身方向的右手侧抬高」换算成「轨道参数系的某一侧抬高」。
     *
     * @param dx             从 {@code Rail.position1} 指向 {@code Rail.position2} 的水平向量 X 分量（不必归一化）
     * @param dz             同上，Z 分量
     * @param directionDegrees 该端点的节点方向（度，0=E、90=S，即 {@code atan2(dz, dx)} 的度数）
     * @return {@code +1}（节点方向与轨道方向同向）或 {@code -1}（反向）；
     *         两者近乎垂直（{@code |dot| < }{@link #FRAME_SIGN_DEADBAND}）时回退为 {@code +1}，
     *         并打印一次性 warn 让「从没转过方向的节点」可见。
     *         <p>
     *         <b>这个 +1 回退只对翻滚成立</b>（翻滚的抬升量对符号不敏感，回退只是「抬哪一侧」的
     *         约定问题）。俯仰<b>绝不能</b>这样回退：那会让同一节点两条轨道拿到相同符号并把坡度
     *         算成 tan(pitch) 而不是 0，渲染出来就是驼峰 —— 这正是 MTR4 的 FIX-PITCH-2 修掉的缺陷。
     *         <p>
     *         <b>这是翻滚换算唯一改动符号的地方</b>，不要在别处再翻一次。
     */
    private static double frameSign(double dx, double dz, double directionDegrees) {
        final double len = Math.hypot(dx, dz);
        if (len < 1.0E-9D) {
            return 1.0D;
        }
        // 节点方向的水平单位向量：节点方向是与 straightAngle 同一套的罗盘角
        // （0=E、90=S、180=W、270=N，即 atan2(dz, dx) 的度数），所以方向向量就是 (cos, sin)。
        final double nodeRadians = Math.toRadians(directionDegrees);
        final double dot = (dx / len) * Math.cos(nodeRadians) + (dz / len) * Math.sin(nodeRadians);
        if (Math.abs(dot) < FRAME_SIGN_DEADBAND) {
            // 节点方向与轨道方向近乎垂直（例如从未旋转绑定过的节点，方向仍是默认 0=东，
            // 却连了一条南北向的轨道）。此时「节点方向的右手侧」在轨道横断面上没有明确对应，
            // 保持角度符号不变（等价于按参数方向右侧处理），并一次性报出来。
            if (!warnedFrameSignDeadband) {
                warnedFrameSignDeadband = true;
                Main.LOGGER.warn("[NodeRoll] 万向节点方向 {}° 与轨道方向近乎垂直（dot={}），"
                                + "翻滚角无法换算到轨道帧，按 +1 处理；请检查该节点是否已旋转绑定到轨道方向",
                        directionDegrees, dot);
            }
            return 1.0D;
        }
        return Math.signum(dot);
    }

    // ==================== 俯仰角的节点帧 → 轨道帧换算 ====================
    //
    // 【权威表述，请勿「简化」】节点 N 的 pitchDeg 定义的是<b>节点自身方向 d 上的纵坡</b>：
    // tan(pitch) = 沿 d 水平前进 1 格时的高度增量。轨道要落在同一个高度场上，因此在节点处
    // 每条相连轨道的切线必须满足
    //     slope_kernel(端点) = tan(pitch) · dot(d, incrDir)
    // 其中 slope_kernel 是内核 Hermite 的端点切线 dy/d(param)（内核 hermitePositionY 用
    // slope·length，所以写进 RailPoseExtra 的角度取 tan 后<b>就是</b>这个切线值），
    // incrDir 是该轨道在内核参数系里「参数增大方向」的水平单位向量。
    //
    // incrDir 怎么来：内核参数 0 端是 firstPosition、沿 secondPosition 增大。MTR4 在
    // reversePositions（position1.compareTo(position2) > 0）时把内核构造成
    // (position2, …, position1, …)，于是 incrDir = pSign · chordUnit；MTR3 <b>不做</b>这种交换
    // （见 parameterSign），incrDir 恒等于 chordUnit = 单位(position1 → position2)（锚点差，含节点平移）。
    // 关键：incrDir 在<b>两端是同一个向量</b>。参数是从 position1 一路增大到 position2，所以位置 2 端的
    // 切线方向仍然是「从 position1 指向 position2」（到达方向），<b>不是</b>反向。因此不需要任何
    // 「端点在哪一头就翻一次符号」的额外因子；符号差异全部来自两条轨道各自的 incrDir。
    //
    // 关于「一侧按正值、一侧按负值」：那只是两条轨道参数方向相反时<b>存储值</b>的表现，
    // 不是要求本身。真正的判据是「节点处纵坡连续」——两条轨道在 N 处落在同一个倾斜平面上。
    // 共线时 incrDir 相反 → 存储值必然一正一负；两条轨道的建轨顺序相同时（pSign 相同）也可能
    // 存储成 +/+，那同样是正确的。判定标准只有一条：斜率 = tan(pitch) · dot(d, incrDir)。
    //
    // 两点与「只取符号」版本的差别：
    //   1) 不再只取符号，而是把 dot 的<b>大小</b>也乘进去：节点方向与轨道斜交时，轨道沿自己的方向
    //      只分到 cos(夹角) 的坡度（节点方向垂直于轨道时正好 0 = 该处水平）。这是「落在同一个倾斜
    //      平面上」的必然结果；节点方向沿/逆轨道（自动绑定）时 |dot| = 1，结果与只取符号逐位相同。
    //   2) 垂直情形<b>不再回退成 +1</b>。回退会让同一节点的两条轨道拿到不同的存储符号
    //      （一条 +tan、一条 −tan），渲染出来正是驼峰。投影到 0 后两条轨道都水平，
    //      坡度自然连续；只保留一次 warn 提示「该节点方向与轨道垂直，俯仰在这条轨道上没有分量」。
    //
    // 为什么必须用 atan：写进 RailPoseExtra 的是<b>角度</b>，内核会对它取 tan 当切线。要求切线等于
    // tan(pitch)·k，所以存的角度必须是 atan(tan(pitch)·k)，<b>不能</b>写成 pitch·k ——
    // 那是角度线性缩放（tan(pitch·k) ≠ tan(pitch)·k）。k = pSign · dot(d, chordUnit) ∈ [-1, 1]，
    // 所以 |存进去的角度| ≤ |pitch| ≤ MAX_PITCH_DEG，不存在除零、溢出、Hermite 爆掉的风险。
    //
    // 数值验证：见 .tmp_p5_2_probe/RailPoseProbe.java（真实 MTR3 Rail + 真实内核）：
    //   共线直线 × 两条轨道的 2×2 建轨顺序 × {沿 d、逆 d、垂直 d} × pitch = ±10°，
    //   全部满足 slope = tan(pitch)·dot(d, 该轨道自己参数方向)，且节点两侧完全一致。

    /** 俯仰投影中「节点方向与轨道方向近乎垂直」的判定阈值：只用于一次性日志，<b>不影响取值</b>。 */
    private static final double PITCH_PERPENDICULAR_EPSILON = 1.0E-3D;

    /** 俯仰垂直情形的一次性日志标记（与翻滚的死区标记分开，互不吞掉对方提示）。 */
    private static boolean warnedPitchPerpendicular = false;

    /**
     * 单端点纵坡：把「节点方向 d 上的纵坡 pitch」<b>投影</b>到这条轨道的内核参数方向上，
     * 返回写入 {@code RailPoseExtra.pitchNDegrees} 的角度。
     * <p>
     * {@code 存进去的角度 = atan( tan(pitch) · pSign · dot(d, chordUnit) )}。
     *
     * @param dx             从 {@code Rail.position1} 指向 {@code Rail.position2} 的水平向量 X 分量（锚点差，不必归一化）
     * @param dz             同上，Z 分量
     * @param directionDegrees 该端点的节点方向（度）
     * @param pitchDegrees   该端点的节点俯仰角（度）
     * @param pSign          MTR 的轨道参数方向符号，见 {@link #parameterSign}
     * @return 内核参数系里的纵坡角（度）；{@code pitch == 0} 或水平方向退化时返回 {@code +0.0}
     */
    private static double pitchFrameAngle(double dx, double dz, double directionDegrees, double pitchDegrees, double pSign) {
        final double pitch = pitchDegrees;
        if (pitch == 0.0D) {
            // 默认姿态不变式：0 必须原样返回 +0.0（RailPoseExtra.DEFAULT 逐位相等，isDefault() 才成立）
            return 0.0D;
        }
        final double len = Math.hypot(dx, dz);
        if (len < 1.0E-9D) {
            // 水平方向退化（两个锚点重合）：没有 incrDir，就没有可承载的纵坡
            return 0.0D;
        }
        final double nodeRadians = Math.toRadians(directionDegrees);
        final double dot = (dx / len) * Math.cos(nodeRadians) + (dz / len) * Math.sin(nodeRadians);
        final double k = pSign * dot;
        if (Math.abs(k) >= 1.0D - 1.0E-12D) {
            // 节点方向正好沿/逆这条轨道（自动绑定）：直接给出 ±pitch，
            // 与只取符号的版本存储角<b>逐位相同</b> —— 这一支保证自动绑定的轨道几何没有任何回归。
            return k > 0.0D ? pitch : -pitch + 0.0D;
        }
        if (Math.abs(dot) < PITCH_PERPENDICULAR_EPSILON && !warnedPitchPerpendicular) {
            warnedPitchPerpendicular = true;
            Main.LOGGER.warn("[NodePitch] 万向节点方向 {}° 与轨道方向近乎垂直（dot={}），"
                            + "俯仰在该轨道上没有分量，节点处按水平处理；请检查该节点是否已旋转绑定到轨道方向",
                    directionDegrees, dot);
        }
        return Math.toDegrees(Math.atan(Math.tan(Math.toRadians(pitch)) * k));
    }

    /**
     * 轨道参数方向符号：把「position1 → position2 的弦向」换算成「内核参数增大方向」。
     * <p>
     * <b>MTR3 与 MTR4 在这里是<b>不同</b>的，这是移植本特性最关键的适配点，不要照抄 MTR4。</b>
     * <ul>
     *   <li><b>MTR4</b>：{@code Rail} 构造器在 {@code reversePositions = position1.compareTo(position2) > 0}
     *       时把 {@code RailMath} 构造成 {@code (position2, …, position1, …)}，所以内核参数 0 端是
     *       {@code position1.compareTo(position2) <= 0} 的那一端。因此 MTR4 必须显式比较
     *       {@code org.mtr.core.data.Position.compareTo}（<b>x → y → z</b> 字典序）。</li>
     *   <li><b>MTR3</b>：{@code Rail(BlockPos posStart, RailAngle facingStart, BlockPos posEnd, RailAngle facingEnd, …)}
     *       构造器<b>不交换端点</b>（源码逐行核对：{@code yStart = posStart.getY()}、
     *       {@code vecDifference = posEnd − posStart}、{@code tStart1} 由 {@code xStart} 得、
     *       {@code tEnd1/tEnd2} 由 {@code xEnd/zEnd} 得），{@code getPosition(0)} 端恒为
     *       {@code posStart}（= {@code position1}）。调用方也一律按这个顺序构造并把轨道登记到
     *       {@code rails[position1][position2]}（{@code ItemRailModifier.onConnect} 的
     *       {@code rail1 = new Rail(posStart, …, posEnd, …)} 与
     *       {@code NodeConnector.createAndSendRail} 都是如此）。</li>
     * </ul>
     * 因此 MTR3 上内核参数增大方向<b>恒等于</b> position1 → position2 的弦向，本方法返回
     * {@code +1}。若照抄 MTR4 的 {@code Position.compareTo} 字典序，在「position1 的字典序更大」
     * 的斜向轨道上就会得到一个错误的 {@code -1}，把该端纵坡整体反号 —— 节点处出现驼峰。
     * <p>
     * <b>为什么仍保留参数与函数形状</b>：① 调用方（{@code pitchFrameAngle} / {@code readRailPose}）
     * 需要显式表达「这里做过一次帧换算」，而不是把符号悄悄藏在别处；② 万一将来 MTR3 也引入
     * 端点交换（或本模组改走 {@code Position} 排序建轨），只需要改这一个返回值，
     * 其余投影逻辑一字不动。{@link #compareXyz(BlockPos, BlockPos)} 保留 {@code Position.compareTo}
     * 的字典序实现，供那一天直接复用，也供探针断言「两种排序在斜向轨道上确实不同」。
     *
     * @return 恒 {@code +1}（MTR3 内核参数 0 端就是 position1）
     */
    private static double parameterSign(BlockPos pos1, BlockPos pos2) {
        return 1.0D;
    }

    /**
     * 与 {@code org.mtr.core.data.Position.compareTo} 同为 <b>x → y → z</b> 字典序的整数比较。
     * <p>
     * <b>不能</b>用 {@code net.minecraft.core.BlockPos.compareTo}：1.20.1 的 {@code Vec3i.compareTo}
     * 是 <b>y → z → x</b>，在斜向轨道（dx、dz 都不为 0）上两者会给出相反的顺序
     * （真实类实测：{@code BlockPos(0,64,5).compareTo(BlockPos(10,64,0)) = +5}，
     * 而 {@code Position(0,64,5).compareTo(Position(10,64,0)) = -1}）。
     * MTR3 上 {@link #parameterSign} 恒为 +1，本方法不被生产路径消费，只保留给
     * 「将来 MTR3 也引入端点交换」的复用与探针断言。
     */
    private static int compareXyz(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return Integer.compare(a.getX(), b.getX());
        }
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    // ==================== 附加姿态计算 ====================

    /**
     * 读取某方块位置处的「单端附加姿态」：只有端点 1 的字段被填充，端点 2 全零。
     * 调用方需要自行决定这个姿态属于轨道的哪一端（见 {@link #readRailPose}）。
     * <p>
     * <b>P5-2</b>：俯仰 / 翻滚 / 半轨距从这里开始进入轨道姿态：
     * <ul>
     *   <li>{@code pitch1Degrees} = 节点 {@code pitchDeg} <b>原样</b>给出（<b>不做</b>节点帧 → 轨道帧
     *       换算：这里没有另一端的锚点，算不出轨道弦向）。真正写进轨道的换算在
     *       {@link #readRailPose} 里，用的是 {@link #pitchFrameAngle}；本方法只给单端诊断/预览用，
     *       所以它的俯仰是<b>节点帧</b>的值，不是内核参数系的值。驱动几何内核的三次 Hermite
     *       竖向剖面（纵坡），<b>不受外轨超高开关影响</b>；</li>
     *   <li>{@code roll1Degrees} = 节点 {@code rollDeg} —— 但外轨超高开关关闭时写 0（见
     *       {@link #rollContribution}），驱动内核的 {@code 半轨距·|sin(roll)|} 中心线抬升；</li>
     *   <li>{@code halfGauge} = 节点 {@code rollOffsetM}（半轨距，米）。</li>
     * </ul>
     * 非万向节点（普通 MTR 节点 / 空位置）贡献全零 + 默认半轨距，即 {@link RailPoseExtra#DEFAULT}，
     * 完全等价于原版行为。
     */
    public static RailPoseExtra readNodePose(Level level, BlockPos pos) {
        final BlockEntityMultiDirectionNode node = multiDirectionNodeAt(level, pos);
        if (node == null) {
            return RailPoseExtra.DEFAULT;
        }
        return new RailPoseExtra(
                node.getOffsetX(), node.getOffsetY(), node.getOffsetZ(),
                0.0D, 0.0D, 0.0D,
                node.getRollOffsetM(),
                node.getPitchDegrees(), 0.0D,
                rollContribution(node), 0.0D
        );
    }

    /**
     * 合成一条轨道的附加姿态：{@code offset1} 属于 {@code Rail.position1}（= 建轨时的 pos1 /
     * 构造器的 posStart），{@code offset2} 属于 {@code Rail.position2}。
     * <p>
     * 这正是 {@code new Rail(posStart, facingStart, posEnd, facingEnd, …)} 的参数顺序，
     * 因此可以直接与 {@code RailPoseExtra} 的端点语义对应 —— MTR3 <b>不做</b> MTR4 那种
     * {@code reversePositions} 端点交换（见 {@link #parameterSign}），所以这里不需要 MTR4
     * {@code FangSuRailMath} 里的 {@code firstIsPosition1} 端点映射。
     * <p>
     * <b>端点 → 姿态字段的完整映射</b>
     * <ul>
     *   <li>{@code pitch1/2Degrees} = 该端点万向节点的 {@code pitchDeg}（非万向节点端点 → 0），
     *       <b>但先用 {@link #pitchFrameAngle} 投影到内核参数系</b>
     *       （{@code atan(tan(pitch) · pSign · dot(d, chordUnit))}）。写入内核的三次 Hermite 纵坡剖面
     *       （端点切线 = {@code tan(轨道帧俯仰角)}），<b>不受外轨超高开关门控</b>。
     *       节点方向沿/逆轨道时该投影退化为 ±pitch。</li>
     *   <li>{@code roll1/2Degrees} = 该端点万向节点的 {@code rollDeg}，<b>但当该端点的外轨超高开关
     *       关闭时写 0</b>（{@link #rollContribution}）。开关是按节点存的，所以门控也是逐端点的：
     *       一端关、另一端开时，只有关闭端的滚转被抹掉，另一端照常贡献。
     *       写 0 使 {@code hasRoll()} 为 false，内核的 {@code 半轨距·|sin(roll)|} 抬升随之消失。
     *       <p>
     *       写入前还要乘一次「端点帧符号」{@link #frameSign}，把「节点自身方向的右手侧抬高」
     *       换算成「轨道参数系的某一侧抬高」。纯 ±1 缩放：0 / 非 0 与 {@code |·|} 的全部比较都不受影响，
     *       但对渲染的「抬哪一侧」是决定性的。注意 {@code roll1Degrees/roll2Degrees} 因此是
     *       <b>轨道帧</b>的值，不再是节点方向的原始角度符号。</li>
     *   <li>{@code halfGauge} = <b>整条轨道只取一个值</b>（{@code RailPoseExtra} 就是这么设计的）。
     *       取值来源是「<b>实际贡献滚转</b>的端点」，贡献判定复用 {@link #rollContribution}：
     *       该端点 {@code superelevation} 开关打开<b>且</b> {@code rollDeg != 0}。规则：
     *       <ul>
     *         <li>只有一端贡献滚转 → 用那一端的 {@code rollOffsetM}；</li>
     *         <li>两端都贡献滚转 → 用 {@code |rollDeg|} 较大的一端；两者完全相等时取端点 1；</li>
     *         <li>两端都不贡献（纯普通 MTR 轨道、或开关全关 / 角度全为 0）→
     *             {@link RailPoseExtra#DEFAULT_HALF_GAUGE}。</li>
     *       </ul>
     *       因此结果<b>不依赖两个端点的传入顺序</b>，唯一例外是两端 {@code |rollDeg|} <b>完全相等</b>
     *       的平局情形，那时固定取端点 1 以保证确定性。</li>
     * </ul>
     * <b>默认姿态不变式</b>：两端都是普通 MTR 节点、或万向节点的俯仰 / 翻滚都是 0 且平移也是 0 时，
     * 返回的姿态满足 {@link RailPoseExtra#isDefault()}，因此几何钩子继续返回 MTR 自己的值，
     * 原版轨道几何逐位不变。
     */
    public static RailPoseExtra readRailPose(Level level, BlockPos pos1, BlockPos pos2) {
        final BlockEntityMultiDirectionNode node1 = multiDirectionNodeAt(level, pos1);
        final BlockEntityMultiDirectionNode node2 = multiDirectionNodeAt(level, pos2);
        final double[] offset1 = node1 == null
                ? new double[]{0.0D, 0.0D, 0.0D}
                : new double[]{node1.getOffsetX(), node1.getOffsetY(), node1.getOffsetZ()};
        final double[] offset2 = node2 == null
                ? new double[]{0.0D, 0.0D, 0.0D}
                : new double[]{node2.getOffsetX(), node2.getOffsetY(), node2.getOffsetZ()};
        // 每个端点：方向 + 原始翻滚角 + 外轨超高开关 + 半轨距；非万向节点端点取原版默认值
        // （方向不参与姿态计算，只填 0 占位）。
        return railPose(
                pos1, offset1,
                node1 == null ? 0.0D : node1.getDirectionDegrees(),
                node1 == null ? 0.0D : node1.getRollDegrees(),
                node1 != null && node1.isSuperelevationEnabled(),
                node1 == null ? 0.0D : node1.getPitchDegrees(),
                node1 == null ? RailPoseExtra.DEFAULT_HALF_GAUGE : node1.getRollOffsetM(),
                pos2, offset2,
                node2 == null ? 0.0D : node2.getDirectionDegrees(),
                node2 == null ? 0.0D : node2.getRollDegrees(),
                node2 != null && node2.isSuperelevationEnabled(),
                node2 == null ? 0.0D : node2.getPitchDegrees(),
                node2 == null ? RailPoseExtra.DEFAULT_HALF_GAUGE : node2.getRollOffsetM()
        );
    }

    /**
     * {@link #readRailPose} 的<b>纯函数版本</b>：不碰世界、不碰方块实体，只做「端点参数 → 附加姿态」
     * 的换算。存在的意义有两个：
     * <ul>
     *   <li>让「节点帧 → 轨道帧」这套最容易出符号错的换算可以在不启动游戏、不加载方块实体的情况下
     *       用真实 MTR3 类做数值探针（见 {@code .tmp_p5_2_probe/RailPoseProbe.java}）；</li>
     *   <li>把 {@code readRailPose} 里「读方块实体」与「算姿态」两件事分开，读侧改了不会碰算侧。</li>
     * </ul>
     *
     * @param pos1          {@code Rail.position1}（构造器的 posStart）
     * @param offset1       端点 1 的节点锚点平移 {@code {x, y, z}}（非万向节点给全零）
     * @param direction1Deg 端点 1 的节点方向（度，0=E、90=S）；非万向节点给任意值（不参与计算）
     * @param roll1Deg      端点 1 的节点翻滚角（度，<b>未经外轨超高开关门控</b>的原始值）
     * @param superelevation1 端点 1 的外轨超高开关是否开启
     * @param pitch1Deg     端点 1 的节点俯仰角（度）
     * @param halfGauge1    端点 1 的半轨距（米）
     * @return 该轨道的附加姿态，端点语义与构造器参数顺序一致
     */
    public static RailPoseExtra railPose(
            BlockPos pos1, double[] offset1, double direction1Deg,
            double roll1Deg, boolean superelevation1, double pitch1Deg, double halfGauge1,
            BlockPos pos2, double[] offset2, double direction2Deg,
            double roll2Deg, boolean superelevation2, double pitch2Deg, double halfGauge2
    ) {
        // 翻滚贡献：先按各端点的外轨超高开关门控（关闭 → 0），再乘端点帧符号换算到轨道参数系。
        // 换算只乘 ±1，所以下面半轨距判据里的「是否为 0」「|·| 大小比较」全部不变。
        // 末尾的 + 0.0D 只为把 「0 × (-1) = -0.0」 归一成 +0.0：
        // 默认姿态必须逐位等于 RailPoseExtra.DEFAULT（isDefault() 对 ±0 都成立，但序列化出来
        // 的字符串会差一个负号，没必要引入这种差异）。
        final double frameDx = (pos2.getX() + offset2[0]) - (pos1.getX() + offset1[0]);
        final double frameDz = (pos2.getZ() + offset2[2]) - (pos1.getZ() + offset1[2]);
        final double contribution1 = frameSign(frameDx, frameDz, direction1Deg)
                * rollContribution(superelevation1, roll1Deg) + 0.0D;
        final double contribution2 = frameSign(frameDx, frameDz, direction2Deg)
                * rollContribution(superelevation2, roll2Deg) + 0.0D;
        // 半轨距：整条轨道一个值，只从「实际贡献滚转」的端点取，与传入顺序无关
        // （唯一例外：两端 |rollDeg| 完全相等时固定取端点 1，保证确定性）
        final double halfGauge;
        if (contribution1 != 0.0D && contribution2 == 0.0D) {
            halfGauge = halfGauge1;
        } else if (contribution2 != 0.0D && contribution1 == 0.0D) {
            halfGauge = halfGauge2;
        } else if (contribution1 != 0.0D) {
            halfGauge = Math.abs(contribution1) >= Math.abs(contribution2) ? halfGauge1 : halfGauge2;
        } else {
            halfGauge = RailPoseExtra.DEFAULT_HALF_GAUGE;
        }
        // 俯仰：纵坡不受外轨超高开关影响；投影到内核参数系（非万向节点端点贡献 0）。
        // 完整推导见 pitchFrameAngle 上方的「俯仰角的节点帧 → 轨道帧换算」注释。
        final double pSign = parameterSign(pos1, pos2);
        return new RailPoseExtra(
                offset1[0], offset1[1], offset1[2],
                offset2[0], offset2[1], offset2[2],
                halfGauge,
                pitchFrameAngle(frameDx, frameDz, direction1Deg, pitch1Deg, pSign) + 0.0D,
                pitchFrameAngle(frameDx, frameDz, direction2Deg, pitch2Deg, pSign) + 0.0D,
                contribution1,
                contribution2
        );
    }

    /**
     * 把附加姿态写入轨道并重建几何。
     * <p>
     * 姿态为默认时等价于还原成 MTR 原生几何（P5-1 的钩子在 {@code affectsGeometry()} 为 false 时
     * 根本不参与运算，逐位返回 MTR 自己的值）。写入后由
     * {@link RailPoseExtraHolder#apply} 触发 {@code rebuildRailGeometry()}，
     * 因此不会留下与姿态不一致的脏缓存。
     */
    public static void applyPose(Rail rail, RailPoseExtra pose) {
        RailPoseExtraHolder.apply(rail, pose);
    }

    // ==================== 客户端 ====================

    /**
     * 客户端：查找连接到某节点位置的其他端点（读取 {@code ClientData.RAILS}）。
     * <p>
     * MTR 客户端数据由网络线程写入，这里加锁读取并对并发修改做兜底（照 ItemDisplacementTool）。
     */
    public static List<BlockPos> findConnectedEndpoints(BlockPos nodePos) {
        final List<BlockPos> result = new ArrayList<>();
        final Map<BlockPos, Rail> connections;
        synchronized (mtr.client.ClientData.RAILS) {
            connections = mtr.client.ClientData.RAILS.get(nodePos);
        }
        if (connections == null) {
            return result;
        }
        try {
            result.addAll(connections.keySet());
        } catch (ConcurrentModificationException ignored) {
            // 轨道数据正在更新，放弃本次查询（不崩）
        }
        return result;
    }

    /**
     * 客户端：查询连接到本节点的第一条轨道（供角度界面显示轨道编辑信息用）。
     */
    public static Rail findFirstConnectedRail(BlockPos nodePos) {
        synchronized (mtr.client.ClientData.RAILS) {
            final Map<BlockPos, Rail> connections = mtr.client.ClientData.RAILS.get(nodePos);
            if (connections == null || connections.isEmpty()) {
                return null;
            }
            try {
                return connections.values().iterator().next();
            } catch (ConcurrentModificationException ignored) {
                return null;
            }
        }
    }

    /**
     * 一条「连着某个节点的轨道」：连同它的两个端点一起给出。
     * <p>
     * <b>为什么要带上端点</b>：MTR3 的 {@code mtr.data.Rail} <b>不保存</b>自己的方块坐标
     * （端点信息只存在于 {@code ClientData.RAILS} / {@code RailwayData.rails} 的表键里，
     * 见 {@code ItemRailModifier#onConnect} 的注册路径）。而逐轨道超高的 C2S 通道必须携带两个端点，
     * 让服务端<b>自己</b>重算轨道身份（客户端无法用一个伪造的 id 命中别的轨道）。
     * 因此「解析轨道」在 MTR3 上必须是「解析 (position1, position2, rail) 三元组」，
     * 不能只返回 {@link Rail}。
     *
     * @param position1 轨道的 {@code position1} = 表的<b>外层</b>键 = 构造器的 {@code posStart}
     * @param position2 轨道的 {@code position2} = 表的<b>内层</b>键 = 构造器的 {@code posEnd}
     * @param rail      该方向的轨道实例
     */
    public record ConnectedRail(BlockPos position1, BlockPos position2, Rail rail) {
    }

    /**
     * 客户端：列出所有连接到 {@code nodePos} 的轨道（含各自的端点），顺序<b>确定</b>。
     * <p>
     * 排序键是另一端点的 {@code x → y → z} 字典序（{@link #compareXyz}）。MTR3 的表是
     * {@code HashMap}，迭代顺序不稳定；若界面直接按迭代顺序取「第一条」，
     * 同一次会话里刷新两次都可能换一条轨道，用户会看到「编辑对象自己跳了」。
     * 固定排序让「节点连了多条轨道时默认编辑哪一条」在两次打开之间保持一致。
     * <p>
     * 只读客户端数据，不发包、不改世界。数据未同步（表里没有本节点）时返回空表。
     */
    public static List<ConnectedRail> connectedRails(BlockPos nodePos) {
        final Map<BlockPos, Rail> connections;
        synchronized (mtr.client.ClientData.RAILS) {
            connections = mtr.client.ClientData.RAILS.get(nodePos);
        }
        if (connections == null || connections.isEmpty()) {
            return List.of();
        }
        final List<BlockPos> others = new ArrayList<>(connections.size());
        try {
            others.addAll(connections.keySet());
        } catch (ConcurrentModificationException ignored) {
            return List.of();
        }
        others.sort(NodeConnector::compareXyz);
        final List<ConnectedRail> result = new ArrayList<>(others.size());
        for (final BlockPos other : others) {
            final Rail rail = connections.get(other);
            if (rail != null) {
                result.add(new ConnectedRail(nodePos, other, rail));
            }
        }
        return result;
    }

    // ==================== 服务端：建轨 / 重建 ====================

    /**
     * 服务端：以两个端点位置 + 两个角度，按连接器类型构建一条铁轨并派发到服务端数据。
     * <p>
     * 角度用 {@link RailAngleExtra} 生成精确值，避免 22.5° 快照，使万向节点建出的轨道摆脱
     * 原版 16×22.5° 的离散限制。合法性判定不使用 {@code Rail.isValid()}——它内部用
     * {@code facingStart == getRailAngle(false)} 做枚举引用相等比较，对任意角度必然为假；
     * 这里改为几何退化判定（长度非零且有限）。
     *
     * @return 轨道是否创建成功（false = 几何不成立，如两端绑定方向与连线冲突）
     */
    public static boolean createAndSendRail(
            Level level,
            Player player,
            TransportMode transportMode,
            BlockPos posStart, double startDegrees,
            BlockPos posEnd, double endDegrees,
            RailType railType, boolean isOneWay
    ) {
        if (!(level instanceof ServerLevel)) {
            return false;
        }
        final RailwayData railwayData = RailwayData.getInstance(level);
        if (railwayData == null) {
            return false;
        }

        final double[] aligned = alignToConnection(posStart, startDegrees, posEnd, endDegrees);
        final RailAngle facingStart = angleFor(aligned[0]);
        final RailAngle facingEnd = angleFor(aligned[1]);

        final Rail rail1 = new Rail(posStart, facingStart, posEnd, facingEnd,
                isOneWay ? RailType.NONE : railType, transportMode);
        final Rail rail2 = new Rail(posEnd, facingEnd, posStart, facingStart, railType, transportMode);

        if (!isGeometryUsable(rail1) || !isGeometryUsable(rail2)) {
            Main.debug("[NodeConnector] 轨道几何不成立 {}->{} 角度 {} / {}", posStart, posEnd, aligned[0], aligned[1]);
            return false;
        }

        // 附加姿态（节点平移 / 俯仰 / 翻滚 / 半轨距）：必须在派发之前写入。
        // 两条方向各取自己 position1 端的姿态（readRailPose 的端点语义就是构造器参数顺序）。
        applyPose(rail1, readRailPose(level, posStart, posEnd));
        applyPose(rail2, readRailPose(level, posEnd, posStart));

        railwayData.addRail(player, transportMode, posStart, posEnd, rail1, false);
        final long newId = railwayData.addRail(player, transportMode, posEnd, posStart, rail2, true);
        markConnected(level, posStart);
        markConnected(level, posEnd);
        PacketTrainDataGuiServer.createRailS2C(level, transportMode, posStart, posEnd, rail1, rail2, newId);
        Main.debug("[NodeConnector] 已建轨 {}->{} 角度 {} / {}", posStart, posEnd, aligned[0], aligned[1]);
        return true;
    }

    /**
     * 服务端：按节点当前绑定角度，重建所有连接到该节点的轨道。
     * <p>
     * 用在两个场景：
     * <ul>
     *   <li>世界重载 —— MTR 反序列化轨道时 {@code facingStart/facingEnd} 会经
     *       {@code RailAngle.fromAngle} 量化到 22.5°，重建可恢复精确角度</li>
     *   <li>玩家在角度界面改绑方向 —— 已连接轨道的曲线随之刷新</li>
     * </ul>
     * 另一端角度语义：万向节点取绑定角度，普通节点取 blockstate 角度（均视为固定方向）。
     * 注意 MTR3 的 {@code validateData()} 为私有且只清理无效站台/侧线，此处重建只替换同一连接，
     * 无需触发（照 MTR4 版的实现也不触发）。
     */
    public static void rebuildRailsAtNode(Level level, BlockPos nodePos) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        final BlockEntity entity = level.getBlockEntity(nodePos);
        if (!(entity instanceof BlockEntityMultiDirectionNode node)) {
            return;
        }
        if (!node.isDirectionBonded() || !node.isConnected()) {
            return;
        }
        if (node.getRailAngle() == null) {
            return;
        }

        final RailwayData railwayData = RailwayData.getInstance(level);
        if (railwayData == null) {
            return;
        }
        final Map<BlockPos, Map<BlockPos, Rail>> rails = getRails(railwayData);
        if (rails == null) {
            return;
        }
        final Map<BlockPos, Rail> connections = rails.get(nodePos);
        if (connections == null || connections.isEmpty()) {
            return;
        }

        final List<BlockPos> targets = new ArrayList<>(connections.keySet());
        int rebuilt = 0;
        for (final BlockPos target : targets) {
            final Rail forward = connections.get(target);
            final Map<BlockPos, Rail> backConnections = rails.get(target);
            final Rail backward = backConnections == null ? null : backConnections.get(nodePos);
            if (forward == null || backward == null) {
                continue;
            }
            final RailAngle angleTarget = railAngleAt(level, target);
            if (angleTarget == null) {
                continue;
            }
            // 与建轨时同一套朝向语义：节点存储的是“离开节点的方向”，Rail 需要对齐后的角度
            final double[] aligned = alignToConnection(nodePos, node.getDirectionDegrees(), target, getDirectionDegrees(level, target));
            final RailAngle facingStart = angleFor(aligned[0]);
            final RailAngle facingEnd = angleFor(aligned[1]);

            final Rail newForward = new Rail(nodePos, facingStart, target, facingEnd, forward.railType, forward.transportMode);
            final Rail newBackward = new Rail(target, facingEnd, nodePos, facingStart, backward.railType, backward.transportMode);
            if (!isGeometryUsable(newForward) || !isGeometryUsable(newBackward)) {
                Main.LOGGER.warn("[NodeConnector] 重建轨道几何不成立，跳过 {}->{}", nodePos, target);
                continue;
            }
            // 附加姿态必须在派发之前写入：写入后几何（= 内核读数）才算就位。
            // 姿态两端分别按「轨道自己的 position1 / position2」取，因此 forward 与 backward
            // 两条轨道的姿态参数顺序相反，正好与它们各自的内核参数方向一致。
            applyPose(newForward, readRailPose(level, nodePos, target));
            applyPose(newBackward, readRailPose(level, target, nodePos));

            railwayData.addRail(null, forward.transportMode, nodePos, target, newForward, false);
            railwayData.addRail(null, backward.transportMode, target, nodePos, newBackward, false);
            PacketTrainDataGuiServer.createRailS2C(level, forward.transportMode, nodePos, target, newForward, newBackward, 0);
            rebuilt++;
        }
        if (rebuilt > 0) {
            Main.debug("[NodeConnector] 节点 {} 重建了 {} 条轨道", nodePos, rebuilt);
        }
    }

    /**
     * 服务端：原地重建「连接 nodePos 与 otherPos」的<b>两条方向</b>轨道，并写入各自的附加姿态。
     * <p>
     * <b>为什么不删除旧轨</b>（与 {@link #rebuildRailsAtNode} 的既有选择一致）：MTR3 的
     * {@code addRail} 对同一 {@code (posStart, posEnd)} 键是覆盖写入，{@code createRailS2C}
     * 随后把结果广播给所有客户端；先删后建会出现「旧轨已删、新轨未到」的瞬时无轨窗口，
     * 而且删除回调会把单轨节点误判成「已无轨道」并复位 connected（延迟回调，落地时间不可控）。
     * 因此本方法<b>只做覆盖 + 广播</b>。
     * <p>
     * 角度语义与 {@link #rebuildRailsAtNode} 完全一致：两端各自取
     * {@link #getDirectionDegrees}（万向节点取 BE 绑定角、普通节点取 blockstate 角），
     * 再用 {@link #alignToConnection} 对齐 MTR 的「起点=离开方向 / 终点=进入方向」语义。
     * 因此本方法本身就是方向无关的：它按服务端方块实体里的<b>当前</b>值重算，
     * 所以「客户端刚改完方向、NODE_REFRESH_RAIL 先到、BE_SYNC 后到」这种到达顺序不会再产生
     * 用旧方向或旧姿态重建出来的轨道（MTR4 的 {@code railAction} 预检做法在 MTR3 上无对应
     * API，这里改用「服务端始终重读自己权威的方块实体」这一更简单的等价保证）。
     *
     * @param level      服务端世界
     * @param nodePos    万向节点位置（改姿态/方向的那一端）
     * @param otherPos   另一端位置
     * @param tiltCarry  随刷新请求带来的「作者授权逐轨道超高」；{@code null} 或
     *                   {@link RailTiltCarry#hasRailTilt()} 为 false 时保持节点派生值
     * @return 是否真的重建并派发了（false = 几何不成立 / 端点不是节点 / 数据缺失，
     *         此时<b>旧轨道原样保留</b>）
     */
    public static boolean refreshNodeRail(Level level, BlockPos nodePos, BlockPos otherPos, RailTiltCarry tiltCarry) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        final RailwayData railwayData = RailwayData.getInstance(level);
        if (railwayData == null) {
            return false;
        }
        final Map<BlockPos, Map<BlockPos, Rail>> rails = getRails(railwayData);
        if (rails == null) {
            return false;
        }
        final Map<BlockPos, Rail> nodeConnections = rails.get(nodePos);
        final Map<BlockPos, Rail> otherConnections = rails.get(otherPos);
        if (nodeConnections == null || otherConnections == null) {
            return false;
        }
        // 键存在性判定在真正刷新之后：refreshNodeRail 自己不会删表，所以读到的就是「另一端是否连着」
        final boolean forwardExists = nodeConnections.containsKey(otherPos);
        final boolean backwardExists = otherConnections.containsKey(nodePos);
        if (!forwardExists && !backwardExists) {
            return false;
        }
        final double[] nodeOffset = readNodeOffset(level, nodePos);
        final double[] otherOffset = readNodeOffset(level, otherPos);
        final double anchorDx = (otherPos.getX() + otherOffset[0]) - (nodePos.getX() + nodeOffset[0]);
        final double anchorDz = (otherPos.getZ() + otherOffset[2]) - (nodePos.getZ() + nodeOffset[2]);
        // 服务端读到的就是权威的当前姿态（readRailPose 内部读的是服务端方块实体）
        final RailPoseExtra nodePose = readRailPose(level, nodePos, otherPos);
        final RailPoseExtra otherPose = readRailPose(level, otherPos, nodePos);
        // 逐轨道超高的参考帧是「nodePos → otherPos」（随行数据就是这么打包的），
        // 而反向那条轨道的 position1 是 otherPos，所以它的 start/end 必须对调、
        // 中间控制点位置镜像（控制点在物理上没有移动）。不做这一步，节点重建后
        // 反向轨道的超高会左右颠倒 —— 两条方向都被渲染，用户看到的就是一个扭结。
        final RailTiltCarry backwardCarry = tiltCarry == null ? null : tiltCarry.reversed();
        final boolean created = refreshOneDirection(serverLevel, railwayData, nodePos, otherPos,
                nodeConnections.get(otherPos), anchorDx, anchorDz, nodePose, tiltCarry)
                | refreshOneDirection(serverLevel, railwayData, otherPos, nodePos,
                otherConnections.get(nodePos), anchorDx, anchorDz, otherPose, backwardCarry);
        if (!created) {
            Main.LOGGER.warn("[NodeConnector] refreshNodeRail 无候选几何，旧轨道保留 {}<->{}", nodePos, otherPos);
            return false;
        }
        markConnected(level, nodePos);
        markConnected(level, otherPos);
        Main.debug("[NodeConnector] refreshNodeRail 完成 {}<->{}", nodePos, otherPos);
        return true;
    }

    /**
     * 重建并派发<b>一个方向</b>的轨道（{@code posStart → posEnd}）。
     * <p>
     * 角度按 MTR 的朝向语义对齐：起点取 {@code posStart} 的方向、终点取 {@code posEnd} 的方向，
     * 其中 {@code anchorDx/anchorDz} 是共享的锚点连线向量（两个方向都用同一对锚点，
     * 因此不需要反向重算）。
     *
     * @param existing  该方向的旧轨道（提供 {@code railType} 与 {@code transportMode}）；
     *                  {@code null} = 该方向不存在，直接跳过
     * @param tiltCarry 作者授权的逐轨道超高（{@code null} / 未授权时保持节点派生值）
     * @return 是否派发了一条轨道
     */
    private static boolean refreshOneDirection(
            ServerLevel level, RailwayData railwayData,
            BlockPos posStart, BlockPos posEnd, Rail existing,
            double anchorDx, double anchorDz,
            RailPoseExtra nodePose, RailTiltCarry tiltCarry
    ) {
        if (existing == null) {
            return false;
        }
        final double startDegrees = getDirectionDegrees(level, posStart);
        final double endDegrees = getDirectionDegrees(level, posEnd);
        final double anchorDegrees = Math.toDegrees(Math.atan2(anchorDz, anchorDx));
        final double alignedStart = normalizeDegrees(startDegrees
                + (RailAngle.similarFacing((float) anchorDegrees, (float) startDegrees) ? 0 : 180));
        final double alignedEnd = normalizeDegrees(endDegrees
                + (RailAngle.similarFacing((float) anchorDegrees, (float) endDegrees) ? 180 : 0));
        final RailAngle facingStart = angleFor(alignedStart);
        final RailAngle facingEnd = angleFor(alignedEnd);
        final Rail rebuilt = new Rail(posStart, facingStart, posEnd, facingEnd, existing.railType, existing.transportMode);
        if (!isGeometryUsable(rebuilt)) {
            return false;
        }
        // 附加姿态：节点派生值 +（可选）作者授权的逐轨道超高。
        // 未授权时 tiltCarry 为 null / hasRailTilt() 为 false，mergeInto 原样返回 nodePose ——
        // 这正是「清除逐轨道超高」之后应有的行为（完全跟随节点值）。
        final RailPoseExtra pose = tiltCarry == null ? nodePose : tiltCarry.mergeInto(nodePose);
        applyPose(rebuilt, pose);
        railwayData.addRail(null, existing.transportMode, posStart, posEnd, rebuilt, false);
        PacketTrainDataGuiServer.createRailS2C(level, existing.transportMode, posStart, posEnd, rebuilt, rebuilt, 0);
        return true;
    }

    // ==================== 作者授权的逐轨道超高（随刷新请求送达服务端） ====================

    /**
     * 把客户端读到的一条轨道的「附加姿态」里的<b>逐轨道</b>字段（三点剖面 + 半轨距）
     * 打包成随行数据，供服务端重建时保留作者授权值。
     * <p>
     * <b>为什么需要它</b>：{@code RailPoseExtraHolder.apply} 是<b>整份写入</b>，
     * 服务端重建时派生的姿态只含节点数据（逐轨道字段全是「未授权」），
     * 不带上作者的授权值就会把三点剖面与半轨距一起抹掉。
     * <p>
     * <b>参考帧</b>：三点剖面的 start/end 属于 {@code Rail.position1} / {@code Rail.position2}。
     * MTR3 的 {@code position1} 就是建轨时的 {@code posStart}，而服务端重建恒以
     * {@code (nodePos, otherPos)} 为 {@code position1/position2}，因此调用方必须按
     * 「旧轨道的 position1 是哪一端」决定是否反转 —— 见 {@link RailTiltCarry#fromPose}。
     * <p>
     * <b>生产路径</b>：客户端 {@code BlockEntityMultiDirectionNode#refreshConnectedRailsIfNeeded}
     * 用本方法把每条相连轨道当前的逐轨道字段带上（P5-5 起逐轨道超高界面真的会写出这些字段）；
     * 服务端重建反向轨道时会用 {@code reverseTiltCarry} 把参考帧换过去。
     * 未授权时得到 {@link RailTiltCarry#NONE}，服务端据此保持节点派生值。
     *
     * @param pose     该轨道当前的附加姿态（{@link RailPoseExtraHolder#peek}）
     * @param reversed 参考帧是否需要反转（true = 该轨道的 position1 <b>不是</b>本节点）
     */
    public static RailTiltCarry carryRailTilt(RailPoseExtra pose, boolean reversed) {
        return RailTiltCarry.fromPose(pose, reversed);
    }

    // ==================== 服务端：逐轨道超高的 C2S 落库（P5-5） ====================

    /**
     * 服务端：把一次「逐轨道超高」编辑写进<b>服务端已有的那条轨道</b>，并<b>显式重播</b>给所有玩家。
     * <p>
     * 这是 {@code RailTiltPackets} 的服务端落库路径。之所以放在 {@link NodeConnector} 而不是
     * 包类里：轨道身份（两个端点 → 表键）与轨道几何都归 MTR3 的 {@link RailwayData} 所有，
     * 只有这里能拿到 {@code rails} 表。
     * <p>
     * <b>轨道身份不是 id 而是「两个端点」</b>：MTR3 没有 MTR4 的 {@code TwoPositionsBase.getHexId}，
     * 也没有 {@code Simulator.railIdMap}；它的轨道表就是 {@code rails[position1][position2]}。
     * 本方法用<b>收到的两个端点</b>去查服务端自己的表，因此客户端无论如何都只能命中
     * 「自己报出来的那一对端点上的轨道」——伪造 id 这条路在 MTR3 上根本不存在。
     * 查不到就直接返回 {@code false}（调用方记日志丢弃）。
     * <p>
     * <b>写入是「合并」而不是「替换」</b>：底层用
     * {@link RailPoseExtra#withRailTilt} —— 它只覆盖三个倾斜控制点 + 中间控制点位置 + 半轨距，
     * 两端的平移 / 俯仰 / <b>节点派生的滚转</b>原样保留。所以一个持有过期节点几何的客户端
     * <b>不可能</b>把陈旧的节点姿态写回服务端（这也是 MTR4 同款做法的移植）。
     * <p>
     * <b>中间控制点由服务端按自己的实时几何算</b>（{@link RailTiltSupport#middleBreakpointFraction}），
     * 不信任客户端上报的值：曲线轨上的接缝位置是几何量，客户端数据可能已经过期。
     * <p>
     * <b>两个方向一起写</b>：MTR3 为每一对端点存<b>两条</b> {@code Rail}（{@code rails[a][b]} 与
     * {@code rails[b][a]}），渲染器两条都会画。只写一条会出现「一条轨道带倾斜、另一条水平」
     * 的重叠伪影。反向轨道的三点剖面参考帧与正向相反，因此 start/end 对调
     * （与 {@link RailTiltCarry#fromPose} 的 {@code reversed} 分支同一条换算）。
     * <p>
     * <b>显式重播</b>：MTR3 自己的周期性轨道同步<b>不检测变化</b>（客户端已在
     * {@code existingRailIds} 里的轨道会被跳过），所以每次写都必须主动广播，
     * 否则其他玩家永远看不到。重播走 MTR3 自己的 {@code PACKET_CREATE_RAIL}（全维度广播），
     * 与 {@link #refreshOneDirection} 完全同一条路径；此刻姿态已落在轨道对象上，
     * 广播出去的载荷自然带着新的逐轨道超高（由 {@code RailPoseExtraMixin} 的
     * {@code writePacket} TAIL 追加）。
     *
     * @param level             服务端世界
     * @param position1         轨道 {@code position1}（C2S 载荷原样带来的端点 1）
     * @param position2         轨道 {@code position2}
     * @param tiltDegrees       三个倾斜控制点（起点 / 中间 / 终点，度）；{@code null} = 清除逐轨道超高
     * @param requestedHalfGauge 半轨距（米）；非有限值 = 沿用服务端当前值
     * @return 是否真的写入并重播（false = 服务端表里没有这条轨道 / 非服务端世界）
     */
    public static boolean applyRailTilt(Level level, BlockPos position1, BlockPos position2,
                                        double[] tiltDegrees, double requestedHalfGauge) {
        if (!(level instanceof ServerLevel)) {
            return false;
        }
        if (position1 == null || position2 == null || position1.equals(position2)) {
            return false;
        }
        final RailwayData railwayData = RailwayData.getInstance(level);
        if (railwayData == null) {
            return false;
        }
        final Map<BlockPos, Map<BlockPos, Rail>> rails = getRails(railwayData);
        if (rails == null) {
            return false;
        }
        final Map<BlockPos, Rail> forwardConnections = rails.get(position1);
        final Rail forward = forwardConnections == null ? null : forwardConnections.get(position2);
        if (forward == null) {
            // 服务端自己的表里没有这一对端点 → 客户端数据过期 / 伪造，丢弃
            return false;
        }
        final Map<BlockPos, Rail> backwardConnections = rails.get(position2);
        final Rail backward = backwardConnections == null ? null : backwardConnections.get(position1);

        // 正向：参考帧 = position1 → position2，与 railTiltStart/Middle/EndDegrees 的约定一致
        writeOneRailTilt(forward, tiltDegrees, requestedHalfGauge, false);
        // 反向：参考帧整体反转（它的 position1 就是正向的 position2）
        if (backward != null && backward != forward) {
            writeOneRailTilt(backward, tiltDegrees, requestedHalfGauge, true);
        }
        // 显式重播：一次写两条（载荷里 rail1 = position1→position2、rail2 = position2→position1）。
        // 反向轨道缺失（单向轨的常见形态）时按本工程既有做法重复传正向那条，
        // 见 refreshOneDirection 的 createRailS2C 调用。
        PacketTrainDataGuiServer.createRailS2C(level, forward.transportMode, position1, position2,
                forward, backward == null ? forward : backward, 0);
        return true;
    }

    /**
     * 把逐轨道超高合并进<b>一条</b>轨道并落库（{@link #applyRailTilt} 的单方向实现）。
     * <p>
     * <b>注意顺序</b>：中间控制点位置必须在改姿态<b>之前</b>读（它来自轨道当前几何），
     * 而 {@code RailGeometryMixin} 算接缝读的是 {@code Rail} 的 {@code private final}
     * 几何字段 —— 那些字段不随姿态变化，所以顺序其实不影响正确性；
     * 之所以仍按「先算后写」写，是为了让「读到的就是客户端点应用那一刻的几何」这一语义显式可见。
     *
     * @param reversed true = 该轨道的 position1 是「编辑参考帧」的终点（反向轨道）
     */
    private static void writeOneRailTilt(Rail rail, double[] tiltDegrees, double requestedHalfGauge, boolean reversed) {
        final RailPoseExtra current = RailPoseExtraHolder.peek(rail);
        final double halfGauge = Double.isFinite(requestedHalfGauge) ? requestedHalfGauge : current.halfGauge;
        if (tiltDegrees == null) {
            // 清除：三点剖面回到「未授权」，半轨距按请求（未指定则保持）
            RailPoseExtraHolder.apply(rail, current.withRailTilt(
                    null, null, null,
                    RailPoseExtra.DEFAULT_RAIL_TILT_MIDDLE_FRACTION,
                    halfGauge));
            return;
        }
        final double middleFraction = RailTiltSupport.middleBreakpointFraction(rail);
        // 反向轨道：它的 position1 = 编辑参考帧的终点，所以 start/end 对调。
        // 中间控制点在物理上没有移动，而 middleFraction 是各自参考帧里的位置，因此直接用各自算出的值。
        final RailPoseExtra updated = reversed
                ? current.withRailTilt(tiltDegrees[2], tiltDegrees[1], tiltDegrees[0], middleFraction, halfGauge)
                : current.withRailTilt(tiltDegrees[0], tiltDegrees[1], tiltDegrees[2], middleFraction, halfGauge);
        RailPoseExtraHolder.apply(rail, updated);
    }

    /**
     * 服务端校验：客户端随 {@code NODE_REFRESH_RAIL} 带来的「轨道属性」是否与服务端自己那条旧轨道一致。
     * <p>
     * <b>为什么需要它</b>：重建时的一切属性都取自服务端旧轨道（见 {@link #refreshNodeRail}），
     * 因此客户端本来就没有「伪造属性」的能力。本方法是<b>一致性断言</b>：
     * 若两者不一致，说明客户端与服务端的轨道数据不同步（例如客户端还在用被删掉的旧轨道），
     * 此时宁可跳过这条刷新并记一条 warn，也不要让服务端凭过期的客户端视图重建。
     * <p>
     * 判定内容刻意只包含「客户端能观测到的属性」：{@code RailType} 名字与单向性
     * （MTR3 的单向性表现为正向 {@code RailType.NONE}）。MTR3 的轨道没有形状 / 样式
     * （这些是 MTR4 的模型），所以没有更多可比项。
     *
     * @param railType  客户端上报的轨道类型（已由 {@code EnumHelper} 解析，未知名回退 {@code IRON}）
     * @param isOneWay  客户端上报的单向标记
     * @return true = 服务端确实存在该方向的轨道且属性一致；false = 不存在或属性不符，调用方应跳过
     */
    public static boolean railPropsMatch(Level level, BlockPos nodePos, BlockPos otherPos, RailType railType, boolean isOneWay) {
        if (!(level instanceof ServerLevel)) {
            return false;
        }
        final RailwayData railwayData = RailwayData.getInstance(level);
        if (railwayData == null) {
            return false;
        }
        final Map<BlockPos, Map<BlockPos, Rail>> rails = getRails(railwayData);
        if (rails == null) {
            return false;
        }
        final Map<BlockPos, Rail> connections = rails.get(nodePos);
        if (connections == null) {
            return false;
        }
        final Rail forward = connections.get(otherPos);
        if (forward == null) {
            return false;
        }
        // 单向轨道的正向是 RailType.NONE（反向才是原类型），因此同一条轨道按方向看会得到不同的类型，
        // 这里只比较「反向（非 NONE 的那条）的类型」：客户端读到的可能就是其中任意一条，
        // 所以两种可能性都接受，只要有一条对得上即可。
        final boolean typeMatches = forward.railType == railType
                || (isOneWay && forward.railType == RailType.NONE);
        final Map<BlockPos, Rail> backConnections = rails.get(otherPos);
        final Rail backward = backConnections == null ? null : backConnections.get(nodePos);
        final boolean backwardMatches = backward != null && (backward.railType == railType || (isOneWay && backward.railType == RailType.NONE));
        return typeMatches || backwardMatches;
    }

    /**
     * 几何预检：给定两端<b>原始</b>节点角度（未对齐），判断按 MTR 朝向语义对齐后能否建出有效轨道。
     * <p>
     * 无副作用（只构造临时 {@code Rail} 做长度判定），供连接器在“决定绑定哪个角度”之前调用，
     * 从而做到“建轨成功才绑定”，失败不改变节点绑定状态。
     */
    public static boolean isGeometryValid(BlockPos posStart, double startDegrees, BlockPos posEnd, double endDegrees) {
        return isGeometryValid(posStart, startDegrees, posEnd, endDegrees, straightAngle(posStart, posEnd));
    }

    /**
     * 几何预检的锚点感知版本：用于朝向比较的弦向角由调用方给出（节点有平移时是锚点连线的方位角）。
     * <p>
     * 与服务端重建（{@link #refreshOneDirection}）用的是同一段对齐算术
     * （{@link #alignToConnection(BlockPos, double, BlockPos, double, double)}），
     * 因此界面预检与服务端实际建轨不会出现口径不一致。
     *
     * @param chordDegrees 用于朝向比较的弦向角（度）
     * @see #isGeometryValid(BlockPos, double, BlockPos, double)
     */
    public static boolean isGeometryValid(BlockPos posStart, double startDegrees, BlockPos posEnd, double endDegrees,
                                          double chordDegrees) {
        final double[] aligned = alignToConnection(posStart, startDegrees, posEnd, endDegrees, chordDegrees);
        final RailAngle facingStart = angleFor(aligned[0]);
        final RailAngle facingEnd = angleFor(aligned[1]);
        final Rail forward = new Rail(posStart, facingStart, posEnd, facingEnd, RailType.IRON, TransportMode.TRAIN);
        final Rail backward = new Rail(posEnd, facingEnd, posStart, facingStart, RailType.IRON, TransportMode.TRAIN);
        return isGeometryUsable(forward) && isGeometryUsable(backward);
    }

    /**
     * 几何是否可用：长度非零且有限（{@code Rail} 构造无解时退化为全 0 参数，长度为 0；
     * 出现 NaN 时长度同样非有限）。
     */
    public static boolean isGeometryUsable(Rail rail) {
        if (rail == null) {
            return false;
        }
        final double length = rail.getLength();
        return Double.isFinite(length) && length > LENGTH_EPSILON;
    }

    /**
     * 读取 MTR3 服务端的轨道表：优先走 mixin 接口（Fabric 端 mixin 生效）；
     * Forge 端不加载该 mixin，回退反射 {@link RailwayData} 的 private {@code rails} 字段
     * （MTR 为 official mappings，字段名即源码名）。照 ItemHybridCreator 的既有做法。
     */
    @SuppressWarnings("unchecked")
    private static Map<BlockPos, Map<BlockPos, Rail>> getRails(RailwayData railwayData) {
        if (railwayData.railwayDataRailActionsModule instanceof RailActionsModuleExtraSupplier supplier) {
            return supplier.getRails();
        }
        try {
            final Field field = RailwayData.class.getDeclaredField("rails");
            field.setAccessible(true);
            return (Map<BlockPos, Map<BlockPos, Rail>>) field.get(railwayData);
        } catch (ReflectiveOperationException e) {
            Main.LOGGER.error("[NodeConnector] 读取轨道表失败", e);
            return null;
        }
    }
}
