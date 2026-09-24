package com.fangsu.blockEntities;

import com.fangsu.Main;
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

//        public ObjBlockProperty(LocalComponent name, ModelCluster model, ScriptHolder script) {
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
            this.scriptResultWriting.addModel(model, poseStack == null ? Matrix4f.IDENTITY : poseStack.last());
        }

        public void drawModel(DynamicModelHolder model, Matrices poseStack) {
            if (model == null || model.getUploadedModel() == null || model.getUploadedModel().isClosed()) return;
            this.scriptResultWriting.addModel(model, poseStack == null ? Matrix4f.IDENTITY : poseStack.last());
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

    // ==================== whenRendering 异步化 ====================

    /**
     * 渲染专用后台线程池，全方块实体共享的单线程。
     * <p>
     * 之前在 {@code FunctionalObjBlockEntity} 里另有一份同名的池，且非 Functional 的方块实体
     * （如 {@code BlockEntityRotatingRail}）会在渲染线程上阻塞等待该池 —— 那是一条每帧硬卡顿的路径。
     * 现在所有方块实体统一走这里的"非阻塞 + 上一帧结果兜底"模型。
     */
    private static final java.util.concurrent.ExecutorService RENDERING_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "fangsu-rendering-async");
                t.setDaemon(true);
                return t;
            });

    /**
     * 进行中的异步渲染任务。
     */
    private java.util.concurrent.CompletableFuture<Void> renderingTask;

    /**
     * 最近一次后台 whenRendering 是否已把结果填充进 scriptResultWriting。
     * 渲染线程只在为 true 时交换双缓冲，避免读到半写入内容。
     */
    private volatile boolean renderResultReady = false;

    /**
     * 是否允许提交新的后台 whenRendering。子类可覆盖（例如等待异步加载完成）。
     */
    protected boolean isRenderReady() {
        return true;
    }

    /**
     * 尝试在后台线程执行一次 whenRendering，绝不阻塞 GL 渲染线程。
     * <p>
     * 若尚未就绪、上一次 whenRendering 仍在执行、或上一次结果尚未被交换提交，
     * 则不重复提交（直接复用上一帧已就绪的结果）。
     *
     * @return 是否提交了新的后台渲染任务
     */
    public final boolean tryBeginRendering() {
        if (!isRenderReady()) return false;
        if (renderResultReady) return false;
        if (renderingTask != null && !renderingTask.isDone()) return false;
        renderResultReady = false;
        renderingTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                this.whenRendering();
            } catch (Exception e) {
                Main.LOGGER.error("Async whenRendering error for {} at {}: {}",
                        getClass().getSimpleName(), getBlockPos(), e.getMessage());
            } finally {
                renderResultReady = true;
            }
        }, RENDERING_EXECUTOR);
        return true;
    }

    /**
     * 后台 whenRendering 是否已把结果填充进 scriptResultWriting（可交换提交）。
     */
    public final boolean isRenderResultReady() {
        return renderResultReady;
    }

    /**
     * 若后台结果已就绪则消费该标记并返回 true（此时渲染线程应执行双缓冲交换），
     * 否则返回 false（复用上一帧结果，不交换，避免读到半写入缓冲）。
     */
    public final boolean consumeRenderResultIfReady() {
        if (renderResultReady) {
            renderResultReady = false;
            return true;
        }
        return false;
    }

    /**
     * 标记当帧渲染已完成，允许下一帧继续触发渲染。
     * 仅当后台任务已完成才清空引用，避免与仍在执行的 whenRendering 冲突。
     */
    public final void finishRendering() {
        if (renderingTask != null && renderingTask.isDone()) {
            renderingTask = null;
        }
    }

    /**
     * 取消进行中的异步渲染任务。在实体被移除时调用。
     */
    protected final void cancelPendingRendering() {
        if (renderingTask != null && !renderingTask.isDone()) {
            renderingTask.cancel(true);
            renderingTask = null;
        }
    }

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
        cancelPendingRendering();
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
