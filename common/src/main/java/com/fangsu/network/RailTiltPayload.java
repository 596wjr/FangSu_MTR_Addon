package com.fangsu.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * {@code RAIL_TILT_EDIT} 载荷的编解码。
 * <p>
 * <b>为什么要有这个类</b>：本包<b>不做版本探测</b>（客户端与服务端永远运行同一份 FangSu 构建），
 * 写侧与读侧的字段顺序必须逐行对应，而 <b>javac 抓不到读写不对称</b> —— 把写侧放在客户端、
 * 读侧放在服务端时，顺序写错只是运行期读到垃圾值，或者多读一个字段把后面的字段整体错位。
 * 把写与读放进<b>同一个类、同一段字段顺序</b>，不对称就不再是一个「需要小心」的事情。
 * <p>
 * <b>载荷布局</b>（写侧 {@link #write} 与读侧 {@link #read} 逐行对应）：
 * <pre>
 *   BlockPos p1             端点 1（= 轨道的 position1）
 *   BlockPos p2             端点 2（= 轨道的 position2）
 *   boolean  setTilt        true = 写入三个控制点；false = 清除逐轨道超高
 *   boolean  clearTilt      true = 显式清除（与 setTilt=false 同义）
 *   double   startDegrees   起点控制点（度）
 *   double   middleDegrees  中间控制点（度）
 *   double   endDegrees     终点控制点（度）
 *   float    halfGauge      半轨距（米）；非有限值 = 沿用服务端当前值
 * </pre>
 * <b>全部字段都是定长</b>（{@code writeBlockPos} = 8 字节 long，其余为原始类型变体），
 * 所以「少写一个 / 多读一个」<b>不会抛异常</b>，只会静默错位。这一点由
 * {@code .tmp_p5_5_probe/PayloadSymmetryProbe.java} 的往返断言把守：
 * 写出的字节数必须等于读侧消费的字节数，且八个字段逐值相等。
 * <p>
 * 与 MTR4 版的载荷<b>逐字段相同</b>（含眼下冗余的 {@code clearTilt} 布尔），
 * 这样两个工程的线格式保持一致、可以并排阅读；字段顺序即契约，改动必须两侧同步。
 */
public final class RailTiltPayload {

    private RailTiltPayload() {
    }

    /**
     * 一次逐轨道超高编辑请求。
     *
     * @param position1   轨道 {@code position1}（轨道表的外层键）
     * @param position2   轨道 {@code position2}（轨道表的内层键）
     * @param setTilt     true = 写入三个控制点
     * @param clearTilt   true = 清除逐轨道超高（回到节点派生滚转）
     * @param startDegrees  起点控制点（度）
     * @param middleDegrees 中间控制点（度）
     * @param endDegrees    终点控制点（度）
     * @param halfGauge     半轨距（米）；非有限值 = 沿用服务端当前值
     */
    public record Payload(
            BlockPos position1,
            BlockPos position2,
            boolean setTilt,
            boolean clearTilt,
            double startDegrees,
            double middleDegrees,
            double endDegrees,
            float halfGauge
    ) {
    }

    /** 写出载荷。字段顺序见类注释，必须与 {@link #read} 逐行对应。 */
    public static void write(FriendlyByteBuf buf, Payload payload) {
        buf.writeBlockPos(payload.position1());
        buf.writeBlockPos(payload.position2());
        buf.writeBoolean(payload.setTilt());
        buf.writeBoolean(payload.clearTilt());
        buf.writeDouble(payload.startDegrees());
        buf.writeDouble(payload.middleDegrees());
        buf.writeDouble(payload.endDegrees());
        buf.writeFloat(payload.halfGauge());
    }

    /**
     * 读出载荷。必须在主线程排队<b>之前</b>调用（排队回调可能在网络缓冲区释放之后才执行）。
     * 字段顺序与 {@link #write} 逐行对应。
     */
    public static Payload read(FriendlyByteBuf buf) {
        final BlockPos position1 = buf.readBlockPos();
        final BlockPos position2 = buf.readBlockPos();
        final boolean setTilt = buf.readBoolean();
        final boolean clearTilt = buf.readBoolean();
        final double startDegrees = buf.readDouble();
        final double middleDegrees = buf.readDouble();
        final double endDegrees = buf.readDouble();
        final float halfGauge = buf.readFloat();
        return new Payload(position1, position2, setTilt, clearTilt,
                startDegrees, middleDegrees, endDegrees, halfGauge);
    }
}
