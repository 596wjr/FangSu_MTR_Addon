package com.fangsu.mtr.rail;

import com.fangsu.Main;
import com.fangsu.mappings.rail.RailPoseExtra;
import mtr.data.MessagePackHelper;
import net.minecraft.network.FriendlyByteBuf;
import org.msgpack.core.MessagePacker;
import org.msgpack.value.Value;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MTR3 轨道附加姿态的<b>序列化读写实现</b>（msgpack 存档路径 + FriendlyByteBuf 网络路径）。
 * <p>
 * 这里刻意<b>不</b>写成 mixin 的一部分：{@code RailPoseExtraMixin} 的注入处理器只做转发，
 * 全部逻辑留在本类，于是可以用真实 MTR3 的 {@code mtr.data.Rail} / {@code MessagePacker} /
 * {@code FriendlyByteBuf} 在不启动游戏的前提下做往返验证（见
 * {@code .tmp_p5_0_probe/RailPoseRoundTripProbe.java}）。
 * <p>
 * <b>两条路径必须同时覆盖</b>（P5-0 的核心陷阱）：
 * <pre>
 *   存档：Rail.toMessagePack + Rail.messagePackLength  →  Rail(Map&lt;String,Value&gt;)     [msgpack 世界存档]
 *   网络：Rail.writePacket                            →  Rail(FriendlyByteBuf)          [S2C 同步]
 * </pre>
 * 只补网络路径会出现「联机时看到超高、退出重进就丢」；只补存档路径则相反。
 * <p>
 * <b>默认姿态逐字节不变</b>：两条路径都只在 {@link RailPoseExtra#shouldPersist()} 为真时才写，
 * 因此普通 MTR3 轨道的存档与数据包与装本模组之前完全一致。
 * <p>
 * msgpack 是「字符串键的 map」，天然自带存在性判断；而 {@code writePacket} 追加的是<b>变长</b>
 * 尾部数据，读侧没有长度前缀可依赖，所以用一个只在本模组写侧出现过的 8 字节魔数做哨兵
 * （见 {@link #PACKET_MAGIC}），读不到就当作没写。
 */
public final class RailPoseExtraIO {

    /**
     * 网络路径的尾部哨兵：{@code 0x7FF8465352504558}（高 16 位落在 IEEE-754 的 NaN 区，
     * 低 56 位是 ASCII {'F','S','R','P','E','X','T'} 便于在十六进制转储里肉眼辨认）。
     * <p>
     * 为什么要哨兵：{@code writePacket} 追加的是<b>变长</b>尾部数据，而 MTR3 的读侧
     * （{@code Rail(FriendlyByteBuf)}）没有任何「本轨道还带了多少附加字节」的长度前缀可用，
     * 也没有协议版本号可以协商。要同时满足「默认姿态逐字节不变」和「有姿态时能读回来」，
     * 只能靠一个只在本模组写侧出现过的魔术数来判断尾部是否存在。
     * <p>
     * 选值理由与<b>残余风险（已如实记录）</b>：
     * <ul>
     *   <li>{@code Rail.writePacket} 之后紧跟的 8 字节，在 MTR3 的真实布局里只会是
     *       {@code BlockPos.asLong()}、某个 {@code long} id、或下一条轨道的 {@code h/k/r/t}
     *       double（见 {@code RailwayData.java:287-296}、{@code PacketTrainDataGuiServer:105-114}、
     *       {@code PathData:91-98}、{@code ClientData:84-99}）。</li>
     *   <li>本常量解释成 double 是 NaN（指数位全 1），而合法轨道的几何量一定是有限值，
     *       所以它<b>不可能</b>与下一条轨道的第一项几何冲突。</li>
     *   <li>它<b>可能</b>与某个 {@code BlockPos.asLong()} 或 long id 数值相同 —— 26 位 x 左移 38 后
     *       最高字节可以取到 {@code 0x7F}，因此「按位不可能与 BlockPos 冲突」是<b>不成立</b>的，
     *       不能这样论证。碰撞概率是 2^-64 量级，且即使真的碰撞，
     *       {@link #readPacket(FriendlyByteBuf)} 还会校验解出的姿态必须满足
     *       {@code shouldPersist()}，不满足就把读指针还原。</li>
     * </ul>
     * 最坏情况因此是「这条轨道的姿态这次没读到」（下次同步/存档仍会重写），
     * <b>而不是</b>后续 MTR 数据整体错位。
     */
    public static final long PACKET_MAGIC = 0x7FF8465352504558L;

    /** 与 MTR 的 {@code SerializedDataBase.PACKET_STRING_READ_LENGTH} 保持一致。 */
    private static final int PACKET_STRING_READ_LENGTH = 32767;

    // 一次性诊断日志：每种路径「首次真正写下 / 读回」各打一条，方便进游戏时确认钩子活着。
    // 用 AtomicBoolean 而不是 boolean 是因为 Rail 会被多个线程构造（服务端主线程 / 网络线程）。
    private static final AtomicBoolean LOGGED_SAVE_WRITE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_SAVE_READ = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_PACKET_WRITE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_PACKET_READ = new AtomicBoolean();

    private RailPoseExtraIO() {
    }

    // ==================== msgpack 世界存档路径 ====================

    /**
     * msgpack 读侧：挂在 {@code mtr.data.Rail(Map&lt;String,Value&gt;)} 构造器的 TAIL。
     * <p>
     * 这就是「从存档重建 Rail」的唯一入口 —— 已核实 3.2.2 的调用链是
     * {@code RailwayDataFileSaveModule.RailEntry(Map)} → {@code new Rail(mapSK)}
     * （另见 {@code RailwayData.java:846} 与 {@code PathData(Map)}），全树没有别的 msgpack 读法。
     */
    public static RailPoseExtra readMessagePack(Map<String, Value> map) {
        if (map == null) {
            return RailPoseExtra.DEFAULT;
        }
        try {
            final RailPoseExtra pose = RailPoseExtra.decode(
                    new MessagePackHelper(map).getString(RailPoseExtra.KEY, ""));
            if (pose.shouldPersist()) {
                logOnce(LOGGED_SAVE_READ, "轨道附加姿态：从存档 msgpack 读回 {}", pose.encode());
            }
            return pose;
        } catch (Exception ignored) {
            // 键存在但不是字符串等异常情形：按「没有附加姿态」处理，绝不让存档加载失败
            return RailPoseExtra.DEFAULT;
        }
    }

    /** msgpack 写侧要额外写的键值对数（0 或 1），供 {@code Rail.messagePackLength()} 相加。 */
    public static int messagePackPairs(RailPoseExtra pose) {
        return pose != null && pose.shouldPersist() ? 1 : 0;
    }

    /** msgpack 写侧：挂在 {@code Rail.toMessagePack(MessagePacker)} 的 TAIL。 */
    public static void writeMessagePack(MessagePacker messagePacker, RailPoseExtra pose) throws IOException {
        if (messagePacker == null || pose == null || !pose.shouldPersist()) {
            return;
        }
        messagePacker.packString(RailPoseExtra.KEY).packString(pose.encode());
        logOnce(LOGGED_SAVE_WRITE, "轨道附加姿态：写入存档 msgpack {}", pose.encode());
    }

    // ==================== FriendlyByteBuf 网络同步路径 ====================

    /**
     * 网络读侧：挂在 {@code mtr.data.Rail(FriendlyByteBuf)} 构造器的 TAIL。
     * <p>
     * 写侧只在 {@code shouldPersist()} 时追加，所以这里必须先判断「后面到底有没有本模组的尾部」。
     * 判据是 8 字节魔数；不满足就原样返回默认姿态，<b>一个字节也不消费</b>。
     */
    public static RailPoseExtra readPacket(FriendlyByteBuf packet) {
        if (packet == null || packet.readableBytes() < Long.BYTES) {
            return RailPoseExtra.DEFAULT;
        }
        final int mark = packet.readerIndex();
        if (packet.getLong(mark) != PACKET_MAGIC) {
            return RailPoseExtra.DEFAULT;
        }
        packet.readLong();
        try {
            final RailPoseExtra pose = RailPoseExtra.decode(packet.readUtf(PACKET_STRING_READ_LENGTH));
            if (pose.shouldPersist()) {
                logOnce(LOGGED_PACKET_READ, "轨道附加姿态：从数据包读回 {}", pose.encode());
                return pose;
            }
        } catch (Exception ignored) {
            // 落到这里说明那 8 字节只是「碰巧相等」，或者数据被截断/损坏
        }
        packet.readerIndex(mark);
        return RailPoseExtra.DEFAULT;
    }

    /** 网络写侧：挂在 {@code Rail.writePacket(FriendlyByteBuf)} 的 TAIL。 */
    public static void writePacket(FriendlyByteBuf packet, RailPoseExtra pose) {
        if (packet == null || pose == null || !pose.shouldPersist()) {
            return;
        }
        packet.writeLong(PACKET_MAGIC);
        packet.writeUtf(pose.encode());
        logOnce(LOGGED_PACKET_WRITE, "轨道附加姿态：写入数据包 {}", pose.encode());
    }

    /** 一次性日志：只在每个方向第一次真的发生读写时打一行，避免刷屏。 */
    private static void logOnce(AtomicBoolean flag, String message, String detail) {
        if (flag.compareAndSet(false, true)) {
            Main.LOGGER.info("[P5-0] " + message, detail);
        }
    }
}
