package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.CustomItems;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.network.ModNetwork;
import com.fangsu.render.scripting.AbstractScriptContext;
import com.fangsu.render.scripting.eyecandy.EyeCandyDrawCalls;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.render.sowcerext.model.ModelCluster;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

public abstract class BaseObjBlockEntity extends BlockEntity implements Syncable {
    private ObjBlockProperty property;
    public ObjBlockScriptContext scriptContext;

    public float translateX = 0, translateY = 0, translateZ = 0;
    public float rotateX = 0, rotateY = 0, rotateZ = 0;

    public boolean fullLight = false;

    public String mainModel;
    public Map<String, String> subModels = new ConcurrentHashMap<>();

    protected boolean markedError = false;

    Map<String, String> extraConfigs = new ConcurrentHashMap<>();
    private boolean disposed = false;

    public BaseObjBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
        property = new ObjBlockProperty();
        scriptContext = new ObjBlockScriptContext(this);
    }

    @Override
    public final void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);

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
    public final void load(@NotNull CompoundTag tag) {
        super.load(tag);

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
            subModels.clear();
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

    public abstract void whenLoading();

    /**
     * Called when this block entity is being disposed (e.g. chunk unload / removal).
     */
    public void whenDisposing() {
    }

    public abstract void whenRendering();

    public void whenSaving(Map<String, String> extraConfigs) {
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
            String value = buf.readUtf(1024);
            extraConfigs.put(key, value);
        }

        size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(1024);
            subModels.put(key, value);
        }

        // 閲嶆柊鍔犺浇閰嶇疆鍒板瓧娈碉紙纭繚 isolation/doorOpenOverride 绛夊悓姝ワ級
        this.whenLoading();
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

    void syncToServer() {
        if (level == null || level.isClientSide) {
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

    public final InteractionResult useWithWrench(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            ClientHooks.openObjBlockConfigScreen(this);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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

    public Vec3 worldToLocal(Vec3 worldPos) {
        Level level = this.getLevel();
        if (level == null) return Vec3.ZERO;

        BlockPos pos = this.getBlockPos();
        Direction facing = level.getBlockState(pos)
                .getValue(BaseObjBlock.FACING);

        // 1. 涓栫晫鍧愭爣 锟?纰版挒绠卞師锟?
        Vec3 v = worldPos.subtract(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5
        );

        // 2. 鍙嶅悜楂樼骇骞崇Щ
        v = v.subtract(
                this.translateX,
                this.translateY,
                this.translateZ
        );

        // 3. 鍙嶅悜楂樼骇鏃嬭浆锛堥『搴忓繀椤诲拰娓叉煋鐩稿弽锟?
        v = rotateZ(v, -this.rotateZ);
        v = rotateY(v, -this.rotateY);
        v = rotateX(v, -this.rotateX);

        // 4. 鍙嶅悜鏂瑰潡鏈濆悜
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
        if (!disposed) {
            disposed = true;
            whenDisposing();
        }
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
