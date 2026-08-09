package com.fangsu.mixin;

import com.fangsu.utils.GraphicsTextureHelper;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * 列车销毁时清理 LCD 纹理（修复纹理泄漏）。
 *
 * MTR3 的 deleteTrains 实际删除逻辑包在 {@code client.execute(...)} 中异步执行，
 * 因此 HEAD 先快照当前 TRAINS 的 id，RETURN 时把清理任务排入同一主线程队列
 * （FIFO，必在 removeIf 之后执行），对比快照差集释放已移除列车的纹理。
 */
@Mixin(ClientData.class)
public class ClientDataMixin {

    @Unique
    private static final ThreadLocal<Set<Long>> FANGSU$SNAPSHOT = ThreadLocal.withInitial(HashSet::new);

    @Inject(method = "deleteTrains", at = @At("HEAD"))
    private static void fangsu$snapshot(Minecraft client, FriendlyByteBuf packet, CallbackInfo ci) {
        final Set<Long> snapshot = FANGSU$SNAPSHOT.get();
        snapshot.clear();
        // 防御网络线程与主线程并发修改 TRAINS
        for (TrainClient train : new ArrayList<>(ClientData.TRAINS)) {
            snapshot.add(train.id);
        }
    }

    @Inject(method = "deleteTrains", at = @At("RETURN"))
    private static void fangsu$cleanup(Minecraft client, FriendlyByteBuf packet, CallbackInfo ci) {
        final Set<Long> snapshot = FANGSU$SNAPSHOT.get();
        // 在返回前（网络线程）拷贝快照，避免主线程任务执行时快照已被下次调用清空
        final Set<Long> toCheck = new HashSet<>(snapshot);
        snapshot.clear();
        client.execute(() -> {
            // 此时 MTR 的 removeIf 已执行完毕（FIFO 队列）
            for (long id : toCheck) {
                boolean stillPresent = false;
                for (TrainClient train : ClientData.TRAINS) {
                    if (train.id == id) {
                        stillPresent = true;
                        break;
                    }
                }
                if (!stillPresent) {
                    GraphicsTextureHelper.getInstance().removeDrawGraphic("train_" + id);
                    com.fangsu.Main.LOGGER.info("[FangSu LCD] Released texture for removed train {}", id);
                }
            }
        });
    }
}
