package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.customItem.CustomItems;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.render.scripting.AbstractScriptContext;
import com.fangsu.render.scripting.eyecandy.EyeCandyDrawCalls;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.render.sowcerext.model.ModelCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BaseObjBlockEntity extends BlockEntity {
    private ObjBlockProperty property;
    public ObjBlockScriptContext scriptContext;

    public boolean fullLight = false;

    public String mainModel;
    public Map<String, String> subModels = new ConcurrentHashMap<>();

    protected boolean markedError = false;

    protected boolean disposed = false;

    public BaseObjBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
        property = new ObjBlockProperty();
        scriptContext = new ObjBlockScriptContext(this);
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (markedError) return;

        tag.putBoolean("fullLight", fullLight);

        if (mainModel != null) tag.putString("mainModel", mainModel);
        if (subModels != null) {
            CompoundTag subModelTag = new CompoundTag();
            for (String key : subModels.keySet()) {
                String value = subModels.get(key);
                if (value != null) subModelTag.putString(key, value);
            }
            tag.put("subModel", subModelTag);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        markedError = false;

        fullLight = tag.getBoolean("fullLight");

        mainModel = tag.contains("mainModel") ? tag.getString("mainModel") : null;
        if (tag.contains("subModel")) {
            CompoundTag subModelTag = tag.getCompound("subModel");
            subModels.clear();
            for (String key : subModelTag.getAllKeys()) {
                subModels.put(key, subModelTag.getString(key));
            }
        }
    }

    public final ObjBlockProperty getProperty() {
        return property;
    }

    public final BlockPos getWorldPos() {
        return this.worldPosition;
    }

    public final Vector3f getWorldPosVector3f() {
        return new Vector3f(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }

    public VoxelShape setCollisionShape(BlockState state) {
        return Block.box(0, 0, 0, 0, 0, 0);
    }

    public VoxelShape setShape(BlockState state) {
        return Block.box(0, 0, 0, 16, 16, 16);
    }

    public boolean isMarkedError() {
        return markedError;
    }

    public static class ObjBlockProperty {
        public Component name;
        public ModelCluster model;
//        public ScriptHolder script;

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
            if (model.isClosed()) return;
            this.scriptResultWriting.addModel(model, poseStack == null ? Matrix4f.IDENTITY : poseStack.last().copy());
        }

        public void drawModel(DynamicModelHolder model, Matrices poseStack) {
            if (model == null || model.getUploadedModel() == null || model.getUploadedModel().isClosed()) return;
            this.scriptResultWriting.addModel(model, poseStack == null ? Matrix4f.IDENTITY : poseStack.last().copy());
        }

        public void playSound(ResourceLocation sound, float volume, float pitch) {
            //#if MC_VERSION >= 11903
            this.scriptResultWriting.addSound(SoundEvent.createVariableRangeEvent(sound), volume, pitch);
            //#else
            //$$ this.scriptResultWriting.addSound(new SoundEvent(sound), volume, pitch);
            //#endif
        }
    }

    public abstract void whenRendering();

    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    public InteractionResult whenUseWithOther(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    public void whenEntityInside(Player player) {
    }

    public void serverTick() {
    }

    public final VoxelShape getShapeInternal(BlockState state) {
        return setShape(state); // 浣犲凡鏈夌殑閫昏緫
    }

    public final VoxelShape getCollisionShapeInternal(BlockState state) {
        return setCollisionShape(state);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public final @NotNull CompoundTag getUpdateTag() {
        final CompoundTag compoundTag = super.getUpdateTag();
        saveAdditional(compoundTag);
        return compoundTag;
    }

    public abstract String getMainModelKey();

    public InteractionResult useWithWrench(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    public List<SubModelDispInfo> getSubModelInfos() {
        return null;
    }

    protected SubModelDispInfo createSubModelSelectInfo(String nestedKeyPath, String defaultSubModel) {
        return createSubModelSelectInfo(nestedKeyPath, "subModel", defaultSubModel);
    }

    protected SubModelDispInfo createSubModelSelectInfo(String nestedKeyPath, String subModelKey, String defaultSubModel) {
        List<ModelSelectInfo> options = getModelSelectOptions(nestedKeyPath);
        return new SubModelDispInfo(
                ComponentHelper.translatable("ui.fangsu.block.subModelSelect"),
                options,
                be -> this.subModels.getOrDefault(subModelKey, defaultSubModel),
                (be, v) -> this.subModels.put(subModelKey, v)
        );
    }

    protected List<ModelSelectInfo> getModelSelectOptions(String nestedKeyPath) {
        if (this.mainModel == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(CustomItems.getModelSelectInfos(this.mainModel, nestedKeyPath));
    }

    protected static Vec3 transformOffset(Direction facing, Vec3 trans) {
        // Keep translation in world axes so collision/outline offsets match rendering.
        // Rendering applies translate first, then facing rotation:
        //   translate(translateX, translateY, translateZ) -> rotateY(facing) -> rotateXYZ(custom)
        // so the offset itself should not be remapped by block facing.
        return trans;
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

    public void setDefaultSubModel() {
        List<SubModelDispInfo> subModelInfos = getSubModelInfos();
        if (subModelInfos == null || subModelInfos.isEmpty()) return;
        for (final SubModelDispInfo subModelInfo : subModelInfos) {
            List<ModelSelectInfo> infos = subModelInfo.infos();
            if (infos == null || infos.isEmpty()) continue;
            subModelInfo.setter().accept(this, infos.get(0).getContent());
        }
    }

    public void afterChangeModel() {
    }

    @Override
    public void setRemoved() {
        disposed = true;
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        disposed = false;
        super.clearRemoved();
    }

    public static class BlockInfo {
        private BaseObjBlockEntity entity;

        public BlockInfo(BaseObjBlockEntity entity) {
            this.entity = entity;
        }

        public Vector3f getWorldPosVector3f() {
            return entity.getWorldPosVector3f();
        }


    }

}
