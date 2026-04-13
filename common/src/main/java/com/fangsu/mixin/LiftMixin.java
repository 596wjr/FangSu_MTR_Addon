package com.fangsu.mixin;

import com.fangsu.Main;
import com.fangsu.blockEntities.IPlatformDoor;
import com.fangsu.data.LiftExtraSupplier;
import mtr.data.Lift;
import mtr.data.MessagePackHelper;
import mtr.data.RailwayData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.msgpack.core.MessagePacker;
import org.msgpack.value.Value;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.Map;

@Mixin(value = Lift.class, remap = false)
public class LiftMixin implements LiftExtraSupplier {
    @Final
    @Shadow
    public static int DOOR_MAX;

    @Shadow
    public int liftDepth;

    @Shadow
    public Direction facing;

    @Shadow
    public int liftOffsetX, liftOffsetY, liftOffsetZ;

    @Shadow
    protected double currentPositionX, currentPositionY, currentPositionZ;

    @Shadow
    protected float doorValue;

    @Shadow
    protected static final String KEY_LIFT_UPDATE = "lift_update";

    @Unique
    private String fangsu$modelKey = "";

    @Override
    @Unique
    public void fangsu$setModelKey(String modelKey) {
        this.fangsu$modelKey = modelKey;
    }

    @Override
    @Unique
    public String fangsu$getModelKey() {
        return fangsu$modelKey;
    }

    @Unique
    private static final String MODEL_KEY = "fangsu_model_key";


    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("TAIL"), remap = false)
    private void fromMessagePack(Map<String, Value> map, CallbackInfo ci) {
        MessagePackHelper messagePackHelper = new MessagePackHelper(map);
        fangsu$modelKey = messagePackHelper.getString(MODEL_KEY, "a");
    }

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"), remap = true)
    private void fromPacket(FriendlyByteBuf packet, CallbackInfo ci) {
        if (packet.readableBytes() <= 1) {
            Main.LOGGER.info("Invalid packet length : {}", packet.readableBytes());
            return;
        }
        if (packet.readInt() != FANGSU_PACKET_EXTRA_MAGIC) {
            Main.LOGGER.info("Invalid packet extra magic");
            packet.readerIndex(packet.readerIndex() - 2);
            return;
        }
        String key = packet.readUtf();
        Main.LOGGER.info("read key {}", key);
        fangsu$modelKey = key;
    }

    @Inject(method = "toMessagePack", at = @At("TAIL"), remap = false)
    private void toMessagePack(MessagePacker messagePacker, CallbackInfo ci) throws IOException {
        messagePacker.packString("fangsu_model_key").packString(fangsu$modelKey);
    }

    @Inject(method = "messagePackLength", at = @At("TAIL"), cancellable = true, remap = false)
    private void messagePackLength(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + 1);
    }

    @Unique
    private final int FANGSU_PACKET_EXTRA_MAGIC = 0x6673;


    @Inject(method = "writePacket", at = @At("TAIL"))
    private void toPacket(FriendlyByteBuf packet, CallbackInfo ci) {
        packet.writeInt(FANGSU_PACKET_EXTRA_MAGIC);
        packet.writeUtf(fangsu$modelKey);
        Main.LOGGER.info("writing {}", fangsu$modelKey);
    }

    @Inject(method = "checkDoor", at = @At("RETURN"), cancellable = true)
    private void checkDoor(Level world, boolean front, CallbackInfoReturnable<Boolean> cir) {
        boolean hasDoorVanilla = cir.getReturnValueZ();
        if (hasDoorVanilla) cir.setReturnValue(true);
        boolean hasDoorCustom = false;

        final Direction directionClockwise = facing.getClockWise();
        final int sign = front ? 1 : -1;
        for (int i = -1; i <= 1; i++) {
            final BlockPos checkPos = RailwayData.newBlockPos(currentPositionX + liftOffsetX / 2F - facing.getStepX() * sign * (liftDepth / 2F + 0.5) + directionClockwise.getStepX() * i, currentPositionY + liftOffsetY, currentPositionZ + liftOffsetZ / 2F - facing.getStepZ() * sign * (liftDepth / 2F + 0.5) + directionClockwise.getStepZ() * i);
            if (world.getNearestPlayer(currentPositionX, currentPositionY, currentPositionZ, 32, entity -> true) != null && RailwayData.chunkLoaded(world, checkPos) && RailwayData.chunkLoaded(world, checkPos.above())) {
                final BlockEntity entity1 = world.getBlockEntity(checkPos);
                if (entity1 instanceof IPlatformDoor be1
                        && !be1.isLocked()
                ) {
                    (be1).setDoorValue(Math.min(Math.round(doorValue), DOOR_MAX));
                    hasDoorCustom = true;
                }
            }
        }

        cir.setReturnValue(hasDoorCustom || hasDoorVanilla);
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void update(String key, FriendlyByteBuf packet, CallbackInfo ci) {
        if (KEY_LIFT_UPDATE.equals(key) && packet.readableBytes() >= 1) {
            String readKey = packet.readUtf();
            if (MODEL_KEY.equals(readKey)) {
                fangsu$modelKey = packet.readUtf();
            }
        }
    }
}
