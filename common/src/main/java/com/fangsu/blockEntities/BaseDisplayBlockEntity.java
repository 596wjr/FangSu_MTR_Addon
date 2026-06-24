package com.fangsu.blockEntities;

import com.fangsu.mappings.GsonHelper;
import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.shape.RawShape;
import com.fangsu.shape.RotatableShapeHelper;
import com.fangsu.shape.ShapeCollection;
import com.fangsu.ui.RouteSelectInfo;
import com.fangsu.utils.GraphicsTextureHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import mtr.client.ClientData;
import mtr.data.Platform;
import mtr.data.Route;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    protected Map<String, Object> drawState = new ConcurrentHashMap<>();
    /**
     * ⽤⼾⾃定义额外配置（来⾃ content JSON 的 extraConfig）
     */
    protected Map<String, JsonElement> userExtraConfigs = new ConcurrentHashMap<>();

    // ==================== 重试节流 ====================

    /**
     * 重试间隔（毫秒），避免数据未就绪时每帧都重试
     */
    private static final long RETRY_INTERVAL_MS = 200;

    /**
     * 上次重试初始化的时间戳
     */
    private long lastRetryTime = 0;

    /**
     * 外部 MTR 数据变更检测间隔（毫秒），比重试间隔长得多以减少不必要开销
     */
    private static final long DATA_CHECK_INTERVAL_MS = 2000;

    /**
     * 上次数据变更检测的时间戳
     */
    private long lastDataCheckTime = 0;

    /**
     * 检查是否应该检查外部数据变更（受 DATA_CHECK_INTERVAL 节流）。
     * 用于检测 MTR 数据（路线颜色、车站名称等）的外部更新。
     */
    protected boolean shouldCheckDataChange() {
        long now = System.currentTimeMillis();
        if (now - lastDataCheckTime >= DATA_CHECK_INTERVAL_MS) {
            lastDataCheckTime = now;
            return true;
        }
        return false;
    }

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

    // ==================== 异步任务基础设施 ====================

    /**
     * 共享后台线程池，用于将 JSON 解析 + MTR 查找等操作从主线程移走
     */
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fangsu-display-async");
        t.setDaemon(true);
        return t;
    });

    /**
     * 进行中的异步任务，用于去重
     */
    private CompletableFuture<Void> asyncTaskFuture;

    /**
     * 提交两步异步任务。
     * <ol>
     *   <li>{@code snapshotPhase} — 在主线程执行，用于快照 MTR 数据（线程安全）</li>
     *   <li>{@code backgroundPhase} — 在后台线程执行，用于查表和创建对象</li>
     * </ol>
     * 已有进行中的任务时会跳过本次提交。
     */
    protected final void submitAsyncTask(Runnable snapshotPhase, Runnable backgroundPhase) {
        if (asyncTaskFuture != null && !asyncTaskFuture.isDone()) return;
        snapshotPhase.run();
        asyncTaskFuture = CompletableFuture.runAsync(backgroundPhase, ASYNC_EXECUTOR);
    }

    /**
     * 检查异步任务是否已完成
     */
    protected final boolean isAsyncTaskDone() {
        return asyncTaskFuture != null && asyncTaskFuture.isDone();
    }

    /**
     * 消费（清除）已完成的异步任务标记
     */
    protected final void consumeAsyncTask() {
        asyncTaskFuture = null;
    }

    /**
     * 取消所有待处理异步任务并丢弃结果
     */
    private void cancelPendingAsyncTask() {
        asyncRoutesResult = null;
        asyncTaskFuture = null;
    }

    // ==================== 异步路线重载（RIS / Diaoban 统一） ====================

    /**
     * 异步路线重载的结果（后台线程写入，主线程读取）
     */
    private volatile List<RouteSelectInfo> asyncRoutesResult;

    /**
     * 单调递增的任务代次号，用于丢弃过期后台任务的结果
     */
    private int asyncTaskGen = 0;

    /**
     * 异步触发路线重载（两步：主线程快照 → 后台线程查表）。
     * RIS 和 Diaoban 共用此方法，避免重复代码。
     */
    protected final void triggerAsyncRouteReload(String routeJson) {
        final List<JsonElement>[] rawRoutesRef = new List[]{new ArrayList<>()};
        final Map<Long, Route>[] routeMapRef = new Map[]{new HashMap<>()};
        final Map<Long, Platform>[] platformMapRef = new Map[]{new HashMap<>()};
        final int myGen = ++asyncTaskGen;

        submitAsyncTask(
                // snapshotPhase — 主线程：快照 MTR 数据（受 submitAsyncTask 去重保护）
                () -> {
                    // 有新任务提交时立即丢弃旧结果，防止过期数据被 pollAsyncRoutes 读取
                    asyncRoutesResult = null;
                    try {
                        rawRoutesRef[0] = GsonHelper.asList(
                                Main.JSON_PARSER.parse(routeJson).getAsJsonArray());
                    } catch (Exception ignored) {
                    }
                    Map<Long, Route> map = new HashMap<>();
                    for (Route r : ClientData.ROUTES) map.put(r.id, r);
                    routeMapRef[0] = map;
                    Map<Long, Platform> pMap = new HashMap<>();
                    for (Platform p : ClientData.PLATFORMS) pMap.put(p.id, p);
                    platformMapRef[0] = pMap;
                },
                // backgroundPhase — 后台线程：查表创建 RouteSelectInfo
                () -> {
                    // 如果后台线程启动时已有新任务取代了本任务，直接丢弃结果
                    if (myGen != asyncTaskGen) return;
                    List<RouteSelectInfo> results = new ArrayList<>();
                    Map<Long, Route> rMap = routeMapRef[0];
                    Map<Long, Platform> pMap = platformMapRef[0];
                    for (JsonElement rawRoute : rawRoutesRef[0]) {
                        if (!rawRoute.isJsonArray() || rawRoute.getAsJsonArray().size() < 2) continue;
                        JsonArray a = rawRoute.getAsJsonArray();
                        Route mtrRoute = rMap.get(a.get(0).getAsLong());
                        Platform plat = pMap.get(a.get(1).getAsLong());
                        if (mtrRoute != null && plat != null) {
                            results.add(new RouteSelectInfo(new LocalRoute(mtrRoute), plat));
                        }
                    }
                    if (results.isEmpty()) {
                        results.add(new RouteSelectInfo(new LocalRoute(), null));
                    }
                    // 再次检查代次，确保在计算过程中没有被新任务取代
                    if (myGen != asyncTaskGen) return;
                    asyncRoutesResult = results;
                }
        );
    }

    /**
     * 检查异步路线重载是否完成，完成则返回结果并清理状态。
     * 未完成返回 {@code null}。
     */
    @Nullable
    protected final List<RouteSelectInfo> pollAsyncRoutes() {
        if (isAsyncTaskDone()) {
            List<RouteSelectInfo> result = asyncRoutesResult;
            asyncRoutesResult = null;
            consumeAsyncTask();
            return result;
        }
        return null;
    }

    /**
     * 比较两个路线列表是否相等（仅比较 id 字段，避免对象引用不同导致的误判）。
     */
    protected static boolean routesEqual(List<RouteSelectInfo> a, List<RouteSelectInfo> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            RouteSelectInfo ra = a.get(i), rb = b.get(i);
            long raId = ra.route != null ? ra.route.id : -1;
            long rbId = rb.route != null ? rb.route.id : -1;
            if (raId != rbId) return false;
            long raPlat = ra.plat != null ? ra.plat.id : -1;
            long rbPlat = rb.plat != null ? rb.plat.id : -1;
            if (raPlat != rbPlat) return false;
        }
        return true;
    }

    // ==================== 显示纹理 ====================

    /**
     * 显⽰⾯（屏幕）模型
     */
    protected DynamicModelHolder dmhDisp = new DynamicModelHolder();
    /**
     * 上一次替换的纹理标识，用于去重避免每帧 replaceAllTexture
     */
    private ResourceLocation lastDispTextureId = null;
    /**
     * 显示纹理是否已获取并可用，为 true 时跳过每帧的 synchronized 查询
     */
    private boolean dispTextureReady = false;
    /**
     * 缓存方块 ID 字符串，避免每帧 String 分配
     */
    private String cachedBlockId = null;
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
            userExtraConfigs = new ConcurrentHashMap<>();
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
        if (markedError || !scriptDone || dmhDisp == null) {
            return;
        }
        ModelCluster dispModel = dmhDisp.getUploadedModel();
        if (dispModel == null) {
            return;
        }

        // 纹理已就绪：直接绘制，跳过每帧的 synchronized 查询
        if (dispTextureReady) {
            ctx.drawModel(dispModel, null);
            return;
        }

        // 首次获取纹理（合并 isTextureAvailable + getBlockGraphics 为一次 synchronized 调用）
        GraphicsTexture tex = GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos());
        if (tex != null && tex.isValid()) {
            dispModel.replaceAllTexture(tex.identifier);
            lastDispTextureId = tex.identifier;
            dispTextureReady = true;
            ctx.drawModel(dispModel, null);
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
        lastDispTextureId = null;
        dispTextureReady = false;
        resetRetryTimer();
        // 清除待处理的异步任务结果，避免 UI 更新后过期数据覆盖正确路线
        cancelPendingAsyncTask();
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
