package com.fangsu.blockEntities;


//#if FABRIC

import fabric.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.AbstractScriptContext;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.ScriptHolder;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.eyecandy.EyeCandyDrawCalls;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
import fabric.cn.zbx1425.sowcer.math.Matrices;
import fabric.cn.zbx1425.sowcer.math.Matrix4f;
import fabric.cn.zbx1425.sowcerext.model.ModelCluster;
//#elseif FORGE
//$$ import forge.cn.zbx1425.sowcerext.model.ModelCluster;
//$$ import forge.cn.zbx1425.mtrsteamloco.render.scripting.eyecandy.EyeCandyScriptContext;
//$$ import forge.cn.zbx1425.mtrsteamloco.render.scripting.ScriptHolder;
//#endif
import mtr.mappings.BlockEntityClientSerializableMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseObjBlockEntity extends BlockEntityClientSerializableMapper {
    private ObjBlockProperty property;
    public ObjBlockScriptContext scriptContext;

    public float translateX = 0, translateY = 0, translateZ = 0;
    public float rotateX = 0, rotateY = 0, rotateZ = 0;

    public boolean fullLight = false;

    public String mainModel;
    public Map<String, String> subModels;

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

    public ObjBlockProperty getProperty() {
        return property;
    }

    public BlockPos getWorldPos() {
        return this.worldPosition;
    }

    public VoxelShape setCollisionShape() {
        return Block.box(0, 0, 0, 0, 0, 0);
    }

    public VoxelShape setShape() {
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

    public VoxelShape getShapeInternal() {
        return setShape(); // 你已有的逻辑
    }

    public VoxelShape getCollisionShapeInternal() {
        return setCollisionShape();
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

}
