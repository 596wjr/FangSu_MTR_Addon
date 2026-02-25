package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.customItem.CustomItemLoader;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.userScripts.PidsScriptHolder;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import com.fangsu.utils.CollisionBoxUtil;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_PIDS;

public class BlockEntityPids extends BaseObjBlockEntity {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:pids/mtr_pids.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_pids_3a";
    private static final String MAIN_MODEL_KEY = "pids";

    protected String subModel;

    private DynamicModelHolder mainHolder, dispHolder = new DynamicModelHolder();
    private CollisionBoxUtil.CollisionBox shape;
    private Map<String, JsonElement> userExtraConfigs;

    private ScriptHolderBase scriptHolder;
    private Map<String, Map<String, Object>> loaded;
    private int texW, texH;

    public BlockEntityPids(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_PIDS.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("extraConfig", "{}");
        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        try {
            userExtraConfigs = Main.JSON_PARSER.parse(getExtraConfigOrDefault("extraConfig", "{}")).getAsJsonObject().asMap();
        } catch (Throwable ignored) {
            userExtraConfigs = new HashMap<>();
        }

        try {
            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(mainModel), "content");
            if (loaded == null || !loaded.containsKey(subModel)) {
                markedError = true;
                return;
            }
            Map<String, Object> current = loaded.get(subModel);
            if (current.containsKey("script")) {
                ScriptManager manager = ScriptManager.getInstance();
                ResourceLocation location = new ResourceLocation((String) current.get("script"));
                manager.initHolder(location, PidsScriptHolder::new);
                scriptHolder = manager.getHolder(location);
            }
            boolean flipV = current.containsKey("flipV") && (boolean) current.get("flipV");
            String model = (String) current.get("model");
            mainHolder = ResourceUtil.loadDmh(new ResourceLocation(model), flipV);
            if (current.containsKey("slots") && current.get("slots") instanceof List<?> slotsList) {
                RawMeshBuilder builder = new RawMeshBuilder(4, "light", new ResourceLocation("fangsu:pids/black.png"));
                for (Object slot : slotsList) {
                    if (!(slot instanceof List<?> currentSlot)) continue;
                    List<List<Double>> finalList = new ArrayList<>();
                    for (Object o : currentSlot) {
                        if (!(o instanceof List<?> a)) continue;
                        List<Double> temp = new ArrayList<>();
                        for (Object o2 : a) {
                            if (!(o2 instanceof Number)) continue;
                            temp.add(((Number) o2).doubleValue());
                        }
                        if (temp.size() == 4) finalList.add(temp);
                    }
                    addQuad(builder, finalList, false);
                }
                RawModel dispRawModel = new RawModel();
                dispRawModel.append(builder.getMesh());
                dispRawModel.generateNormals();
                dispHolder.uploadLater(dispRawModel);
            }
            if (current.containsKey("texSize") && current.get("texSize") instanceof List<?> l) {
                if (l.size() > 0) texW = (int) l.get(0);
                else texW = 128;
                if (l.size() > 1) texH = (int) l.get(1);
                else texH = 128;
            }
        } catch (Exception e) {
            markedError = true;
        }
    }

    @Override
    public void whenRendering() {
        ObjBlockScriptContext ctx = this.scriptContext;
        if (mainHolder != null) ctx.drawModel(mainHolder, null);
        if (dispHolder != null) ctx.drawModel(dispHolder, null);
    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {

    }

    @Override
    public InteractionResult whenUseWithinBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public String getMainModelKey() {
        return MAIN_MODEL_KEY;
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;
        long posLong = worldPosition.asLong();

        VoxelShape finalShape = Shapes.empty();
        if (shape != null) {
            VoxelShape thisShape = CollisionBoxUtil.cachedRotatedShape(posLong, shape, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
            finalShape = Shapes.or(finalShape, thisShape.move(trans.x, trans.y, trans.z));
        }
        return finalShape;
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        return setShape(state);
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        List<ModelSelectInfo> thisInfo = new ArrayList<>();
        try {
            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(this.mainModel), "common");
            for (String key : loaded.keySet()) {
                Map<String, Object> item = loaded.get(key);
                String text = "";
                String content = "";
                String contentText = null;
                if (item.containsKey("text") && item.get("text") instanceof String s) text = s;
                if (item.containsKey("id") && item.get("id") instanceof String s) content = s;
                if (item.containsKey("contentText") && item.get("contentText") instanceof String s) contentText = s;
                if (contentText != null) thisInfo.add(new ModelSelectInfo(text, content, contentText));
                else thisInfo.add(new ModelSelectInfo(text, content));
            }
        } catch (Exception ignored) {
        }
        infos.add(new SubModelDispInfo(
                Component.translatable("ui.fangsu.block.subModelSelect"),
                thisInfo,
                (be) -> this.subModels.getOrDefault("subModel", DEFAULT_SUB_MODEL),
                (be, v) -> this.subModels.put("subModel", v)));
        return infos;
    }

    private void addQuad(RawMeshBuilder builder, List<List<Double>> quad, boolean reverse) {
        float[] normal = ModelHelper.calculateNormal(quad.get(0), quad.get(1), quad.get(2));

        // 如果需要反转法向（比如背面）
        if (reverse) {
            normal[0] *= -1;
            normal[1] *= -1;
            normal[2] *= -1;
        }

        builder.vertex(quad.get(0).get(0), quad.get(0).get(1), quad.get(0).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(0, 0).endVertex()
                .vertex(quad.get(1).get(0), quad.get(1).get(1), quad.get(1).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(0, 1).endVertex()
                .vertex(quad.get(2).get(0), quad.get(2).get(1), quad.get(2).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(1, 1).endVertex()
                .vertex(quad.get(3).get(0), quad.get(3).get(1), quad.get(3).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(1, 0).endVertex();
    }

    private static Vec3 transformOffset(Direction facing, Vec3 trans) {
        return switch (facing) {
            case NORTH -> new Vec3(trans.x, trans.y, -trans.z);
            case SOUTH -> new Vec3(-trans.x, trans.y, trans.z);
            case WEST -> new Vec3(trans.z, trans.y, -trans.x);
            case EAST -> new Vec3(-trans.z, trans.y, trans.x);
            default -> trans;
        };
    }
}
