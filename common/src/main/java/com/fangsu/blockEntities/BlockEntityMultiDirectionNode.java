package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.blocks.ModBlocks;
import com.fangsu.mappings.rail.RailPoseExtra;
import com.fangsu.mtr.RailAngleExtra;
import com.fangsu.mtr.rail.RailPoseExtraHolder;
import com.fangsu.network.ModNetwork;
import com.fangsu.network.NodeRefreshRailPayload;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.util.NodeConnector;
import com.fangsu.utils.ResourceUtil;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.block.BlockNode;
import mtr.data.Rail;
import mtr.data.RailAngle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 万向节点方块实体。
 * <p>
 * 方块本体是 MTR 的 {@link BlockNode} 子类，因此 MTR 的轨道连接器 / 轨道删除逻辑 / 连接状态
 * （blockstate {@code IS_CONNECTED}）全部照常工作；本实体额外承载“任意角度 + 绑定状态”。
 * <p>
 * NBT 存储（与 MTR4 版保持一致）：
 * <ul>
 *   <li>{@code direction} (double) — 当前方向（度，0=东, 90=南, 180=西, 270=北），未绑定时为 0</li>
 *   <li>{@code connected} (bool) — 是否已连接轨道（由 blockstate IS_CONNECTED 派生，单一数据源）</li>
 *   <li>{@code directionBonded} (bool) — 方向是否已绑定；未绑定(false)时模型持续旋转</li>
 * </ul>
 * 交互：扳手 / 刷子右键打开角度配置界面（客户端注入，见 ClientHooks）。
 */
public class BlockEntityMultiDirectionNode extends BaseObjBlockEntity implements Syncable {

    private static final String DEFAULT_MODEL = "fangsu:models/obj/node.obj";
    private static final String CONNECTED_MODEL = "fangsu:models/obj/node_connected.obj";

    // ==================== NBT 键 ====================
    public static final String KEY_DIRECTION = "direction";
    public static final String KEY_CONNECTED = "connected";
    public static final String KEY_DIRECTION_BONDED = "directionBonded";
    /** 节点锚点平移（格，double，钳制到 ±{@value #MAX_OFFSET}）。 */
    public static final String KEY_OFFSET_X = "offsetX";
    public static final String KEY_OFFSET_Y = "offsetY";
    public static final String KEY_OFFSET_Z = "offsetZ";
    /**
     * 节点俯仰角（度，double，钳制到 ±{@value #MAX_PITCH_DEG}）。
     * <p>
     * 约定：节点处轨道切线的<b>竖向坡角</b>，沿节点方向前进时<b>正 = 上坡</b>。
     */
    public static final String KEY_PITCH_DEG = "pitchDeg";
    /**
     * 节点翻滚角（度，double，钳制到 ±{@value #MAX_ROLL_DEG}）。
     * <p>
     * 约定：绕节点前进轴的旋转，<b>正 = 前进方向右手侧抬高</b>（外轨超高 / 翻滚约定，
     * 与 {@code RailPoseExtra.roll1Degrees/roll2Degrees} 及几何内核的 {@code |sin(roll)|}
     * 中心线抬升一致）。
     */
    public static final String KEY_ROLL_DEG = "rollDeg";
    /**
     * 外轨超高开关（bool，默认 true）。
     * <p>
     * <b>只门控滚转</b>对轨道几何的贡献：关闭时翻滚角照常保存、节点模型照常倾斜，
     * 但写进 {@code RailPoseExtra.roll1Degrees/roll2Degrees} 的值强制为 0，
     * 于是几何内核的中心线抬升 {@code 半轨距·|sin(roll)|} 消失（{@code hasRoll() == false}）。
     * <p>
     * 纵坡（pitch）是独立功能，<b>不受本开关影响</b>：俯仰角始终写入
     * {@code RailPoseExtra.pitch1Degrees/pitch2Degrees} 并驱动三次 Hermite 竖向剖面。
     */
    public static final String KEY_SUPERELEVATION = "superelevation";
    /**
     * 半轨距（米，double，默认 {@link RailPoseExtra#DEFAULT_HALF_GAUGE}，钳制到
     * [{@value #MIN_HALF_GAUGE}, {@value #MAX_HALF_GAUGE}]）。
     * <p>
     * 外轨超高的几何量：滚转时中心线抬高 {@code 半轨距·|sin(翻滚角)|}，使内轨保持标高（ANTE 语义）。
     * 默认 0.7175 = 1435 mm 标准轨距的一半。整条轨道只取一个值，由贡献端点提供
     * （见 {@code NodeConnector.readRailPose}）。
     */
    public static final String KEY_ROLL_OFFSET_M = "rollOffsetM";

    // ==================== 硬边界常量（本类是唯一权威来源） ====================
    //
    // 这些常量<b>与 MTR4 版 {@code BlockEntityMultiDirectionNode} 逐字相同</b>：两个工程共用
    // 同一份 NBT 键名与同一套钳制区间，因此存档 / 网络契约不会漂移。改动其中任何一个都必须
    // 同步改另一侧，并在交付报告里写明。

    /**
     * 锚点平移硬上限（格）：几何上把轨道的连接点从方块中心挪出去。
     * <p>
     * 平移只改变「锚点 = 方块坐标 + 偏移」这一项，几何内核对锚点大小没有任何约束，
     * 所以把锚点挪得比一格远只会让轨道端点落到相邻方块范围，不会产生退化轨道。
     * 但偏移若再大就会让端点跑到别的节点/方块语义之外，既无法解释也无法调试，
     * 因此「相邻一格 + 自身一格」= ±2 格是可解释的最远距离。
     */
    public static final double MAX_OFFSET = 2.0D;

    /**
     * 俯仰角（纵坡）硬上限（度）。
     * <p>
     * 几何内核把俯仰角直接当作端点切线：{@code slope = tan(pitch)}，三次 Hermite 竖向剖面的
     * 位移是 {@code slope · 轨道长度}。于是 ±90° 是<b>真正的奇点</b>（{@code tan} 发散），
     * 内核<b>不做</b>任何保护（{@code isValid()} 只看平面几何，不看斜率），必须在节点侧钳制。
     * <p>
     * 取 60°：{@code tan60° = √3 ≈ 1.732}，剖面仍然有限、单调、可渲染，且离 ±90° 奇点有 30° 余量。
     */
    public static final double MAX_PITCH_DEG = 60.0D;

    /**
     * 翻滚角（外轨超高）硬上限（度）。
     * <p>
     * 滚转只在几何里产生一项中心线抬升 {@code 半轨距 · |sin(roll)|}，{@code |sin|} 有界，
     * 所以几何上再大的滚转也安全；限制纯粹来自视觉与语义：超过 45° 后截面已经翻掉一半，
     * 再大则 {@code |sin|} 反而开始变小（「更倾斜反而抬得更少」），语义会自相矛盾。
     */
    public static final double MAX_ROLL_DEG = 45.0D;

    /**
     * 半轨距下限（米）。
     * <p>
     * 必须保持为正：{@code RailPoseExtra} 与几何内核都把 {@code halfGauge <= 0} 当作
     * 「未设置」而回退到默认值，所以下限不能取 0。
     */
    public static final double MIN_HALF_GAUGE = 0.25D;

    /**
     * 半轨距上限（米）。抬升仍被 {@code |sin|} 与 45° 滚转上限共同夹住
     * （最大 {@code 2.0 · sin45° ≈ 1.41 m}），因此不会把截面尺寸算飞。
     */
    public static final double MAX_HALF_GAUGE = 2.0D;

    /**
     * C2S 载荷版本（{@code ModNetwork.BE_SYNC}）。
     * <p>
     * <b>布局只追加不修改</b>：v1 = direction/directionBonded（MTR3 的历史布局），
     * v2 在其后追加 3 个 double（平移，MTR4 的 v2 段），
     * v3 再追加 2 个 double（俯仰角 + 翻滚角），v4 再追加 1 个 boolean + 1 个 double
     * （外轨超高开关 + 半轨距）。接收侧用「剩余可读字节数」逐段判断版本，因此旧客户端
     * （只写前几段）与新客户端可以互通，不会出现读串位。
     * <p>
     * 与 MTR4 的唯一差异：MTR4 的 v1 段是 {@code direction/connected/directionBonded}（3 个字段、
     * 10 字节），MTR3 的 v1 段是 {@code direction/directionBonded}（2 个字段、9 字节，历史布局，
     * <b>不能改</b>否则与已发布的客户端不兼容）。因此同样停在 v4 时，MTR3 的负载比 MTR4 少 1 字节。
     * 两个工程的字段<b>个数、顺序、类型</b>完全一致，只有那一个历史遗留的 boolean 差异。
     * <p>
     * 字节数（不含 ModNetwork 的 BlockPos）：v1 = 9、v2 = 9 + 24 = 33、v3 = 33 + 16 = 49、
     * v4 = 49 + 1 + 8 = 58。
     */
    private static final int C2S_PAYLOAD_VERSION = 4;

    // ==================== 运行时状态 ====================
    /** 当前方向（度）。 */
    private double direction;
    /** 方向是否已绑定。 */
    private boolean directionBonded;
    /** 连接状态缓存；真正的数据源是 blockstate IS_CONNECTED，此处仅用于 NBT 与渲染读取。 */
    private boolean connected;
    /** 节点锚点平移（格）。导轨中心线、车辆路径与节点模型都跟随该偏移。 */
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    /**
     * 俯仰角（度）：节点处轨道切线的竖向坡角，正 = 沿方向前进时上坡。
     * <p>
     * <b>P5-2</b>：本值写进 {@code RailPoseExtra.pitch1Degrees/pitch2Degrees}
     * （先经 {@code NodeConnector.pitchFrameAngle} 投影到内核参数系），由几何内核的三次 Hermite
     * 竖向剖面（端点高度 + 端点切线 {@code tan(俯仰角)}）消费，从而真正改变轨道几何。
     * 轨道截面与车体的视觉倾斜仍属后续子阶段。
     */
    private double pitchDeg;
    /**
     * 翻滚角（度）：绕前进轴旋转，正 = 前进方向右手侧抬高。
     * <p>
     * <b>P5-2</b>：在 {@link #superelevation} 开关为开时写进
     * {@code RailPoseExtra.roll1Degrees/roll2Degrees}（先经节点帧 → 轨道帧换算），
     * 由几何内核的中心线抬升 {@code 半轨距·|sin(roll)|} 消费。
     */
    private double rollDeg;
    /**
     * 外轨超高开关：只门控滚转对轨道几何的贡献（纵坡不受影响）。
     * <p>
     * 默认 true（老存档没有该键 → 取默认值 true）。关闭后翻滚角仍被保存，
     * 但轨道姿态里的 roll 端点值写 0，几何回到无超高的水平截面。
     */
    private boolean superelevation = true;
    /**
     * 半轨距（米）：外轨超高的几何量，滚转时中心线抬高 {@code 半轨距·|sin(roll)|}（ANTE 语义）。
     * <p>
     * 默认 {@link RailPoseExtra#DEFAULT_HALF_GAUGE}（= 0.7175，1435 mm 标准轨距的一半），
     * 存入 NBT 前钳制到 [{@link #MIN_HALF_GAUGE}, {@link #MAX_HALF_GAUGE}]。
     */
    private double rollOffsetM = RailPoseExtra.DEFAULT_HALF_GAUGE;

    private DynamicModelHolder modelHolder;
    private DynamicModelHolder connectedModelHolder;

    // ==================== 异步加载（照 BlockEntityRotatingRail） ====================
    private static final ExecutorService LOADING_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fangsu-node-loading-async");
        t.setDaemon(true);
        return t;
    });

    private CompletableFuture<Void> loadingFuture;

    public BlockEntityMultiDirectionNode(BlockPos pos, BlockState state) {
        super(ModBlocks.BLOCK_ENTITY_MULTI_DIRECTION_NODE.get(), pos, state);
    }

    // ==================== 供连接器 / 角度界面读取的公开接口 ====================

    /** 当前方向（度）。 */
    public double getDirectionDegrees() {
        return direction;
    }

    /**
     * 节点锚点平移 X（格）。几何上表示「轨道连接点相对方块中心的水平偏移」。
     * <p>
     * 本阶段（P5-2）没有平移编辑界面，但 {@code NodeConnector.readRailPose} 与
     * {@code readNodeOffset} 已经读取它；这里补一个与 MTR4 版同名的访问器，
     * 使 {@code NodeConnector} 的代码在两个工程里逐字对应。
     */
    public double getOffsetX() {
        return offsetX;
    }

    /** 节点锚点平移 Y（格）。P5-2 起它会进入几何内核的锚点高度（= 方块坐标 + 本偏移）。 */
    public double getOffsetY() {
        return offsetY;
    }

    /** 节点锚点平移 Z（格）。 */
    public double getOffsetZ() {
        return offsetZ;
    }

    /**
     * 把平移分量钳制到 ±{@link #MAX_OFFSET}，并消除 NaN / 无穷。
     * <p>
     * 相位 5-2 还没有平移编辑界面，但 NBT 反序列化与 {@code NODE_REFRESH_RAIL} 载荷都会经过它，
     * 因此这里先按 MTR4 版同名同语义落地（{@code NaN → 0}、其余钳制到 ±2 格）。
     */
    public static double clampOffset(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        if (value > MAX_OFFSET) {
            return MAX_OFFSET;
        }
        if (value < -MAX_OFFSET) {
            return -MAX_OFFSET;
        }
        return value;
    }

    /**
     * 一次性写入节点锚点平移（自动钳制），只改数据并同步，不发轨道重建包。
     * <p>
     * 与 MTR4 版同名同语义；相位 5-2 无编辑界面调用它，但保留接口以便平移与角度共用同一条写入路径。
     * 平移会改变锚点位置 → 轨道几何随之改变，因此调用方写完同样应触发一次
     * {@link #refreshConnectedRailsIfNeeded()}。
     */
    public void setNodeOffset(double x, double y, double z) {
        final double newX = clampOffset(x);
        final double newY = clampOffset(y);
        final double newZ = clampOffset(z);
        if (newX == offsetX && newY == offsetY && newZ == offsetZ) {
            return;
        }
        this.offsetX = newX;
        this.offsetY = newY;
        this.offsetZ = newZ;
        markChangedAndSync();
    }

    /** 方向是否已绑定。 */
    public boolean isDirectionBonded() {
        return directionBonded;
    }

    /**
     * 是否已连接轨道。
     * <p>
     * 以 blockstate {@link BlockNode#IS_CONNECTED} 为准：MTR 的连接器建轨、轨道删除器、
     * 拆除节点等逻辑都直接改写该属性，BE 侧的 {@code connected} 只是派生缓存。
     */
    public boolean isConnected() {
        final BlockState state = getBlockState();
        if (state.getBlock() instanceof BlockNode && state.hasProperty(BlockNode.IS_CONNECTED)) {
            return state.getValue(BlockNode.IS_CONNECTED);
        }
        return connected;
    }

    /** 已绑定时返回任意角度 {@link RailAngle}；未绑定返回 null（连接器据此判定“自由端”）。 */
    public RailAngle getRailAngle() {
        return directionBonded ? NodeConnector.angleFor(direction) : null;
    }

    /** 绑定角度（度）。服务端直接刷新方块数据，客户端走 C2S 同步。 */
    public void bind(double degrees) {
        this.direction = RailAngleExtra.normalizeNodeDegrees(degrees);
        this.directionBonded = true;
        markChangedAndSync();
    }

    /** 与 MTR4 版同名的便捷入口。 */
    public void setDirectionAndBind(double degrees) {
        bind(degrees);
    }

    /** 与 MTR4 版同名的便捷入口（连接器建轨成功后写入实际使用的角度）。 */
    public void setDirectionBonded(double degrees) {
        bind(degrees);
    }

    /**
     * <b>只写方向值，不绑定</b>（P5-5 的「旋转绑定：否」分支）。
     * <p>
     * 与 {@link #bind(double)} 的唯一区别是 {@code directionBonded} 保持不变（仍为 false）：
     * 未绑定的万向节点方向只是「一个待用的参考值」，模型继续旋转、相连轨道按自由端求解
     * （{@link #getRailAngle()} 返回 {@code null}，连接器据此判定自由端）。
     * <p>
     * 这是 MTR4 版 {@code setDirectionUnbound} 的对应物：MTR4 的新面板允许「拖一下方向就把节点
     * 绑死」被修掉，本方法就是那个修复的写入端。与 MTR4 一样，绑定开关在节点**已连接**时被锁定为
     * 「是」（服务端重建轨道依赖绑定方向），因此本方法只在未连接时被走到。
     * <p>
     * 载荷形状不变：方向与绑定标志本来就是 v1 段的两个字段，写值后由
     * {@link #markChangedAndSync()} 发一次全量 BE_SYNC。
     */
    public void setDirectionUnbound(double degrees) {
        final double normalized = RailAngleExtra.normalizeNodeDegrees(degrees);
        if (normalized == direction) {
            return;
        }
        this.direction = normalized;
        markChangedAndSync();
    }

    /**
     * <b>只改方向绑定标志</b>（P5-5 的「绑定开关切到否」分支），保留当前方向值。
     * <p>
     * 与 {@link #unbind()} 的区别：{@code unbind()} 会把方向清成 0（那是「解绑并复位」），
     * 而本方法保留方向值 —— 与 MTR4 版同名方法一致，也与界面语义一致
     * （用户在开关上点一下不该把刚设好的方向丢掉）。
     */
    public void setRotationBonded(boolean bonded) {
        if (this.directionBonded == bonded) {
            return;
        }
        this.directionBonded = bonded;
        markChangedAndSync();
    }

    /**
     * 两端均未绑定时，把两个节点绑定到彼此连线的方向（直线轨道）。
     * <p>
     * 照 ANTE 蓝本：本端先计算到对方的方位角并绑定，再触发对方对称绑定；
     * 对方回过来调用本端时本端已绑定，直接返回，不会递归。
     */
    public void bind(BlockEntityMultiDirectionNode other) {
        if (directionBonded) {
            return;
        }
        final BlockPos self = getBlockPos();
        final BlockPos target = other.getBlockPos();
        bind(Math.toDegrees(Math.atan2(target.getZ() - self.getZ(), target.getX() - self.getX())));
        other.bind(this);
    }

    /** 解除绑定（已连接轨道的节点不允许解除，避免轨道几何失去约束）。 */
    public void unbind() {
        if (isConnected()) {
            return;
        }
        this.directionBonded = false;
        this.direction = 0D;
        markChangedAndSync();
    }

    // ==================== 俯仰 / 翻滚 / 外轨超高（P5-2） ====================
    // 与 MTR4 版逐字对应：字段名、NBT 键名、钳制常量、协议段顺序全部相同，只有「模型是否也倾斜」
    // 这一步在 MTR3 上尚未实现（见本文件末尾的说明）。约定：
    //   pitchDeg  节点处轨道切线的竖向坡角，正 = 沿节点方向前进时上坡（爬升）；
    //   rollDeg   绕节点前进轴的旋转，正 = 前进方向右手侧抬高（外轨超高 / 翻滚约定，
    //             与 RailPoseExtra.roll*Degrees 及内核的 半轨距·|sin(roll)| 抬升一致）。

    /**
     * 节点俯仰角（度，纵坡）。
     * <p>
     * 该符号被 {@code NodeConnector.readRailPose} 经 {@code pitchFrameAngle} 投影到内核参数系后
     * 写进 {@code RailPoseExtra.pitch1Degrees/pitch2Degrees}，喂给几何内核的三次 Hermite 竖向剖面
     * （端点切线 = {@code tan(俯仰角)}）。
     */
    public double getPitchDegrees() {
        return pitchDeg;
    }

    /**
     * 节点翻滚角（度，外轨超高）。
     * <p>
     * 该符号被 {@code NodeConnector.readRailPose} 经节点帧 → 轨道帧换算（±1）后写进
     * {@code RailPoseExtra.roll1Degrees/roll2Degrees}（外轨超高开关开启时），
     * 由几何内核按 {@code 半轨距·|sin(roll)|} 抬升中心线。
     */
    public double getRollDegrees() {
        return rollDeg;
    }

    /** 是否设置了非零俯仰 / 翻滚。 */
    public boolean hasTilt() {
        return pitchDeg != 0.0D || rollDeg != 0.0D;
    }

    /**
     * 一次性写入俯仰角 + 翻滚角（自动钳制），只改数据并 {@code setChanged()}，<b>不</b>发包。
     * <p>
     * 与 {@code bind(double)} 保持同一风格：钳制后与旧值完全相同则直接返回，
     * 避免拖动滑块时每帧都触发一次方块实体更新。需要同步到服务端时调用
     * {@link #setAnglesAndSync(double, double)}，或写完补一次 {@link #sendUpdateC2S()}。
     * <p>
     * <b>注意</b>：写完之后轨道几何会变（姿态进入内核），调用方<b>必须</b>再触发一次刷新
     * （{@link #refreshConnectedRailsIfNeeded()}），否则客户端看到的还是旧几何。
     */
    public void setNodeAngles(double pitch, double roll) {
        final double newPitch = clampPitch(pitch);
        final double newRoll = clampRoll(roll);
        if (newPitch == pitchDeg && newRoll == rollDeg) {
            return;
        }
        this.pitchDeg = newPitch;
        this.rollDeg = newRoll;
        markChangedAndSync();
    }

    /**
     * 俯仰 / 翻滚写入入口：写数据 + 立即 BE_SYNC（<b>不发</b>轨道重建包）。
     * <p>
     * 调用方在写完本方法后<b>应当</b>再调一次 {@link #refreshConnectedRailsIfNeeded()}：
     * 本方法只把姿态同步给服务端方块实体，轨道几何的重建由刷新请求负责。
     * （服务端 {@link #readC2S} 也会在收到 BE_SYNC 后按新姿态重建一次，
     * 两条路径幂等，重复重建只会得到相同几何。）
     * <p>
     * {@link #writeC2S} 的载荷是<b>全量</b>（方向 + 绑定 + 平移 + 俯仰 / 翻滚 + 超高开关 + 半轨距），
     * 所以调用方应先把开关 / 半轨距等其它字段写好，最后调本方法，一次包就带齐全部改动。
     */
    public void setAnglesAndSync(double pitch, double roll) {
        // markChangedAndSync 在客户端内部就会发 BE_SYNC（与服务端方向对称），
        // 因此这里不再单独 sendUpdateC2S，避免一次改动发两个包。
        setNodeAngles(pitch, roll);
    }

    /** 把俯仰角钳制到 ±{@link #MAX_PITCH_DEG}，并消除 NaN / 无穷（NaN / Inf → 0）。 */
    public static double clampPitch(double value) {
        return clampAngle(value, MAX_PITCH_DEG);
    }

    /** 把翻滚角钳制到 ±{@link #MAX_ROLL_DEG}，并消除 NaN / 无穷（NaN / Inf → 0）。 */
    public static double clampRoll(double value) {
        return clampAngle(value, MAX_ROLL_DEG);
    }

    /**
     * 通用角度钳制：NaN / 无穷归零，其余钳制到 ±{@code limit}。
     * <p>
     * 注意 {@code Double.isInfinite} 必须在比较之前判掉：{@code +Inf > limit} 成立会返回 limit，
     * 虽然也安全，但语义上无穷更应该视作「无效输入」而清零。
     */
    private static double clampAngle(double value, double limit) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        if (value > limit) {
            return limit;
        }
        if (value < -limit) {
            return -limit;
        }
        return value;
    }

    /**
     * 外轨超高开关是否开启。
     * <p>
     * <b>只门控滚转</b>：关闭时 {@code NodeConnector.readRailPose} 把该端点的
     * {@code roll*Degrees} 写成 0，几何内核的中心线抬升 {@code 半轨距·|sin(roll)|} 随之消失。
     * 纵坡（pitch）不受影响，照常驱动 Hermite 剖面。
     */
    public boolean isSuperelevationEnabled() {
        return superelevation;
    }

    /** 半轨距（米）：外轨超高抬升中心线时用的 {@code 半轨距·|sin(roll)|} 系数。 */
    public double getRollOffsetM() {
        return rollOffsetM;
    }

    /**
     * 写入外轨超高开关，只改数据并 {@code setChanged()}，<b>不</b>发包也不重建。
     * <p>
     * 开关会改变轨道几何（门控滚转贡献），所以调用方写完必须触发一次重建
     * （{@link #refreshConnectedRailsIfNeeded()}）。
     */
    public void setSuperelevation(boolean enabled) {
        if (this.superelevation == enabled) {
            return;
        }
        this.superelevation = enabled;
        markChangedAndSync();
    }

    /**
     * 写入半轨距（米，自动钳制到 [{@value #MIN_HALF_GAUGE}, {@value #MAX_HALF_GAUGE}]），
     * 只改数据并 {@code setChanged()}，<b>不</b>发包也不重建。
     */
    public void setRollOffsetM(double metres) {
        final double clamped = clampHalfGauge(metres);
        if (clamped == rollOffsetM) {
            return;
        }
        this.rollOffsetM = clamped;
        markChangedAndSync();
    }

    /**
     * 半轨距钳制：NaN / 无穷归默认值，其余钳制到 [{@value #MIN_HALF_GAUGE}, {@value #MAX_HALF_GAUGE}]。
     * <p>
     * 与 {@link #clampAngle} 不同，非法输入这里退回 {@link RailPoseExtra#DEFAULT_HALF_GAUGE} 而不是 0：
     * 半轨距是「轨距的一半」这种物理尺寸，0 会让 {@code RailPoseExtra} 的构造器回退到默认值、
     * 与节点存储值不一致，语义上更混乱。
     */
    public static double clampHalfGauge(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return RailPoseExtra.DEFAULT_HALF_GAUGE;
        }
        if (value < MIN_HALF_GAUGE) {
            return MIN_HALF_GAUGE;
        }
        if (value > MAX_HALF_GAUGE) {
            return MAX_HALF_GAUGE;
        }
        return value;
    }

    /** 设置连接状态（写 blockstate，保持与 MTR 语义一致）。 */
    public void setConnected(boolean value) {
        final Level level = getLevel();
        if (level == null) {
            return;
        }
        final BlockState state = getBlockState();
        if (state.getBlock() instanceof BlockNode && state.hasProperty(BlockNode.IS_CONNECTED)
                && state.getValue(BlockNode.IS_CONNECTED) != value) {
            level.setBlockAndUpdate(getBlockPos(), state.setValue(BlockNode.IS_CONNECTED, value));
        }
        syncConnectedCache();
    }

    /** 由 blockstate 刷新 BE 的连接状态缓存并落盘（MTR 连接器改 blockstate 后调用）。 */
    public void syncConnectedCache() {
        final boolean nowConnected = isConnected();
        if (this.connected != nowConnected) {
            this.connected = nowConnected;
            this.setChanged();
        }
    }

    private void markChangedAndSync() {
        this.setChanged();
        final Level level = getLevel();
        if (level == null) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            // 服务端：通知客户端方块数据变化（触发 getUpdatePacket）
            serverLevel.getChunkSource().blockChanged(getBlockPos());
        } else {
            sendUpdateC2S();
        }
    }

    // ==================== 网络同步 ====================

    /** 客户端 → 服务端：发送当前方向 / 绑定状态（复用 ModNetwork.BE_SYNC）。 */
    public void sendUpdateC2S() {
        if (level != null && level.isClientSide) {
            final BlockPos pos = getBlockPos();
            if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeBlockPos(pos);
                writeC2S(buf);
                NetworkManager.sendToServer(ModNetwork.BE_SYNC, buf);
            }
        }
        this.setChanged();
    }

    @Override
    public void writeC2S(FriendlyByteBuf buf) {
        // ---- v1 段（MTR3 历史布局，冻结，不可增删改）----
        buf.writeDouble(direction);
        buf.writeBoolean(directionBonded);
        // ---- v2 段（MTR4 的 v2 段，追加字段）----
        // 追加而非插入：旧发送方只写 v1，接收方按剩余字节数判断，双方都不读串位。
        buf.writeDouble(offsetX);
        buf.writeDouble(offsetY);
        buf.writeDouble(offsetZ);
        // ---- v3 段（俯仰 / 翻滚）----
        // 同样只在尾部追加：v1 / v2 发送方读到这里剩余 0 字节，两段检查（>= 24、>= 16）依次失败，
        // 两个角度保持原值（不静默清零）。
        buf.writeDouble(pitchDeg);
        buf.writeDouble(rollDeg);
        // ---- v4 段（外轨超高开关 + 半轨距）----
        // 同样只在尾部追加，接收侧逐段检查（>= 24、>= 16、>= 9）依次失败，
        // 新增的两个字段保持原值（不静默清零）。
        buf.writeBoolean(superelevation);
        buf.writeDouble(rollOffsetM);
        // 版本号本身不写进流（写了会让旧接收方把版本字节当成 direction 的首字节）；
        // C2S_PAYLOAD_VERSION 只在代码内标记当前布局，真实判据是接收侧的字节数检查。
    }

    @Override
    public void readC2S(FriendlyByteBuf buf) {
        final double newDirection = buf.readDouble();
        // C2S 数据不可信：非法数值直接丢弃（否则会写出 NaN 几何的轨道）
        if (!Double.isFinite(newDirection)) {
            Main.LOGGER.warn("[MultiDirectionNode] 收到非法角度 {} @ {}", newDirection, getBlockPos());
            return;
        }
        this.direction = RailAngleExtra.normalizeNodeDegrees(newDirection);
        this.directionBonded = buf.readBoolean();
        // v2：3 个 double = 24 字节。v1 发送方这里剩余 0 字节，直接跳过，偏移保持原值（不静默清零）。
        if (buf.readableBytes() >= 24) {
            this.offsetX = clampOffset(buf.readDouble());
            this.offsetY = clampOffset(buf.readDouble());
            this.offsetZ = clampOffset(buf.readDouble());
        }
        // v3：再 2 个 double = 16 字节。只有 v3 及以上发送方才读写俯仰 / 翻滚。
        // 判据同样是剩余字节数，不依赖包内版本号。
        if (buf.readableBytes() >= 16) {
            this.pitchDeg = clampPitch(buf.readDouble());
            this.rollDeg = clampRoll(buf.readDouble());
        }
        // v4：再 1 个 boolean + 1 个 double = 9 字节。只有 v4 发送方才读写外轨超高开关与半轨距。
        if (buf.readableBytes() >= 9) {
            this.superelevation = buf.readBoolean();
            this.rollOffsetM = clampHalfGauge(buf.readDouble());
        }
        syncConnectedCache();
        this.setChanged();
        // 服务端收到改绑后立刻按新角度 + 新姿态重建已连接轨道
        NodeConnector.rebuildRailsAtNode(level, getBlockPos());
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ==================== 轨道刷新请求（NODE_REFRESH_RAIL） ====================

    /**
     * 客户端：把「节点姿态快照 + 每条相连轨道的属性」打包发给服务端，让它原地重建这些轨道的几何。
     * <p>
     * <b>为什么需要它</b>：姿态（俯仰 / 翻滚 / 半轨距）改变了轨道几何，而服务端只有改了方块实体的
     * 那一刻才会重算。{@code BE_SYNC} 虽然也会触发一次重建，但它<b>不带</b>逐轨道属性
     * （限速 / 类型 / 站台标记 / 样式），且与其它方块同步共用一条通道；本通道专门承载
     * 「这次刷新要为每条轨道保留什么属性、以及作者是否授权过逐轨道超高」，因此
     * 相位 5-5 的逐轨道编辑界面接进来之后不会把作者的授权值冲掉。
     * <p>
     * <b>节点姿态随包送达</b>：服务端 {@code readC2S} 会把同一份姿态写进自己的方块实体，
     * 两个包（BE_SYNC 与本包）的到达顺序因此不影响结果 —— 重建用的姿态始终来自本包。
     * 若某个旧的 / 第三方的客户端只发 BE_SYNC，服务端仍会按方块实体里的当前值重建，
     * 也就是退化成「节点派生姿态」，不会崩也不会用垃圾值。
     * <p>
     * <b>客户端 MTR 数据可能尚未同步</b>：{@code ClientData.RAILS} 是网络线程写入的。
     * 数据不到位时这里<b>直接放弃</b>（打一条 debug），不做跨帧重试：
     * 服务端的 {@code readC2S} 已经保证「方块实体值是权威」，真正决定几何的是服务端那次重建，
     * 因此丢一次刷新包只会让轨道晚一个包更新，不会停留在错误几何上。
     */
    public void refreshConnectedRailsIfNeeded() {
        if (level == null || !level.isClientSide) {
            return;
        }
        final List<BlockPos> others = NodeConnector.findConnectedEndpoints(worldPosition);
        if (others.isEmpty()) {
            // 客户端 MTR 数据未同步（找不到本节点端点）→ 服务端的 BE_SYNC 路径已经重建过一次，
            // 这里只记录，不重试（理由见方法注释）。
            Main.LOGGER.debug("[MultiDirectionNode] 刷新请求跳过：客户端轨道数据里没有 {} 的端点", worldPosition);
            return;
        }
        final Map<BlockPos, Rail> connections = findConnections(worldPosition);
        final List<NodeRefreshRailPayload.RailEntry> entries = new ArrayList<>(others.size());
        for (final BlockPos other : others) {
            final Rail rail = connections == null ? null : connections.get(other);
            if (rail == null) {
                continue;
            }
            // 逐轨道属性：服务端据此原地重建出「同类型、同单向性」的轨道。
            // MTR3 的 Rail 没有形状 / 样式 / 分端限速（这些是 MTR4 的模型），
            // 因此这里只带 railType 的名字与单向标记，见 NodeRefreshRailPayload.RailEntry。
            //
            // 逐轨道超高（P5-5 起真的会出现在生产路径上）：服务端重建用的是「新轨道 + 节点派生姿态」，
            // 若不把作者授权的三点剖面/半轨距一起带过去，RailPoseExtraHolder.apply 的整份写入
            // 会把授权值抹掉 —— 表现就是「改一下节点翻滚角，逐轨道超高就没了」。
            // 轨道表 rails[nodePos][other] 的 position1 就是本节点，所以参考帧不反转（reversed=false）。
            entries.add(new NodeRefreshRailPayload.RailEntry(
                    other, rail.railType.name(), rail.railType == mtr.data.RailType.NONE,
                    NodeConnector.carryRailTilt(RailPoseExtraHolder.peek(rail), false)));
        }
        if (entries.isEmpty()) {
            Main.LOGGER.debug("[MultiDirectionNode] 刷新请求跳过：{} 的相连轨道都不在客户端数据里", worldPosition);
            return;
        }
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        NodeRefreshRailPayload.write(buf, new NodeRefreshRailPayload.Payload(
                worldPosition, direction, offsetX, offsetY, offsetZ, pitchDeg, rollDeg,
                superelevation, rollOffsetM, directionBonded, entries));
        NetworkManager.sendToServer(ModNetwork.NODE_REFRESH_RAIL, buf);
    }

    /**
     * 服务端：把 {@code NODE_REFRESH_RAIL} 载荷里的<b>节点姿态快照</b>落库。
     * <p>
     * <b>为什么让刷新包也带姿态</b>：姿态与「重建请求」必须同源同时刻。若分两个包（姿态走 BE_SYNC、
     * 请求走本通道），两条通道的到达/应用顺序会决定服务端读到的姿态，
     * 于是出现「改了翻滚，轨道却按上一个姿态重建」的竞态。现在刷新包自带姿态，
     * 服务端先落库、再重建，读到的必然是同一个快照。
     * <p>
     * 与 {@link #readC2S} 共用同一套钳制函数（{@code clampPitch/clampRoll/clampHalfGauge}），
     * 因此硬边界只有一处定义。
     * <p>
     * {@code direction} 的合法性检查与 {@link #readC2S} 一致：非有限值直接整包丢弃，
     * 避免用 NaN 角度重建出 NaN 几何的轨道。
     *
     * @param payload 已解码的刷新请求
     */
    public void applyRefreshedState(NodeRefreshRailPayload.Payload payload) {
        if (payload == null || !Double.isFinite(payload.direction())) {
            Main.LOGGER.warn("[MultiDirectionNode] 刷新请求携带非法方向 {} @ {}", payload == null ? "null" : payload.direction(), getBlockPos());
            return;
        }
        this.direction = RailAngleExtra.normalizeNodeDegrees(payload.direction());
        this.directionBonded = payload.directionBonded();
        this.offsetX = clampOffset(payload.offsetX());
        this.offsetY = clampOffset(payload.offsetY());
        this.offsetZ = clampOffset(payload.offsetZ());
        this.pitchDeg = clampPitch(payload.pitchDeg());
        this.rollDeg = clampRoll(payload.rollDeg());
        this.superelevation = payload.superelevation();
        this.rollOffsetM = clampHalfGauge(payload.rollOffsetM());
        this.setChanged();
        syncConnectedCache();
    }

    /**
     * 客户端：读取 {@code ClientData.RAILS} 里连接到本节点的轨道表（与
     * {@link NodeConnector#findConnectedEndpoints} 同一份数据，加锁读取并对并发修改兜底）。
     */
    private static Map<BlockPos, Rail> findConnections(BlockPos nodePos) {
        synchronized (mtr.client.ClientData.RAILS) {
            try {
                return mtr.client.ClientData.RAILS.get(nodePos);
            } catch (ConcurrentModificationException ignored) {
                return null;
            }
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (markedError) {
            return;
        }
        tag.putDouble(KEY_DIRECTION, direction);
        // connected 以 blockstate 为准，写入 NBT 只是为了与 MTR4 版数据形状一致
        tag.putBoolean(KEY_CONNECTED, isConnected());
        tag.putBoolean(KEY_DIRECTION_BONDED, directionBonded);
        // 平移分量总是写（与 MTR4 版一致）：客户端区块加载时要靠它渲染偏移后的模型，
        // 全零写入的开销可忽略，换来的是「读侧不需要判空」。
        // P5-2 还没有平移编辑界面，两个分量除读之外恒为 0，但读写路径先按 MTR4 的形状落地。
        tag.putDouble(KEY_OFFSET_X, offsetX);
        tag.putDouble(KEY_OFFSET_Y, offsetY);
        tag.putDouble(KEY_OFFSET_Z, offsetZ);
        // 同理总是写俯仰 / 翻滚 / 外轨超高开关 / 半轨距：轨道几何与界面都要靠它们。
        tag.putDouble(KEY_PITCH_DEG, pitchDeg);
        tag.putDouble(KEY_ROLL_DEG, rollDeg);
        tag.putBoolean(KEY_SUPERELEVATION, superelevation);
        tag.putDouble(KEY_ROLL_OFFSET_M, rollOffsetM);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        markedError = false;
        this.direction = tag.getDouble(KEY_DIRECTION);
        this.directionBonded = tag.getBoolean(KEY_DIRECTION_BONDED);
        this.connected = tag.getBoolean(KEY_CONNECTED);
        // 老存档没有这三个键 → getDouble 返回 0（原版行为）
        this.offsetX = clampOffset(tag.getDouble(KEY_OFFSET_X));
        this.offsetY = clampOffset(tag.getDouble(KEY_OFFSET_Y));
        this.offsetZ = clampOffset(tag.getDouble(KEY_OFFSET_Z));
        // 老存档（P5-2 之前）没有这两个键 → getDouble 返回 0，再经钳制仍是 0
        this.pitchDeg = clampPitch(tag.getDouble(KEY_PITCH_DEG));
        this.rollDeg = clampRoll(tag.getDouble(KEY_ROLL_DEG));
        // 外轨超高开关<b>默认 true</b>，而 CompoundTag.getBoolean 对缺失键返回 false，
        // 直接用会把所有老存档静默改成「关闭超高」，所以先用 contains 判断再取值。
        this.superelevation = !tag.contains(KEY_SUPERELEVATION) || tag.getBoolean(KEY_SUPERELEVATION);
        // 半轨距缺失（P5-2 之前的存档）→ 退回标准轨距的一半，而不是 0
        // （0 会被 RailPoseExtra 兜成默认值，但节点存储值会与轨道姿态不一致）
        this.rollOffsetM = tag.contains(KEY_ROLL_OFFSET_M)
                ? clampHalfGauge(tag.getDouble(KEY_ROLL_OFFSET_M))
                : RailPoseExtra.DEFAULT_HALF_GAUGE;
        // blockstate 是连接状态的权威来源（服务端 / 客户端都会同步到）
        syncConnectedCache();
        triggerAsyncLoading();
        // 服务端：世界重载后按任意角度重建连接到本节点的轨道（MTR 反序列化会把角度量化到 22.5°），
        // 同时把节点姿态（俯仰 / 翻滚 / 半轨距）写回轨道几何。
        if (level instanceof ServerLevel) {
            NodeConnector.rebuildRailsAtNode(level, getBlockPos());
        }
    }

    // ==================== 生命周期 ====================

    private void triggerAsyncLoading() {
        cancelPendingAsyncLoading();
        loadingFuture = CompletableFuture.runAsync(() -> {
            try {
                this.whenLoading();
            } catch (Exception e) {
                Main.LOGGER.error("加载万向节点方块实体失败 {} @ {}", getClass().getSimpleName(), getBlockPos(), e);
            }
        }, LOADING_EXECUTOR);
    }

    private void cancelPendingAsyncLoading() {
        if (loadingFuture != null && !loadingFuture.isDone()) {
            loadingFuture.cancel(true);
        }
        loadingFuture = null;
    }

    private boolean isAsyncLoadingDone() {
        return loadingFuture == null || loadingFuture.isDone();
    }

    /** 渲染就绪条件：模型异步加载已完成（避免渲染线程读到未就绪的模型）。 */
    @Override
    protected boolean isRenderReady() {
        return isAsyncLoadingDone();
    }

    public void whenLoading() {
        // 仅客户端需要模型：服务端加载 OBJ（文件 IO + 解析）会阻塞主线程
        if (level == null || !level.isClientSide) {
            return;
        }
        ensureModelReady();
    }

    /** 确保未连接 / 已连接两套模型已就绪（照 BlockEntityRotatingRail 的 uploadLater 模式）。 */
    private void ensureModelReady() {
        if (modelHolder == null || modelHolder.getUploadedModel() == null) {
            try {
                final RawModel rawModel = ResourceUtil.loadModel(new ResourceLocation(DEFAULT_MODEL), false);
                if (rawModel != null) {
                    if (modelHolder == null) {
                        modelHolder = new DynamicModelHolder();
                    }
                    modelHolder.uploadLater(rawModel);
                }
            } catch (Exception e) {
                Main.LOGGER.warn("[MultiDirectionNode] 加载模型 {} 失败: {}", DEFAULT_MODEL, e.getMessage(), e);
            }
        }

        if (connectedModelHolder == null || connectedModelHolder.getUploadedModel() == null) {
            try {
                final RawModel rawModel = ResourceUtil.loadModel(new ResourceLocation(CONNECTED_MODEL), false);
                if (rawModel != null) {
                    if (connectedModelHolder == null) {
                        connectedModelHolder = new DynamicModelHolder();
                    }
                    connectedModelHolder.uploadLater(rawModel);
                }
            } catch (Exception e) {
                Main.LOGGER.warn("[MultiDirectionNode] 加载模型 {} 失败: {}", CONNECTED_MODEL, e.getMessage(), e);
            }
        }
    }

    /**
     * 渲染逻辑（运行在方块实体的异步渲染线程上，禁止触碰客户端专属类）。
     * <p>
     * 未连接：绘制 node.obj —— 未绑定时绕 Y 轴匀速旋转（2 秒一圈），已绑定时按方向固定；
     * 已连接：绘制 node_connected.obj（始终绘制）。
     * <p>
     * 与 MTR4 版的差异：MTR4 在“已连接”时默认隐藏模型、仅手持轨道道具时显示
     * （因为 MTR4 本体会在此时渲染节点）；而 MTR3 的节点是常显的方块模型，
     * 若照搬隐藏逻辑，已连接节点将彻底不可见，故这里始终绘制。
     */
    @Override
    public void whenRendering() {
        final ObjBlockScriptContext ctx = this.scriptContext;
        if (ctx == null || level == null) {
            return;
        }
        ensureModelReady();

        final boolean connectedNow = isConnected();
        final DynamicModelHolder holder = connectedNow ? connectedModelHolder : modelHolder;
        if (holder == null || holder.getUploadedModel() == null) {
            return;
        }

        final double rotation;
        if (connectedNow || directionBonded) {
            // 与 MTR 节点显示一致：方向为顺时针罗盘角，rotateY 取负（并补正模型自身朝向）
            rotation = -Math.toRadians(direction) + Math.PI / 2;
        } else {
            // 未绑定：绕 Y 轴匀速 360° 旋转
            rotation = (System.currentTimeMillis() % 2000L) / 2000.0 * (Math.PI * 2);
        }

        final Matrices mat = new Matrices();
        // 原点平移由 BaseBlockEntityRender 统一施加（candyPose.translate(0.5, 0, 0.5)），此处不能再平移
        mat.rotateY((float) rotation);
        ctx.drawModel(holder, mat);
    }

    public void whenDisposing() {
        if (modelHolder != null) {
            modelHolder.close();
            modelHolder = null;
        }
        if (connectedModelHolder != null) {
            connectedModelHolder.close();
            connectedModelHolder = null;
        }
    }

    @Override
    public void setRemoved() {
        if (!disposed) {
            whenDisposing();
            cancelPendingAsyncLoading();
        }
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level == null || !level.isClientSide) {
            return;
        }
        ensureModelReady();
    }

    // ==================== 交互 ====================

    @Override
    public InteractionResult useWithWrench(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            ClientHooks.openMultiDirectionNodeScreen(this);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 刷子与扳手同样打开角度界面（MTR3 无原版轨道形状编辑界面可复用）
        if (level.isClientSide) {
            ClientHooks.openMultiDirectionNodeScreen(this);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ==================== BaseObjBlockEntity 抽象方法 ====================

    @Override
    public String getMainModelKey() {
        return "multiDirectionNode";
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        // 已连接时形状极薄（与原版 MTR 节点一致），避免阻挡玩家视线追踪轨道
        if (isConnected()) {
            return Shapes.box(0.1, 0, 0.1, 0.9, 0.0625, 0.9);
        }
        return Shapes.block();
    }
}
