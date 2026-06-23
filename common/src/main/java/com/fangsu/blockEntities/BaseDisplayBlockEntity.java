package com.fangsu.blockEntities;

import com.fangsu.mappings.GsonHelper;
import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.shape.RawShape;
import com.fangsu.shape.RotatableShapeHelper;
import com.fangsu.shape.ShapeCollection;
import com.fangsu.utils.GraphicsTextureHelper;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统⼀的显示类方块实体基类，封装了 ris、sis、diaoban 共享的绘制初始化、形状管理和渲染生命周期。
 * <p>
 * 子类只需关注：
 * <ul>
 *   <li>在 {@link #whenLoading()} 中调用父类通用方法并加载特有数据</li>
 *   <li>在 {@link #whenRendering()} 中绘制主模型后调用 {@link #renderDisplayModel(ObjBlockScriptContext)}</li>
 * </ul>
 */
public abstract class BaseDisplayBlockEntity extends BaseObjBlockEntity {

    // ==================== 通用状态管理 ====================

    /**
     * 内容/模型是否已加载完成
     */
    protected boolean firstInit = false;
    /**
     * 绘制是否已注册完成
     */
    protected boolean scriptDone = false;
    /**
     * 上一次注册的绘制标识，用于去重
     */
    protected String lastRegisteredDrawInfoId = "";
    /**
     * 绘制状态（传递给 draw 回调）
     */
    protected Map<String, Object> drawState = new HashMap<>();
    /**
     * ⽤⼾⾃定义额外配置（来⾃ content JSON 的 extraConfig）
     */
    protected Map<String, JsonElement> userExtraConfigs = new HashMap<>();

    // ==================== 重试节流 ====================

    /** 重试间隔（毫秒），避免数据未就绪时每帧都重试 */
    private static final long RETRY_INTERVAL_MS = 200;

    /** 上次重试初始化的时间戳 */
    private long lastRetryTime = 0;

    /**
     * 检查是否应该重试初始化（受 RETRY_INTERVAL 节流）。
     * 避免数据未就绪时每帧都执行昂贵的重试操作。
     */
    protected boolean shouldRetryInit() {
        long now = System.currentTimeMillis();
        if (now - lastRetryTime >= RETRY_INTERVAL_MS) {
            lastRetryTime = now;
            return true;
        }
        return false;
    }

    /**
     * 重置重试计时器（在配置变更等明确需要立即重试时调用）。
     */
    protected void resetRetryTimer() {
        lastRetryTime = 0;
    }

    // ==================== 显示纹理 ====================

    /**
     * 显⽰⾯（屏幕）模型
     */
    protected DynamicModelHolder dmhDisp = new DynamicModelHolder();
    /**
     * 显⽰纹理宽度
     */
    protected int texW;
    /**
     * 显⽰纹理⾼度
     */
    protected int texH;

    // ==================== 形状 ====================

    /**
     * 碰撞箱 / 外形形状集合
     */
    protected ShapeCollection shape;

    public BaseDisplayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ================================================================
    //  通用构建方法
    // ================================================================

    /**
     * 从插槽定义构建显示面模型（RIS / SIS 通用）。
     *
     * @param slots       插槽顶点数组列表
     * @param texturePath 占位纹理路径（如 "fangsu:pids/black.png"）
     */
    protected void buildDisplayFromSlots(float[][][] slots, String texturePath) {
        RawMeshBuilder builder = new RawMeshBuilder(4, "exterior", new ResourceLocation(texturePath));
        for (float[][] slot : slots) {
            ModelHelper.addQuad(builder, slot, false);
        }
        RawModel rawModel = new RawModel();
        rawModel.append(builder.getMesh());
        rawModel.generateNormals();
        dmhDisp.uploadLater(rawModel);
    }

    /**
     * 从像素坐标数组（float[][6]，值域 0~16）构建形状集合。
     *
     * @param shapeData 形状数组，每个元素为 [x1, y1, z1, x2, y2, z2]
     * @return 形状集合，若数据无效返回 {@code null}
     */
    @Nullable
    protected static ShapeCollection buildShapeFromArray(float[][] shapeData) {
        if (shapeData == null || shapeData.length == 0) {
            return null;
        }
        ShapeCollection col = new ShapeCollection();
        for (float[] s : shapeData) {
            if (s == null || s.length < 6) {
                continue;
            }
            col.add(new RawShape(
                    s[0] / 16.0, s[1] / 16.0, s[2] / 16.0,
                    s[3] / 16.0, s[4] / 16.0, s[5] / 16.0
            ));
        }
        return col.isEmpty() ? null : col;
    }

    /**
     * 从单个 Box 的 Double 列表构建形状集合（Diaoban 用）。
     *
     * @param data 6 个元素的列表 [x1, y1, z1, x2, y2, z2]，像素坐标 0~16
     * @return 形状集合，若数据无效返回空集合
     */
    protected static ShapeCollection buildShapeFromList(List<Double> data) {
        ShapeCollection col = new ShapeCollection();
        if (data == null || data.size() < 6) {
            return col;
        }
        double[] box = new double[6];
        for (int i = 0; i < 6; i++) {
            box[i] = data.get(i) / 16.0;
        }
        col.add(new RawShape(box));
        return col;
    }

    /**
     * 解析⽤⼾⾃定义额外配置 JSON 字符串。
     *
     * @param json JSON 字符串
     */
    protected void parseUserExtraConfigs(String json) {
        try {
            userExtraConfigs = GsonHelper.asMap(Main.JSON_PARSER.parse(json).getAsJsonObject());
        } catch (Throwable ignored) {
            userExtraConfigs = new HashMap<>();
        }
    }

    // ================================================================
    //  通用绘制注册模板
    // ================================================================

    /**
     * 尝试注册绘制回调到 {@link GraphicsTextureHelper}。
     * <p>
     * 内含去重逻辑：若 {@code drawInfoId} 与上一次相同则直接标记完成。
     *
     * @param drawInfoId   绘制唯一标识
     * @param w            纹理宽度
     * @param h            纹理高度
     * @param drawCallback 绘制回调
     * @return 是否成功注册（或已注册）
     */
    protected boolean tryRegisterDrawing(String drawInfoId, int w, int h, GraphicsTextureHelper.DrawFunctionGt drawCallback) {
        if (drawInfoId == null) {
            return false;
        }
        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        if (drawInfoId.equals(lastRegisteredDrawInfoId)) {
            scriptDone = true;
            return true;
        }
        gtHelper.removeDrawGraphic(getBlockPos());
        gtHelper.addDrawGraphicWithGt(getBlockPos(),
                new GraphicsTextureHelper.DrawInfo(drawInfoId, w, h, true, false),
                drawCallback
        );
        lastRegisteredDrawInfoId = drawInfoId;
        scriptDone = true;
        return true;
    }

    // ================================================================
    //  通用渲染方法
    // ================================================================

    /**
     * 渲染显示面模型（仅在脚本完成且纹理就绪时绘制）。
     * <p>
     * 子类的 {@link #whenRendering()} 应在绘制主模型后调⽤此⽅法。
     *
     * @param ctx 脚本上下文
     */
    protected void renderDisplayModel(ObjBlockScriptContext ctx) {
        if (markedError || !scriptDone || dmhDisp.getUploadedModel() == null) {
            return;
        }
        if (GraphicsTextureHelper.getInstance().isTextureAvailable(getBlockPos())) {
            GraphicsTexture tex = GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos());
            if (tex != null && tex.isValid()) {
                dmhDisp.getUploadedModel().replaceAllTexture(tex.identifier);
                ctx.drawModel(dmhDisp.getUploadedModel(), null);
            }
        }
    }

    // ================================================================
    //  生命周期
    // ================================================================

    @Override
    public void whenDisposing() {
        RotatableShapeHelper.getInstance().removeCache(getWorldPos());
        GraphicsTextureHelper.getInstance().removeDrawGraphic(getBlockPos());
    }

    /**
     * 重置绘制状态，使下次渲染时重新初始化。
     * 子类在配置变更时应调⽤此方法。
     */
    protected void resetDrawingState() {
        scriptDone = false;
        lastRegisteredDrawInfoId = "";
        resetRetryTimer();
    }

    // ================================================================
    //  通用形状 / 碰撞箱
    // ================================================================

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        if (markedError || shape == null || shape.isEmpty()) {
            return Shapes.empty();
        }
        return setShape(state);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        if (markedError || shape == null || shape.isEmpty()) {
            return Shapes.block();
        }
        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;

        RotatableShapeHelper helper = RotatableShapeHelper.getInstance();
        VoxelShape rotated = helper.getShapeForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ);
        if (rotated == null) {
            helper.initForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ, shape);
            rotated = helper.getShapeForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ);
        }
        return rotated.move(trans.x, trans.y, trans.z).optimize();
    }
}
