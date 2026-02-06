package com.fangsu.blockEntities;

import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
//#if FABRIC

import fabric.cn.zbx1425.mtrsteamloco.render.scripting.AbstractScriptContext;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.ScriptHolder;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.eyecandy.EyeCandyDrawCalls;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
import fabric.cn.zbx1425.sowcer.math.Matrices;
import fabric.cn.zbx1425.sowcer.math.Matrix4f;
import fabric.cn.zbx1425.sowcerext.model.ModelCluster;
//#elseif FORGE
//$$ import forge.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy;
//$$ import forge.cn.zbx1425.mtrsteamloco.render.scripting.AbstractScriptContext;
//$$ import forge.cn.zbx1425.mtrsteamloco.render.scripting.ScriptHolder;
//$$ import forge.cn.zbx1425.mtrsteamloco.render.scripting.eyecandy.EyeCandyDrawCalls;
//$$ import forge.cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
//$$ import forge.cn.zbx1425.sowcer.math.Matrices;
//$$ import forge.cn.zbx1425.sowcer.math.Matrix4f;
//$$ import forge.cn.zbx1425.sowcerext.model.ModelCluster;
//#endif
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.network.ModNetwork;
import com.fangsu.ui.ObjBlockConfigScreen;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.mappings.BlockEntityClientSerializableMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseObjBlockEntity extends BlockEntityClientSerializableMapper implements Syncable {
    private ObjBlockProperty property;
    public ObjBlockScriptContext scriptContext;

    public float translateX = 0, translateY = 0, translateZ = 0;
    public float rotateX = 0, rotateY = 0, rotateZ = 0;

    public boolean fullLight = false;

    public String mainModel;
    public Map<String, String> subModels = new HashMap<>();

    Map<String, String> extraConfigs = new HashMap<>();

    public BaseObjBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
        property = new ObjBlockProperty();
        scriptContext = new ObjBlockScriptContext(this);
    }

    @Override
    public void writeCompoundTag(@NotNull CompoundTag tag) {
//        super.saveAdditional(tag);

        tag.putBoolean("fullLight", fullLight);

        tag.putFloat("translateX", translateX);
        tag.putFloat("translateY", translateY);
        tag.putFloat("translateZ", translateZ);
        tag.putFloat("rotateX", rotateX);
        tag.putFloat("rotateY", rotateY);
        tag.putFloat("rotateZ", rotateZ);

        if (mainModel != null) tag.putString("mainModel", mainModel);
        if (subModels != null) {
            CompoundTag subModelTag = new CompoundTag();
            for (String key : subModels.keySet()) {
                subModelTag.putString(key, subModels.get(key));
            }
            tag.put("subModel", subModelTag);
        }

        this.whenSaving(extraConfigs);

        if (extraConfigs != null) {
            CompoundTag subConfigTag = new CompoundTag();
            for (String key : extraConfigs.keySet()) {
                subConfigTag.putString(key, extraConfigs.get(key));
            }
            tag.put("extraConfig", subConfigTag);
        }

    }

    @Override
    public void readCompoundTag(@NotNull CompoundTag tag) {
//        super.load(tag);

        fullLight = tag.getBoolean("fullLight");

        translateX = tag.contains("translateX") ? tag.getFloat("translateX") : 0;
        translateY = tag.contains("translateY") ? tag.getFloat("translateY") : 0;
        translateZ = tag.contains("translateZ") ? tag.getFloat("translateZ") : 0;
        rotateX = tag.contains("rotateX") ? tag.getFloat("rotateX") : 0;
        rotateY = tag.contains("rotateY") ? tag.getFloat("rotateY") : 0;
        rotateZ = tag.contains("rotateZ") ? tag.getFloat("rotateZ") : 0;

        mainModel = tag.contains("mainModel") ? tag.getString("mainModel") : null;
        if (tag.contains("subModel")) {
            CompoundTag subModelTag = tag.getCompound("subModel");
            subModels = new HashMap<>();
            for (String key : subModelTag.getAllKeys()) {
                subModels.put(key, subModelTag.getString(key));
            }
        }
        extraConfigs.clear();
        if (tag.contains("extraConfig")) {
            CompoundTag subConfigTag = tag.getCompound("extraConfig");
            for (String key : subConfigTag.getAllKeys()) {
                extraConfigs.put(key, subConfigTag.getString(key));
            }
        }

        this.whenLoading();
    }

    public String getExtraConfig(String key) {
        return extraConfigs.get(key);
    }

    public String getExtraConfigOrDefault(String key, String defaultValue) {
        return extraConfigs.getOrDefault(key, defaultValue);
    }

    public void setExtraConfig(String key, String value) {
        extraConfigs.put(key, value);
    }

    public void ensureExtraConfig(String key, String value) {
        extraConfigs.putIfAbsent(key, value);
    }

    public boolean getExtraConfigBool(String key, boolean defaultValue) {
        String value = extraConfigs.get(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value);
    }

    public int getExtraConfigInt(String key, int defaultValue) {
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

    public float getExtraConfigFloat(String key, float defaultValue) {
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

    public ObjBlockProperty getProperty() {
        return property;
    }

    public BlockPos getWorldPos() {
        return this.worldPosition;
    }

    public VoxelShape setCollisionShape(BlockState state) {
        return Block.box(0, 0, 0, 0, 0, 0);
    }

    public VoxelShape setShape(BlockState state) {
        return Block.box(0, 0, 0, 16, 16, 16);
    }

    public static class ObjBlockProperty {
        public Component name;
        public ModelCluster model;
        public ScriptHolder script;

        public ObjBlockProperty() {
        }

        public ObjBlockProperty(Component name, ModelCluster model) {
            this.name = name;
            this.model = model;
        }

//        public ObjBlockProperty(Component name, ModelCluster model, ScriptHolder script) {
//            this.name = name;
//            this.model = model;
//            this.script = script;
//        }
    }

    public static class ObjBlockScriptContext extends AbstractScriptContext {
        public BaseObjBlockEntity entity;
        public EyeCandyDrawCalls scriptResult = new EyeCandyDrawCalls();
        private EyeCandyDrawCalls scriptResultWriting = new EyeCandyDrawCalls();
        public boolean disposeForReload = false;

        public ObjBlockScriptContext(BaseObjBlockEntity entity) {
            this.entity = entity;
        }

        public void renderFunctionFinished() {
            synchronized (this) {
                EyeCandyDrawCalls temp = this.scriptResultWriting;
                this.scriptResultWriting = this.scriptResult;
                this.scriptResult = temp;
                this.scriptResultWriting.reset();
            }
        }

        public Object getWrapperObject() {
            return this.entity;
        }

        public boolean isBearerAlive() {
            return !this.disposeForReload && !this.entity.isRemoved();
        }

        public void drawModel(ModelCluster model, Matrices poseStack) {
            this.scriptResultWriting.addModel(model, poseStack == null ? Matrix4f.IDENTITY : poseStack.last().copy());
        }

        public void drawModel(DynamicModelHolder model, Matrices poseStack) {
            this.scriptResultWriting.addModel(model, poseStack == null ? Matrix4f.IDENTITY : poseStack.last().copy());
        }

        public void playSound(ResourceLocation sound, float volume, float pitch) {
            this.scriptResultWriting.addSound(SoundEvent.createVariableRangeEvent(sound), volume, pitch);
        }
    }

    public abstract void whenLoading();

    public abstract void whenRendering();

    public abstract void whenSaving(Map<String, String> extraConfigs);

    public abstract InteractionResult whenUseWithinBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit);

    public void whenEntityInside(Player player) {
    }

    public void serverTick() {
    }

    public VoxelShape getShapeInternal(BlockState state) {
        return setShape(state); // 你已有的逻辑
    }

    public VoxelShape getCollisionShapeInternal(BlockState state) {
        return setCollisionShape(state);
    }

    public List<ConfigEntry<?>> getConfigs() {
        return null;
    }

    @Override
    public void writeC2S(FriendlyByteBuf buf) {
        buf.writeFloat(translateX);
        buf.writeFloat(translateY);
        buf.writeFloat(translateZ);
        buf.writeFloat(rotateX);
        buf.writeFloat(rotateY);
        buf.writeFloat(rotateZ);
        buf.writeUtf(mainModel);
        buf.writeInt(extraConfigs.size());
        for (String key : extraConfigs.keySet()) {
            String value = extraConfigs.get(key);
            buf.writeUtf(key);
            buf.writeUtf(value);
        }
        buf.writeInt(subModels.size());
        for (String key : subModels.keySet()) {
            String value = subModels.get(key);
            buf.writeUtf(key);
            buf.writeUtf(value);
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
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(128);
            extraConfigs.put(key, value);
        }

        size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(128);
            subModels.put(key, value);
        }
        if (level != null && level.isClientSide == false) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    3
            );
        }

    }

    public abstract String getMainModelKey();

    void syncToServer() {
        if (level == null || level.isClientSide) {
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

    public final InteractionResult useWithBrush(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            Minecraft.getInstance().setScreen(new ObjBlockConfigScreen(this));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public List<SubModelDispInfo> getSubModelInfos() {
        return null;
    }

    protected void markShapeDirty() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(
                    worldPosition,
                    state,
                    state,
                    Block.UPDATE_ALL
            );
        }
    }

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

    public record BaseObjC2SData(
            float translateX,
            float translateY,
            float translateZ,
            float rotateX,
            float rotateY,
            float rotateZ,
            Map<String, String> extraConfigs
    ) {
    }

}
