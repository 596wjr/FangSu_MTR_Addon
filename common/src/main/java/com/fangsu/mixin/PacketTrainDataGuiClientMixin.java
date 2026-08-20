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
 * 注意：本 mixin 必须 {@code remap = false} 且注入目标只用方法名、不带完整描述符。
 * MTR 是 compileOnly 依赖、不在 Loom 的重映射表里——写任何完整描述符
 * （如 {@code generatePathC2S(J)V}）编译期都会报
 * "Unable to locate obfuscation mapping"；而 Minecraft 参数类型的运行时（Yarn）名在
 * 1.18.2（{@code MinecraftClient}/{@code PacketByteBuf}）与 1.20.1
 * （{@code Minecraft}/{@code FriendlyByteBuf}）不同，若带完整描述符还会导致 1.18.2
 * 运行时命中失败。纯名字匹配（两方法在类中各唯一）则绑定与版本无关、都能命中。
 * <p>
 * handler 形参（Minecraft / FriendlyByteBuf）是按位置与目标方法参数绑定的，仅用于
 * 声明签名，不参与目标解析，因此两个版本下 handler 里的 {@code packet} 都能正确拿到
 * 缓冲；参数里的 net.minecraft 类型本身也会由编译期反混淆回 Yarn 名，与目标一致。
 */
@Mixin(value = PacketTrainDataGuiClient.class, remap = false)
public abstract class PacketTrainDataGuiClientMixin {

    @Inject(method = "generatePathC2S", at = @At("HEAD"))
    private static void fangsu$onGenerationStarted(long sidingId, CallbackInfo ci) {
        PathGenerationStatusManager.onGenerationStarted(sidingId);
    }

    @Inject(method = "generatePathS2C", at = @At("HEAD"))
    private static void fangsu$onGenerationResult(Minecraft minecraftClient, FriendlyByteBuf packet, CallbackInfo ci) {
        // 绝对索引读取，不推进 readerIndex，避免原逻辑顺序读取越界
        final int readerIndex = packet.readerIndex();
        final long depotId = packet.getLong(readerIndex);
        final int successfulSegments = packet.getInt(readerIndex + 8);
        PathGenerationStatusManager.onGenerationResult(depotId, successfulSegments);
    }
}
