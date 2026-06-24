package com.fangsu.blockEntities;

import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.blocks.BlockScreendoorCentralControl;
import com.fangsu.client.ClientHooks;
import com.fangsu.network.ModNetwork;
import com.fangsu.utils.FacingBlockUtil;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_SCREENDOOR_CENTRAL_CONTROL;

/**
 * 屏蔽门集控方块实体
 * 存储需要控制的屏蔽门坐标列表以及隔离/门控状态
 */
public class BlockEntityScreendoorCentralControl extends BlockEntity {

    /** 需要控制的所有屏蔽门（门 / 玻璃）的坐标 */
    private final List<BlockPos> doorPositions = new ArrayList<>();

    /** 门隔离状态 */
    private boolean isolation = false;

    /** 门开启状态（仅在隔离打开时有效） */
    private boolean doorOpen = false;

    /** 需要保存的起始坐标列表 */
    private final List<BlockPos> startPositions = new ArrayList<>();

    /** 延迟重扫描标记（世界加载时区块未就绪，等第一次Tick时再扫描） */
    private boolean needsRescan = false;
    /** 标记NBT已加载，setLevel时执行初始化 */
    private boolean loadedFromNbt = false;

    public BlockEntityScreendoorCentralControl(BlockPos pos, BlockState state) {
        super(BLOCK_ENTITY_SCREENDOOR_CENTRAL_CONTROL.get(), pos, state);
    }

    @Override
    public void setLevel(@NotNull Level level) {
        super.setLevel(level);
        // load() 调用时 level == null，needsRescan 无法设置
        // 在这里设置标记，使得首个服务端 Tick 能执行重新扫描和状态应用
        if (!level.isClientSide && loadedFromNbt) {
            needsRescan = true;
        }
    }

    // ======================== NBT ========================

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putBoolean("isolation", isolation);
        tag.putBoolean("doorOpen", doorOpen);

        // 保存起始坐标
        tag.putInt("startPosCount", startPositions.size());
        for (int i = 0; i < startPositions.size(); i++) {
            BlockPos p = startPositions.get(i);
            tag.putInt("startX_" + i, p.getX());
            tag.putInt("startY_" + i, p.getY());
            tag.putInt("startZ_" + i, p.getZ());
        }

        // 保存已扫描到的门坐标（重进世界时区块可能未加载，需从NBT恢复）
        tag.putInt("doorPosCount", doorPositions.size());
        for (int i = 0; i < doorPositions.size(); i++) {
            BlockPos p = doorPositions.get(i);
            tag.putInt("doorX_" + i, p.getX());
            tag.putInt("doorY_" + i, p.getY());
            tag.putInt("doorZ_" + i, p.getZ());
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);

        isolation = tag.getBoolean("isolation");
        doorOpen = tag.getBoolean("doorOpen");

        startPositions.clear();
        int count = tag.getInt("startPosCount");
        for (int i = 0; i < count; i++) {
            int x = tag.getInt("startX_" + i);
            int y = tag.getInt("startY_" + i);
            int z = tag.getInt("startZ_" + i);
            startPositions.add(new BlockPos(x, y, z));
        }

        // 从NBT恢复上次扫描到的门坐标（区块可能未加载，先恢复列表以备GUI显示等用途）
        doorPositions.clear();
        int doorCount = tag.getInt("doorPosCount");
        for (int i = 0; i < doorCount; i++) {
            int x = tag.getInt("doorX_" + i);
            int y = tag.getInt("doorY_" + i);
            int z = tag.getInt("doorZ_" + i);
            doorPositions.add(new BlockPos(x, y, z));
        }

        // 更新指示灯
        updateLightState();

        // 注意：load() 调用时 level 始终为 null（区块加载后才 setLevel）
        // 初始化标记在 setLevel() 中触发
        loadedFromNbt = true;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    // ======================== 网络同步 ========================

    /** 发送数据到服务端 */
    public void syncToServer() {
        if (level == null || !level.isClientSide) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(getBlockPos());

        // 模式: 0=更新状态, 1=更新坐标列表
        buf.writeInt(0);
        buf.writeBoolean(isolation);
        buf.writeBoolean(doorOpen);

        NetworkManager.sendToServer(ModNetwork.CENTRAL_CONTROL_SYNC, buf);
    }

    /** 发送坐标列表到服务端 */
    public void syncPositionsToServer() {
        if (level == null || !level.isClientSide) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(getBlockPos());

        buf.writeInt(1); // 模式: 更新坐标列表
        buf.writeInt(startPositions.size());
        for (BlockPos p : startPositions) {
            buf.writeBlockPos(p);
        }

        NetworkManager.sendToServer(ModNetwork.CENTRAL_CONTROL_SYNC, buf);
    }

    /** 服务端处理同步 */
    public void readSync(FriendlyByteBuf buf) {
        int mode = buf.readInt();
        if (mode == 0) {
            // 更新状态
            isolation = buf.readBoolean();
            doorOpen = buf.readBoolean();

            // 更新指示灯
            updateLightState();

            // 应用到所有屏蔽门
            applyToAllDoors();

            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                setChanged();
            }
        } else if (mode == 1) {
            // 更新坐标列表
            startPositions.clear();
            int count = buf.readInt();
            for (int i = 0; i < count; i++) {
                startPositions.add(buf.readBlockPos());
            }

            // 重新扫描屏蔽门
            scanDoors();

            // 应用当前状态
            applyToAllDoors();

            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                setChanged();
            }
        }
    }

    // ======================== 核心逻辑 ========================

    /**
     * 从起始坐标出发，沿左/右方向扫描一排屏蔽门
     * 仅允许一条直线，遇到非屏蔽门（门或玻璃）方块就断开
     */
    public void scanDoors() {
        doorPositions.clear();
        if (level == null || startPositions.isEmpty()) return;

        for (BlockPos startPos : startPositions) {
            Set<BlockPos> scanned = new HashSet<>();
            BlockState state = level.getBlockState(startPos);

            // 检查起始点是否就是屏蔽门
            if (isScreendoorBlock(level, startPos)) {
                scanned.add(startPos);
            }

            if (!state.hasProperty(BaseObjBlock.FACING)) continue;

            // 向左扫描
            scanLine(startPos, state, true, scanned);
            // 向右扫描
            scanLine(startPos, state, false, scanned);

            doorPositions.addAll(scanned);
        }
    }

    private void scanLine(BlockPos startPos, BlockState state, boolean left, Set<BlockPos> result) {
        BlockPos current = startPos;
        BlockPos next;
        int maxScan = 64; // 防止死循环

        for (int i = 0; i < maxScan; i++) {
            if (left) {
                next = FacingBlockUtil.getLeftPos(current, state);
            } else {
                next = FacingBlockUtil.getRightPos(current, state);
            }

            if (!isScreendoorBlock(level, next)) break;
            if (result.contains(next)) break;

            result.add(next);
            current = next;
            // 更新 state 以匹配新位置的朝向（新位置可能也有朝向）
            BlockState nextState = level.getBlockState(next);
            if (nextState.hasProperty(BaseObjBlock.FACING)) {
                state = nextState;
            } else {
                break;
            }
        }
    }

    private static boolean isScreendoorBlock(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof BlockEntityScreendoor || be instanceof BlockEntityScreendoorGlass;
    }

    /**
     * 将当前隔离/开门状态应用到所有已扫描的屏蔽门上
     */
    public void applyToAllDoors() {
        if (level == null || level.isClientSide) return;

        for (BlockPos pos : doorPositions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BlockEntityScreendoor door) {
                door.setCentralLocked(isolation);
                door.setLocalIsolation(isolation);
                door.setLocalDoorOpenOverride(isolation && doorOpen);

                // 保存到 extraConfigs 使其持久化并同步到客户端
                door.setExtraConfig("isolation", isolation ? "true" : "false");
                if (isolation) {
                    door.setExtraConfig("doorOpenOverride", doorOpen ? "true" : "false");
                    door.setExtraConfig("doorTarget", doorOpen ? "true" : "false");
                } else {
                    door.setExtraConfig("doorOpenOverride", "false");
                    door.setExtraConfig("doorTarget", "false");
                    // 解除隔离时，直接重置 transient 字段，确保渲染立即反映关闭状态
                    door.resetDoorState();
                }

                door.setChanged();
                level.sendBlockUpdated(pos, door.getBlockState(), door.getBlockState(), 3);
            }
        }

        // 通知所有屏蔽门上方的吊板更新门灯
        updateAllDoorLights();
    }

    /**
     * 更新屏蔽门上方的吊板门灯
     */
    private void updateDoorLightAbove(BlockEntityScreendoorGlass glass) {
        if (level == null) return;
        BlockPos above = glass.getBlockPos().above();
        // 检查上方两格内的吊板
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos checkPos = glass.getBlockPos().offset(0, dy, 0);
            BlockEntity be = level.getBlockEntity(checkPos);
            if (be instanceof BlockEntityDiaoban diaoban) {
                if (isolation && doorOpen) {
                    diaoban.setDoorTarget(true);
                    diaoban.setDoorValue(1.0f);
                } else {
                    diaoban.setDoorTarget(false);
                    diaoban.setDoorValue(0f);
                }
                diaoban.setChanged();
                level.sendBlockUpdated(checkPos, diaoban.getBlockState(), diaoban.getBlockState(), 3);
            }
        }
    }

    private void updateAllDoorLights() {
        if (level == null) return;
        // 遍历所有已扫描的屏蔽门位置，检查上方两格内的吊板
        Set<BlockPos> updated = new HashSet<>();
        for (BlockPos pos : doorPositions) {
            for (int dy = 1; dy <= 2; dy++) {
                BlockPos checkPos = pos.offset(0, dy, 0);
                if (updated.contains(checkPos)) continue;
                BlockEntity be = level.getBlockEntity(checkPos);
                if (be instanceof BlockEntityDiaoban diaoban) {
                    updated.add(checkPos);
                    boolean newTarget = isolation && doorOpen;
                    float newValue = newTarget ? 1.0f : 0f;
                    // 仅当状态有变化时才发送更新，避免频繁触发客户端重绘导致ogl1282
                    if (diaoban.getDoorTarget() != newTarget || diaoban.getDoorValue() != newValue) {
                        diaoban.setDoorTarget(newTarget);
                        diaoban.setDoorValue(newValue);
                        diaoban.setChanged();
                        level.sendBlockUpdated(checkPos, diaoban.getBlockState(), diaoban.getBlockState(), 3);
                    }
                }
            }
        }
    }

    // ======================== 灯状态 ========================

    /**
     * 根据 isolation / doorOpen 更新方块状态中的 LIGHT_1 / LIGHT_2 属性
     * light_1 = isolation（隔离开启时1号灯亮）
     * light_2 = isolation && doorOpen（门开启时3号灯亮, 门关闭时2号灯亮 - 通过不同模型区分）
     */
    public void updateLightState() {
        if (level == null) return;
        BlockState state = level.getBlockState(worldPosition);
        if (!state.hasProperty(BlockScreendoorCentralControl.LIGHT_1)) return;

        BlockState newState = state
                .setValue(BlockScreendoorCentralControl.LIGHT_1, isolation)
                .setValue(BlockScreendoorCentralControl.LIGHT_2, isolation && doorOpen);

        if (newState != state) {
            level.setBlock(worldPosition, newState, 3);
        }
    }

    // ======================== 客户端 ========================

    // ======================== 延迟Tick ========================

    /**
     * 服务端Tick调用，执行延迟的重新扫描和状态应用
     * 会持续重试直到扫描到门为止，由 {@link BlockScreendoorCentralControl#getTicker} 在服务端驱动
     */
    public void tickServer() {
        if (!needsRescan) return;
        if (level == null || level.isClientSide) return;

        // 保存重进世界时从NBT恢复的旧坐标列表
        List<BlockPos> oldPositions = new ArrayList<>(doorPositions);

        // 重新扫描
        scanDoors();

        if (doorPositions.isEmpty() && !oldPositions.isEmpty()) {
            // 扫描失败（区块未加载），恢复旧坐标并保留 needsRescan 下次重试
            doorPositions.addAll(oldPositions);
            // needsRescan 保持 true，下一个 tick 继续尝试
        } else {
            // 扫描成功，应用状态到新扫描到的门
            needsRescan = false;
            applyToAllDoors();
        }
    }

    // ======================== 客户端 ========================

    /** 客户端打开配置界面 */
    public void openScreen() {
        ClientHooks.OPEN_SCREENDOOR_CENTRAL_CONTROL_SCREEN.accept(this);
    }

    // ======================== Getter / Setter ========================

    public boolean isIsolation() {
        return isolation;
    }

    public void setIsolation(boolean isolation) {
        this.isolation = isolation;
    }

    public boolean isDoorOpen() {
        return doorOpen;
    }

    public void setDoorOpen(boolean doorOpen) {
        this.doorOpen = doorOpen;
    }

    public List<BlockPos> getStartPositions() {
        return startPositions;
    }

    public List<BlockPos> getDoorPositions() {
        return doorPositions;
    }

    @Override
    public void setRemoved() {
        // 方块被破坏或世界卸载时，解除所有门的集控状态
        // 注意：世界卸载时不可调用 applyToAllDoors()（会触发区块加载导致死锁）
        if (level != null && !level.isClientSide && !doorPositions.isEmpty()) {
            boolean prevIsolation = isolation;
            isolation = false;
            doorOpen = false;
            // 仅对已加载区块中的门解除集控，避免世界卸载时触发区块加载导致死锁
            for (BlockPos pos : doorPositions) {
                if (!level.isLoaded(pos)) continue;
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof BlockEntityScreendoor door) {
                    door.setCentralLocked(false);
                    door.setLocalIsolation(false);
                    door.setLocalDoorOpenOverride(false);
                    door.setExtraConfig("isolation", "false");
                    door.setExtraConfig("doorOpenOverride", "false");
                    door.setExtraConfig("doorTarget", "false");
                    door.resetDoorState();
                    door.setChanged();
                    level.sendBlockUpdated(pos, door.getBlockState(), door.getBlockState(), 3);
                }
            }
            isolation = prevIsolation;
        }
        super.setRemoved();
    }
}
