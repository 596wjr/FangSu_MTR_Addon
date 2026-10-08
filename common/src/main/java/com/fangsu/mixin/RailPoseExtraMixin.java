package com.fangsu.mixin;

import com.fangsu.mappings.rail.RailPoseExtra;
import com.fangsu.mtr.rail.RailPoseExtraHolder;
import com.fangsu.mtr.rail.RailPoseExtraIO;
import mtr.data.Rail;
import net.minecraft.network.FriendlyByteBuf;
import org.msgpack.core.MessagePacker;
import org.msgpack.value.Value;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.Map;

/**
 * 把 FangSu 的「轨道附加姿态」挂到 MTR3 的 {@link Rail} 上，并让它在<b>存档与联机同步两条路径</b>上存活。
 * <p>
 * <b>为什么 MTR4 的 {@code RailSchemaMixin} 不能照搬</b>：MTR3 <b>没有</b> {@code RailSchema}，
 * 也没有 {@code RailMath}；{@code mtr.data.Rail} 自己既是数据也是几何，序列化就是
 * {@code toMessagePack}/{@code messagePackLength}/{@code writePacket} 三个方法 +
 * {@code Rail(Map)}/{@code Rail(FriendlyByteBuf)} 两个读构造器。所以这里对应的是
 * <b>ANTE（{@code fabric.cn.zbx1425.mtrsteamloco.mixin.RailMixin}，已用 javap 逐条核对）</b>
 * 的注入集合，而不是 MTR4 的形状。
 * <p>
 * 注入清单（全部经 {@code javap -p} 对 4 个真实 3.2.2 jar 核实，描述符一致：
 * 1.18.2 Fabric/Forge hotfix-1、1.20.1 Fabric hotfix-1、1.20.1 Forge hotfix-2）：
 * <pre>
 *   存档写：toMessagePack(Lorg/msgpack/core/MessagePacker;)V   @TAIL
 *   存档写：messagePackLength()I                               @RETURN (+1，与上一行同门控)
 *   存档读：&lt;init&gt;(Ljava/util/Map;)V                           @TAIL
 *   网络写：writePacket(Lnet/minecraft/network/FriendlyByteBuf;)V @TAIL
 *   网络读：&lt;init&gt;(Lnet/minecraft/network/FriendlyByteBuf;)V     @TAIL
 * </pre>
 * 每一处都显式写了 {@code method = ...}（本仓库硬性要求），没有使用 {@code @ModifyArgs}。
 * <p>
 * {@code mtr.data.Rail} 是硬依赖类（永远存在），且以上 5 个成员在全部可核实版本里形状相同，
 * 因此这里用默认的 {@code require = 1}：万一将来某个版本改了签名，宁可在启动时<b>报错</b>，
 * 也不要静默丢失姿态 —— P5-0 是「静默子阶段」，静默失效无法被发现。
 * <p>
 * 姿态数据住在 {@code @Unique} 字段里（MTR4 用 {@code RailSchema} 的 {@code @Unique} 字段，
 * 这里用 {@code Rail} 的），读写逻辑全部委托给 {@link RailPoseExtraIO}。
 */
@Mixin(value = Rail.class, remap = false)
public abstract class RailPoseExtraMixin implements RailPoseExtraHolder {

    /**
     * 附加姿态。刻意<b>不给初始化器</b>：Mixin 对 {@code @Unique} 字段初始化器的处理与目标类
     * 构造器形态有关，而 {@code Rail} 有 4 个构造器；用 null 表示「未设置」最稳妥。
     */
    @Unique
    private RailPoseExtra fangsu$pose;

    // ==================== 存档（msgpack）读 ====================

    /**
     * {@code Rail(Map<String, Value>)} —— 从存档 msgpack 重建轨道的唯一入口。
     * <p>
     * 已核实调用链：{@code RailwayDataFileSaveModule.RailEntry(Map)} → {@code new Rail(mapSK)}
     * （{@code RailwayData.java:846} 与 {@code PathData(Map)} 同形）。
     */
    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("TAIL"))
    private void fangsu$readMessagePack(Map<String, Value> map, CallbackInfo callbackInfo) {
        this.fangsu$pose = RailPoseExtraIO.readMessagePack(map);
    }

    // ==================== 网络同步读 ====================

    /**
     * {@code Rail(FriendlyByteBuf)} —— S2C 轨道数据包的反序列化。
     * <p>
     * 已核实调用点：{@code ClientData.writeRails} 与 {@code PacketTrainDataGuiClient.createRailS2C}。
     */
    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"))
    private void fangsu$readPacket(FriendlyByteBuf packet, CallbackInfo callbackInfo) {
        this.fangsu$pose = RailPoseExtraIO.readPacket(packet);
    }

    // ==================== 存档（msgpack）写 ====================

    /**
     * {@code Rail.toMessagePack(MessagePacker)} —— 只在非默认姿态时追加单个字符串键
     * {@link RailPoseExtra#KEY}，默认姿态的存档字节与原版完全一致。
     */
    @Inject(method = "toMessagePack(Lorg/msgpack/core/MessagePacker;)V", at = @At("TAIL"))
    private void fangsu$writeMessagePack(MessagePacker messagePacker, CallbackInfo callbackInfo) throws IOException {
        RailPoseExtraIO.writeMessagePack(messagePacker, fangsu$poseOrDefault());
    }

    /**
     * {@code Rail.messagePackLength()}I —— 必须在 {@code @RETURN} 上把多出来的键值对算进去。
     * <p>
     * 这个返回值是 {@code MessagePacker.packMapHeader(...)} 的 map 大小
     * （{@code RailwayDataFileSaveModule:266/307}、{@code RailEntry:351}、{@code PathData:75}、
     * {@code RailwayData:711}），少加 1 会让 msgpack 头与实际写入的键值对数不符、整个存档读不出来。
     * 门控与 {@code toMessagePack} 完全一致（同一对象、同一时刻、同一 {@code shouldPersist()} 判据），
     * 因此两者永远自洽。
     */
    @Inject(method = "messagePackLength()I", at = @At("RETURN"), cancellable = true)
    private void fangsu$messagePackLength(CallbackInfoReturnable<Integer> callbackInfo) {
        final int extraPairs = RailPoseExtraIO.messagePackPairs(fangsu$poseOrDefault());
        if (extraPairs != 0) {
            callbackInfo.setReturnValue(callbackInfo.getReturnValue() + extraPairs);
        }
    }

    // ==================== 网络同步写 ====================

    /**
     * {@code Rail.writePacket(FriendlyByteBuf)} —— 只在非默认姿态时追加
     * 「8 字节魔数 + 编码串」，默认姿态的数据包字节与原版完全一致。
     */
    @Inject(method = "writePacket(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"))
    private void fangsu$writePacket(FriendlyByteBuf packet, CallbackInfo callbackInfo) {
        RailPoseExtraIO.writePacket(packet, fangsu$poseOrDefault());
    }

    // ==================== RailPoseExtraHolder 实现 ====================

    @Override
    public RailPoseExtra getFangSuPose() {
        return fangsu$poseOrDefault();
    }

    @Override
    public void setFangSuPose(RailPoseExtra pose) {
        this.fangsu$pose = pose == null ? RailPoseExtra.DEFAULT : pose;
    }

    /** 注入处理器直接读字段，避免依赖接口方法在本类里的分派（同一目标上可能有别的 mixin）。 */
    @Unique
    private RailPoseExtra fangsu$poseOrDefault() {
        return this.fangsu$pose == null ? RailPoseExtra.DEFAULT : this.fangsu$pose;
    }
}
