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
 */
@Mixin(value = PacketTrainDataGuiClient.class, remap = false)
public abstract class PacketTrainDataGuiClientMixin {

    @Inject(method = "generatePathC2S(J)V", at = @At("HEAD"))
    private static void fangsu$onGenerationStarted(long sidingId, CallbackInfo ci) {
        PathGenerationStatusManager.onGenerationStarted(sidingId);
    }

    @Inject(method = "generatePathS2C(Lnet/minecraft/client/Minecraft;Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("HEAD"))
    private static void fangsu$onGenerationResult(Minecraft minecraftClient, FriendlyByteBuf packet, CallbackInfo ci) {
        // 绝对索引读取，不推进 readerIndex，避免原逻辑顺序读取越界
        final int readerIndex = packet.readerIndex();
        final long depotId = packet.getLong(readerIndex);
        final int successfulSegments = packet.getInt(readerIndex + 8);
        PathGenerationStatusManager.onGenerationResult(depotId, successfulSegments);
    }
}
