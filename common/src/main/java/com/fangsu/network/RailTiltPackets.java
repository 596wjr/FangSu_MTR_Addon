package com.fangsu.network;

import com.fangsu.Main;
import com.fangsu.mappings.rail.RailPoseExtra;
import com.fangsu.util.NodeConnector;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 逐轨道超高（外轨超高 / 逐轨道倾斜）C2S 通道。
 * <p>
 * 客户端把「玩家正在编辑的那条轨道」的倾斜控制点发给服务端，服务端<b>校验后</b>写进轨道姿态
 * （{@link RailPoseExtra}）并<b>显式重播</b>，使所有客户端都能看到。
 * 载荷布局与读写顺序见 {@link RailTiltPayload}（写侧与读侧在同一个类里，不对称写不出来）。
 * <p>
 * 为什么必须有这条独立通道，而不是复用 MTR 自己的轨道更新包：
 * <ul>
 *   <li>那是「整条轨道替换」，服务端不校验任何字段（MTR 的既有设计），把轨道几何 / 限速 / 类型的
 *       写权限直接交给客户端；本通道只允许改三个倾斜角 + 半轨距，服务端自己算出中间控制点位置，
 *       其余字段一概不动；</li>
 *   <li>需要服务端权威地做有限性、范围、距离与权限校验，并把非法请求记日志丢弃。
 *       （本工程上线前审计里「C2S 数据无服务端校验」是既有欠债，本通道<b>不</b>增加新的欠债。）</li>
 * </ul>
 * <b>轨道身份 = 两个端点，不是 id</b>：MTR3 没有 MTR4 的 {@code TwoPositionsBase.getHexId}，
 * 也没有 {@code Simulator} / {@code PacketUpdateData.sendDirectlyToServerRail}；它的轨道表就是
 * {@code RailwayData.rails[position1][position2]}。服务端拿收到的两个端点去查<b>自己</b>的表
 * （见 {@link NodeConnector#applyRailTilt}），因此「伪造 id 命中另一条轨道」在 MTR3 上不可达 ——
 * 客户端根本报不出 id。校验通过后由 {@link NodeConnector#applyRailTilt} 合并进<b>实时姿态</b>
 * 并用 MTR3 自己的 {@code PACKET_CREATE_RAIL} 显式重播。
 * <p>
 * <b>本通道不做版本探测</b>：客户端与服务端永远运行同一份 FangSu 构建（与
 * {@code ModNetwork.handleNodeRefreshRail} 同一约定）。
 */
public final class RailTiltPackets {

    /** C2S：编辑某条轨道的逐轨道超高 */
    public static final ResourceLocation RAIL_TILT_EDIT = new ResourceLocation("fangsu", "rail_tilt_edit");

    /** 半轨距下限（米）：再窄就不是「一条线」了，也避免抬升量小到看不见。 */
    public static final double MIN_HALF_GAUGE = 0.25D;
    /** 半轨距上限（米）：4 m 轨距，远超任何现实轨道。 */
    public static final double MAX_HALF_GAUGE = 2.0D;
    /** 倾斜角硬边界（度）：与服务端校验、界面滑块、{@code RailPoseExtra} 共用同一个常量。 */
    public static final double MAX_TILT_DEGREES = RailPoseExtra.MAX_RAIL_TILT_DEGREES;
    /** 玩家到轨道端点的最大允许距离（格）：建轨交互距离 5 格，这里留足余量以覆盖长轨道的远端。 */
    private static final double MAX_EDIT_DISTANCE = 32.0D;
    /** 服务端写权限等级：与 MTR 自带的方块编辑一致（1 = 普通玩家，2 = OP/作弊）。 */
    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    private RailTiltPackets() {
    }

    /** 服务端注册（由 {@link ModNetwork#init} 调用，注册位置紧挨 {@code NODE_REFRESH_RAIL}）。 */
    public static void registerServer() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, RAIL_TILT_EDIT, RailTiltPackets::handleRailTiltEdit);
    }

    private static void handleRailTiltEdit(FriendlyByteBuf buf, NetworkManager.PacketContext ctx) {
        // 载荷必须在主线程排队<b>之前</b>读完：排队回调可能在网络缓冲区释放之后才执行
        final RailTiltPayload.Payload payload = RailTiltPayload.read(buf);
        final BlockPos p1 = payload.position1();
        final BlockPos p2 = payload.position2();

        ctx.queue(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) {
                return;
            }
            //#if MC_VERSION >= 12000
            final Level level = player.level();
            //#else
            //$$ final Level level = player.level;
            //#endif
            if (level == null || p1 == null || p2 == null || p1.equals(p2)) {
                logIgnore("载荷不全或两端点相同", p1, p2);
                return;
            }
            // 写权限：没有权限的玩家不能改世界数据（C2S 校验，历史遗留的「无服务端校验」不再扩散）
            if (!player.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
                logIgnore("发送者没有写权限", p1, p2);
                return;
            }
            // 距离：轨道两端至少有一端在玩家附近，避免「隔半个世界改别人轨道」
            if (minDistanceToRail(player, p1, p2) > MAX_EDIT_DISTANCE) {
                logIgnore("发送者距离轨道过远", p1, p2);
                return;
            }

            // 倾斜角校验：setTilt 时三个角必须有限并落在 ±45 内；clearTilt（或 setTilt=false）时清空。
            // 用「非法就整条丢弃」而不是「夹取」，是因为客户端已经夹过一次，再收到越界值只可能是
            // 被改过的包；静默夹取会让攻击者以为请求生效。
            final double[] tiltDegrees;
            if (payload.setTilt() && !payload.clearTilt()) {
                if (!Double.isFinite(payload.startDegrees())
                        || !Double.isFinite(payload.middleDegrees())
                        || !Double.isFinite(payload.endDegrees())) {
                    logIgnore("倾斜角非有限值", p1, p2);
                    return;
                }
                if (Math.abs(payload.startDegrees()) > MAX_TILT_DEGREES
                        || Math.abs(payload.middleDegrees()) > MAX_TILT_DEGREES
                        || Math.abs(payload.endDegrees()) > MAX_TILT_DEGREES) {
                    logIgnore("倾斜角超出 ±" + MAX_TILT_DEGREES + " 度", p1, p2);
                    return;
                }
                tiltDegrees = new double[]{payload.startDegrees(), payload.middleDegrees(), payload.endDegrees()};
            } else {
                tiltDegrees = null;
            }
            // 半轨距：非有限值 = 「沿用当前值」；有限值必须落在 [0.25, 2.0]
            final float halfGauge = payload.halfGauge();
            if (Float.isFinite(halfGauge) && (halfGauge < MIN_HALF_GAUGE || halfGauge > MAX_HALF_GAUGE)) {
                logIgnore("半轨距超出 [" + MIN_HALF_GAUGE + ", " + MAX_HALF_GAUGE + "]", p1, p2);
                return;
            }
            final double requestedHalfGauge = Float.isFinite(halfGauge) ? halfGauge : Double.NaN;

            // 落库 + 显式重播：轨道表与姿态都归服务端自己的 RailwayData 所有，
            // 全部在服务端主线程内完成（与 MTR 的建轨 / 刷新路径一致）。
            if (!NodeConnector.applyRailTilt(level, p1, p2, tiltDegrees, requestedHalfGauge)) {
                logIgnore("该轨道不在服务端数据里（端点对查不到）", p1, p2);
                return;
            }
            Main.debug("[RailTilt] 已应用逐轨道超高 {} -> {}（{}）", p1, p2,
                    tiltDegrees == null
                            ? "清除"
                            : "起点=" + tiltDegrees[0] + " 中点=" + tiltDegrees[1] + " 终点=" + tiltDegrees[2]);
        });
    }

    /** 玩家到轨道任一端点的最小水平距离（格）。 */
    private static double minDistanceToRail(ServerPlayer player, BlockPos p1, BlockPos p2) {
        return Math.min(horizontalDistance(player, p1), horizontalDistance(player, p2));
    }

    private static double horizontalDistance(ServerPlayer player, BlockPos pos) {
        final double dx = pos.getX() + 0.5D - player.getX();
        final double dz = pos.getZ() + 0.5D - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 非法请求一律忽略并记 debug 日志（不是 warn：改包/作弊会刷屏，正常玩家看不到）。 */
    private static void logIgnore(String reason, BlockPos p1, BlockPos p2) {
        Main.debug("[RailTilt] 忽略轨道超高编辑请求 {} -> {}：{}", p1, p2, reason);
    }

    /** 客户端：把一条轨道的逐轨道超高写入请求发给服务端。 */
    public static void sendRailTiltC2S(BlockPos p1, BlockPos p2,
                                       double startDegrees, double middleDegrees, double endDegrees,
                                       double halfGauge) {
        writeAndSend(new RailTiltPayload.Payload(p1, p2, true, false,
                clampDegrees(startDegrees), clampDegrees(middleDegrees), clampDegrees(endDegrees),
                clampHalfGauge(halfGauge)));
    }

    /**
     * 客户端：清除一条轨道的逐轨道超高（回到节点派生滚转）。
     * 半轨距传 {@code NaN} = 沿用服务端当前值，因此「清除」只清三个控制点、不会顺手改掉轨距。
     */
    public static void sendClearRailTiltC2S(BlockPos p1, BlockPos p2, double halfGauge) {
        writeAndSend(new RailTiltPayload.Payload(p1, p2, false, true,
                0.0D, 0.0D, 0.0D, clampHalfGauge(halfGauge)));
    }

    /**
     * 客户端写侧。字段顺序由 {@link RailTiltPayload#write} 单点定义（读侧同一个类）。
     * <p>
     * 写侧的夹取只是「让正常客户端不会发出越界值」；服务端仍然会再独立校验一次，
     * 且两条路径共用 {@link #MAX_TILT_DEGREES} / {@link #MIN_HALF_GAUGE} / {@link #MAX_HALF_GAUGE}，
     * 不会出现「客户端放行、服务端拒绝」的口径不一致。
     */
    private static void writeAndSend(RailTiltPayload.Payload payload) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RailTiltPayload.write(buf, payload);
        NetworkManager.sendToServer(RAIL_TILT_EDIT, buf);
    }

    /** 客户端侧的夹取：非有限值归零，其余夹到 ±{@link #MAX_TILT_DEGREES}。服务端仍会再校验一次。 */
    private static double clampDegrees(double degrees) {
        if (!Double.isFinite(degrees)) {
            return 0.0D;
        }
        return Math.max(-MAX_TILT_DEGREES, Math.min(MAX_TILT_DEGREES, degrees));
    }

    /** 客户端侧的夹取：非有限值保持 NaN（= 沿用服务端当前值），其余夹到 [0.25, 2.0]。 */
    private static float clampHalfGauge(double halfGauge) {
        if (!Double.isFinite(halfGauge)) {
            return Float.NaN;
        }
        return (float) Math.max(MIN_HALF_GAUGE, Math.min(MAX_HALF_GAUGE, halfGauge));
    }
}
