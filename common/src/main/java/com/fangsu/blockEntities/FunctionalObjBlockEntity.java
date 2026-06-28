package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.client.ClientHooks;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.network.ModNetwork;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 功能性方块实体的抽象基类，继承自 {@link BaseObjBlockEntity}。
 * <p>
 * 定义了 extraConfig 的存取、客户端↔服务端同步、配置屏幕等通用功能。
 * 所有需要 extraConfig 的方块实体应继承此类。
 */
public abstract class FunctionalObjBlockEntity extends BaseObjBlockEntity implements Syncable {

    protected Map<String, String> extraConfigs = new ConcurrentHashMap<>();

    public float translateX = 0, translateY = 0, translateZ = 0;
    public float rotateX = 0, rotateY = 0, rotateZ = 0;

    public FunctionalObjBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    // ==================== ExtraConfig 存取 ====================

    public final String getExtraConfig(String key) {
        return extraConfigs.get(key);
    }

    public final String getExtraConfig(String key, String defaultValue) {
        return extraConfigs.getOrDefault(key, defaultValue);
    }

    public final void setExtraConfig(String key, String value) {
        extraConfigs.put(key, value);
    }

    public final void ensureExtraConfig(String key, String value) {
        extraConfigs.putIfAbsent(key, value);
    }

    public final boolean getExtraConfigBool(String key, boolean defaultValue) {
        String value = extraConfigs.get(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value);
    }

    public final int getExtraConfigInt(String key, int defaultValue) {
        String value = extraConfigs.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public final float getExtraConfigFloat(String key, float defaultValue) {
        String value = extraConfigs.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putFloat("translateX", translateX);
        tag.putFloat("translateY", translateY);
        tag.putFloat("translateZ", translateZ);
        tag.putFloat("rotateX", rotateX);
        tag.putFloat("rotateY", rotateY);
        tag.putFloat("rotateZ", rotateZ);

        try {
            this.whenSaving(extraConfigs);
        } catch (Exception e) {
            Main.LOGGER.error("Failed to save extra configs for {} at {}", getClass().getSimpleName(), getBlockPos(), e);
        }

        if (extraConfigs != null) {
            CompoundTag subConfigTag = new CompoundTag();
            for (String key : extraConfigs.keySet()) {
                String value = extraConfigs.get(key);
                if (value != null) subConfigTag.putString(key, value);
            }
            tag.put("extraConfig", subConfigTag);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);

        translateX = tag.contains("translateX") ? tag.getFloat("translateX") : 0;
        translateY = tag.contains("translateY") ? tag.getFloat("translateY") : 0;
        translateZ = tag.contains("translateZ") ? tag.getFloat("translateZ") : 0;
        rotateX = tag.contains("rotateX") ? tag.getFloat("rotateX") : 0;
        rotateY = tag.contains("rotateY") ? tag.getFloat("rotateY") : 0;
        rotateZ = tag.contains("rotateZ") ? tag.getFloat("rotateZ") : 0;

        extraConfigs.clear();
        if (tag.contains("extraConfig")) {
            CompoundTag subConfigTag = tag.getCompound("extraConfig");
            for (String key : subConfigTag.getAllKeys()) {
                extraConfigs.put(key, subConfigTag.getString(key));
            }
        }

        try {
            this.whenLoading();
        } catch (Exception e) {
            Main.LOGGER.error("Failed to load block entity {} at {}", getClass().getSimpleName(), getBlockPos(), e);
        }
    }

    /**
     * 保存前回调，允许子类在 extraConfigs 写入 NBT 之前补充或修改配置。
     */
    public void whenSaving(Map<String, String> extraConfigs) {
    }

    // ==================== 网络同步 (C2S) ====================

    @Override
    public void writeC2S(FriendlyByteBuf buf) {
        buf.writeFloat(translateX);
        buf.writeFloat(translateY);
        buf.writeFloat(translateZ);
        buf.writeFloat(rotateX);
        buf.writeFloat(rotateY);
        buf.writeFloat(rotateZ);
        buf.writeUtf(mainModel != null ? mainModel : "");

        int ecSize = Math.min(extraConfigs.size(), 256);
        buf.writeInt(ecSize);
        int count = 0;
        for (String key : extraConfigs.keySet()) {
            if (count >= ecSize) break;
            String value = extraConfigs.get(key);
            buf.writeUtf(key != null ? key : "");
            buf.writeUtf(value != null ? value : "");
            count++;
        }

        int smSize = Math.min(subModels.size(), 256);
        buf.writeInt(smSize);
        count = 0;
        for (String key : subModels.keySet()) {
            if (count >= smSize) break;
            String value = subModels.get(key);
            buf.writeUtf(key != null ? key : "");
            buf.writeUtf(value != null ? value : "");
            count++;
        }
    }

    @Override
    public void readC2S(FriendlyByteBuf buf) {
        translateX = buf.readFloat();
        translateY = buf.readFloat();
        translateZ = buf.readFloat();
        rotateX = buf.readFloat();
        rotateY = buf.readFloat();
        rotateZ = buf.readFloat();
        mainModel = buf.readUtf();
        if (mainModel.isEmpty()) mainModel = null;

        int size = Math.min(buf.readInt(), 256);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(1024);
            if (key != null && !key.isEmpty()) {
                extraConfigs.put(key, value != null ? value : "");
            }
        }

        size = Math.min(buf.readInt(), 256);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(1024);
            if (key != null && !key.isEmpty()) {
                subModels.put(key, value != null ? value : "");
            }
        }

        // 重新加载配置到字段（确保 isolation/doorOpenOverride 等同步）
        try {
            this.whenLoading();
        } catch (Exception e) {
            Main.LOGGER.error("Failed to reload block entity {} at {}", getClass().getSimpleName(), getBlockPos(), e);
        }
        this.setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    3
            );
        }
        this.setChanged();
    }

    public abstract void whenLoading();

    /**
     * Called when this block entity is being disposed (e.g. chunk unload / removal).
     */
    public void whenDisposing() {
    }

    @Override
    public void setRemoved() {
        if (!disposed) {
            whenDisposing();
        }
        super.setRemoved();
    }

    void syncToServer() {
        if (level != null && level.isClientSide) {
            this.whenSaving(this.extraConfigs);
            if (!level.hasChunk(getBlockPos().getX() >> 4, getBlockPos().getZ() >> 4)) return;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBlockPos(getBlockPos());
            writeC2S(buf);
            NetworkManager.sendToServer(ModNetwork.BE_SYNC, buf);
        }
    }

    public void sendUpdateC2S() {
        if (level != null && level.isClientSide)
            syncToServer();
        this.setChanged();
        this.markShapeDirty();
    }

    // ==================== 坐标变换 ====================

    public Vec3 worldToLocal(Vec3 worldPos) {
        Level level = this.getLevel();
        if (level == null) return Vec3.ZERO;

        BlockPos pos = this.getBlockPos();
        Direction facing = level.getBlockState(pos)
                .getValue(BaseObjBlock.FACING);

        // 1. 世界坐标 → 碰撞箱原点
        Vec3 v = worldPos.subtract(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5
        );

        // 2. 反向高级平移
        v = v.subtract(
                this.translateX,
                this.translateY,
                this.translateZ
        );

        // 3. 反向高级旋转（顺序必须和渲染相反）
        v = rotateZ(v, -this.rotateZ);
        v = rotateY(v, -this.rotateY);
        v = rotateX(v, -this.rotateX);

        // 4. 反向方块朝向
        v = rotateY(v, (float) Math.toRadians(facing.toYRot()));

        return v;
    }

    private static Vec3 rotateX(Vec3 v, float rad) {
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vec3(
                v.x,
                v.y * cos - v.z * sin,
                v.y * sin + v.z * cos
        );
    }

    private static Vec3 rotateY(Vec3 v, float rad) {
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vec3(
                v.x * cos + v.z * sin,
                v.y,
                -v.x * sin + v.z * cos
        );
    }

    private static Vec3 rotateZ(Vec3 v, float rad) {
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vec3(
                v.x * cos - v.y * sin,
                v.x * sin + v.y * cos,
                v.z
        );
    }

    // ==================== 配置屏幕 ====================

    @Override
    public InteractionResult useWithWrench(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            ClientHooks.openObjBlockConfigScreen(this);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public List<ConfigEntry<?>> getConfigs() {
        return null;
    }
}
