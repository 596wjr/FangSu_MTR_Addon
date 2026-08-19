package com.fangsu.mixin;

import com.fangsu.utils.PathGenerationStatusManager;
import mtr.packet.PacketTrainDataGuiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR3「刷新线路」提示：MTR3 寻路本身已是独立线程全速，这里只加明确提示——
 * C2S 发送处登记开始时间，S2C 回执处发完成通知（成功/失败/异常都经
 * {@code generatePathS2C} 唯一出口回传，界面关闭后通知仍能到达触发者）。
 * <p>
 * {@code generatePathC2S} 参数名是 sidingId 但实际传的是 depot.id（原版按钮与
 * 复选框均如此），登记语义即「该车厂刷新开始」。{@code generatePathS2C} 的
 * HEAD 注入用绝对索引 getLong/getInt 读取，不推进 readerIndex——因为原逻辑
 * 随后会在同一个 {@code packet} 上用顺序 readLong/readInt 重读同样的值，若此处
 * 先消费掉字节会让原逻辑读到越界（FriendlyByteBuf 为流式顺序读取）。
 * S2C 包处理在 netty 线程，通知内部切主线程。
 * <p>
 * 注意：这里不能加 {@code remap = false}。MTR 是 compileOnly 依赖、方法名/类名
 * 不会被重映射，但注入描述符里的 Minecraft 参数类型（{@code Minecraft}、
 * {@code FriendlyByteBuf}）在 1.18.2 与 1.20.1 的运行时（Yarn）名字不同
 * （1.18.2 为 {@code MinecraftClient}/{@code PacketByteBuf}）。必须让 Loom 经
 * refmap 把描述符重映射到中间名（{@code class_310}/{@code class_2540}），否则在
 * 1.18.2 下会因找不到 {@code generatePathS2C} 目标而崩溃。与 {@code ClientDataMixin}
 * 保持同一写法。
 */
@Mixin(PacketTrainDataGuiClient.class)
public abstract class PacketTrainDataGuiClientMixin {

    @Inject(method = "generatePathC2S(J)V", at = @At("HEAD"))
    private static void fangsu$onGenerationStarted(long sidingId, CallbackInfo ci) {
        PathGenerationStatusManager.onGenerationStarted(sidingId);
    }

    // method 用名字而非完整描述符：Minecraft/FriendlyByteBuf 的运行时（Yarn）名字在
    // 1.18.2（MinecraftClient/PacketByteBuf）与 1.20.1（Minecraft/FriendlyByteBuf）不同，
    // 交由 Loom 生成的 refmap 把 handler 签名映射到中间名（class_310/class_2540）后，
    // 两个版本都能正确命中。generatePathS2C 在类中唯一，名字匹配无歧义。
    @Inject(method = "generatePathS2C", at = @At("HEAD"))
    private static void fangsu$onGenerationResult(Minecraft minecraftClient, FriendlyByteBuf packet, CallbackInfo ci) {
        // 绝对索引读取，不推进 readerIndex，避免原逻辑顺序读取越界
        final int readerIndex = packet.readerIndex();
        final long depotId = packet.getLong(readerIndex);
        final int successfulSegments = packet.getInt(readerIndex + 8);
        PathGenerationStatusManager.onGenerationResult(depotId, successfulSegments);
    }
}
