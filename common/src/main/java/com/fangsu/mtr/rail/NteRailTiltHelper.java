package com.fangsu.mtr.rail;

import com.fangsu.Main;
import mtr.data.Rail;

import java.lang.reflect.Method;

/**
 * NTE（{@code mtrsteamloco}）渲染路径上的外轨超高实现（P5-4 / <b>路径 B</b>）。
 * <p>
 * <b>为什么需要路径 B</b>：NTE 会 {@code @Inject} 到 MTR 的
 * {@code RenderTrains.renderRailStandard(…10 参数…)} 上并 {@code CallbackInfo.cancel()}，
 * 改用它自己的 {@code RailRenderDispatcher} 画轨道。因此 {@link RailTiltRenderHelper}（路径 A / P5-3）
 * 的 {@code IDrawing.drawTexture} 重定向在装了 NTE 时<b>一次都不会被调用</b> ——
 * 装了 NTE 的玩家会看到中心线被抬高（P5-1/P5-2，那条走的是 {@code Rail.getPositionY}，与渲染器无关）、
 * 列车车体也倾斜（P5-6），<b>但轨面是平的</b>。本类补的就是这一段。
 * <p>
 * <b>NTE 的轨道渲染链（对本地 NTE 0.5.2+1.20.1 的真实 jar 逐字节核实）</b>
 * <pre>
 *   mtr.render.RenderTrains.renderRailStandard(Level,Rail,F,Z,F,String,F,F,F,F)
 *     → NTE 的 RenderTrainsMixin @Inject：ClientConfig.getRailRenderLevel()
 *         level 0 → 取消 MTR 的绘制、NTE 也不画
 *         level 1 → 不取消（MTR 的轨面带照旧由路径 A 画）
 *         level ≥ 2 → RailRenderDispatcher.registerRail(rail)；返回 true 时取消 MTR 的绘制
 *     → RailRenderDispatcher.drawRails(Level, BatchManager, Matrix4f)
 *         → addRail(Rail) → new BakedRail(rail)              【烘焙：每条轨道一次】
 *             → modelKey != "null" 时：rail.render(lambda$new$1, 0f, 0f)
 *                 → Rail.renderSegment(...) 每段截面回调一次 lambda$new$1
 *                     → getLookAtMat(FFFFFFFZ) 造一个 4×4 截面矩阵
 *                     → coveredChunks.get(chunkId).add(矩阵)   【★ 本类的注入点】
 *         → RailChunkBase.rebuildBuffer(Level)
 *             → InstancedRailChunk（level 3）：color/light/64 字节矩阵写进 InstanceBuf
 *             → MeshBuildingRailChunk（level 2）：RawModel.appendTransformed(model, 矩阵, …)
 * </pre>
 * <b>逐字节核实的关键事实</b>：
 * <ol>
 *   <li>{@code BakedRail.<init>} 用 {@code rail.render(callback, 0f, 0f)} 烘焙 ——
 *       <b>两个 radiusOffset 都是 0</b>，于是 MTR 的四个角点在 XZ 上退化到<b>中心线</b>，
 *       {@code getLookAtMat} 的枢轴 {@code (px,py,pz) = ((x1+x4)/2, (y1+y2)/2 + yOffset, (z1+z4)/2)}
 *       就是「<b>抬升后的中心线</b>」上的点；而 {@code y1/y2} 来自
 *       {@code Rail.getPositionY(double)}，已经被 P5-1 的内核接管 ——
 *       <b>与路径 A 的枢轴（四角点中心、y 取内核抬升后的中心线高度）在几何上等价</b>
 *       （两者都落在滚转轴上，沿轴平移不改变刚体旋转）。</li>
 *   <li>{@code getLookAtMat} 造的局部标架：{@code +Z = Ry(yaw)·Rx(pitch)·Z} 是<b>轨道前进方向</b>
 *       （{@code yaw = atan2(x4-cx, z4-cz)}，look-at 点就是同一侧相邻截面的中心线点），
 *       {@code +X = Ry(yaw)·X = (Tz, 0, -Tx) = N} 正是 MTR 的 <b>offsetRadius 增大方向</b>
 *       （{@code Rail.renderSegment} 源码 :429-432：角点 1/4 在 {@code offsetRadius1} 侧、
 *       2/3 在 {@code offsetRadius2} 侧；{@code getPositionXZ} 给 x 加 {@code +radiusOffset}、
 *       给 z 加 {@code -radiusOffset}）。</li>
 *   <li>于是「绕该截面中心线滚转」＝在<b>局部系里绕 Z 轴旋转</b>：矩阵的局部原点是枢轴、
 *       局部 Z 是前进轴，<b>一次 {@code Matrix4f.rotateZ(φ)} 就够</b>（sowcer 的
 *       {@code rotateZ} 直接转调 {@code org.joml.Matrix4f.rotateZ}，是后乘
 *       {@code M = M · Rz}，即绕局部原点/局部 Z 轴）。</li>
 * </ol>
 * <b>实例化路径能不能承载「每轨道滚转」——已解决，答案是「能」</b>：
 * {@code InstancedRailChunk.rebuildBuffer} 对 {@code containingRails} 里每个
 * {@code (BakedRail, Matrix4f)} 写入 {@code color(int) + light(int) + 64 字节完整 4×4 矩阵}
 * （{@code Matrix4f.store(FloatBuffer)}），而 {@code InstancedRailChunk.RAIL_MAPPING} 把
 * {@code MATRIX_MODEL} 映射到 {@code INSTANCE_BUF} —— <b>每个实例携带一个任意 4×4 变换</b>，
 * 与 {@code MeshBuildingRailChunk} 用的完全是同一批矩阵（只是消费方式不同：
 * 一个进实例缓冲，一个进 {@code RawModel.appendTransformed}）。因此：
 * <ul>
 *   <li><b>不需要</b>为带滚转的轨道强制切到 mesh 路径；</li>
 *   <li>两个 chunk 实现由<b>同一个注入点</b>覆盖；</li>
 *   <li>性能代价为零（矩阵本来就要逐实例上传）。</li>
 * </ul>
 * <b>被注入的类名必须写三遍</b>：forgix 打出来的 NTE/ANTE jar 里
 * {@code cn.zbx1425.mtrsteamloco…}、{@code fabric.cn.zbx1425.mtrsteamloco…}、
 * {@code forge.cn.zbx1425.mtrsteamloco…} 是<b>三个不同的类</b>（声明的二进制名带前缀，
 * {@code javap} 对 {@code fabric/cn/.../BakedRail.class} 打印的就是
 * {@code public class fabric.cn.zbx1425.mtrsteamloco.render.rail.BakedRail}）。
 * 本地 NTE 0.5.2+1.20.1 的 jar 里实际只有 {@code fabric.} 与 {@code forge.} 两份
 * （{@code cn.} 那份在未 forgix 的构建里），三个都列进 {@code targets} 是无害的。
 * <p>
 * <b>ANTE（{@code mtrsteamloco} 的 fork）与 NTE 的差异</b>：类名、包名、{@code coveredChunks}
 * 的结构都一样，但 <b>ANTE 1.1.0+1.20.1 把「逐截面攒矩阵」的循环从 {@code lambda$new$1}
 * 内联进了 {@code BakedRail.<init>(Lmtr/data/Rail;)V}</b>，那一版没有 {@code lambda$new$1}。
 * 本类因此维护<b>两个</b>截面注入点常量（{@link #BAKED_RAIL_SECTION} /
 * {@link #BAKED_RAIL_SECTION_INLINE}），两者都指向同一个 {@code ArrayList.add(Object)}，
 * 由 {@code NteBakedRailMixin} 各挂一个 {@code @ModifyArg} 并全部写 {@code require = 0}。
 * 附带事实：ANTE 的 {@code RailExtraSupplier} 多了一条原生滚转通道
 * （{@code getRollAngleMap()} → {@code getRollAngle(rail, t)} → {@code getLookAtMat} 的
 * {@code rotateZ}），而方速<b>从不</b>写那个 map，所以 ANTE 自带的滚转角恒为 0，
 * 与本类的逐截面旋转不冲突（方速的 {@code rotateZ(φ)} 后乘在 ANTE 的 {@code rotateZ(0)} 之上）。
 * <p>
 * <b>为什么用「{@code ArrayList.add(Object)} 的 {@code @ModifyArg}」而不是「{@code getLookAtMat}
 * 的返回值」</b>：本 mixin 是 {@code @Pseudo} + 字符串 {@code targets} 的可选目标 mixin，
 * 编译期 NTE <b>不在类路径上</b>（它不是构建依赖），所以处理器方法的签名里<b>一个 NTE 类型都不能出现</b>。
 * {@code lambda$new$1} 里那次 {@code ArrayList.add} 的实参类型在字节码里就是
 * {@code Ljava/lang/Object;}，处理器写成 {@code (Object)Object} 与目标<b>逐字符相同</b>，
 * 不需要 {@code @Coerce}、不需要泛型（{@code CallbackInfoReturnable<Matrix4f>} 那种写法会引入
 * 处理器能否接受泛型实参的不确定性）。矩阵本身用<b>运行时反射</b>调用 {@code rotateZ(float)}
 * —— 反射拿的是实例的运行时类，于是三个 forgix 前缀、乃至将来 NTE 换 sowcer 包名，
 * <b>都不需要在这里再写一遍类名</b>。
 * <p>
 * <b>与路径 A 的一致性</b>：滚转角与相位全部来自 {@link RailTiltRenderHelper}
 * （同一个内核实例、同一个相位累加器、同一个 {@link RailTiltRenderHelper#railFrameAngle}），
 * 所以三条消费者（轨面 / 中心线 / 车体）不可能互相矛盾：
 * <ul>
 *   <li>相位 {@code parameter} = {@link RailTiltRenderHelper#quadParameterRange()}，
 *       即 {@code renderSegment} 交给 {@code Rail.getPositionY} 的那个值
 *       （NTE 的烘焙同样走 {@code Rail.render} → {@code renderSegment}，两个相位钩子在
 *       {@code RailGeometryMixin} 上，与渲染器无关）；</li>
 *   <li>角度 {@code φ = railFrameAngle(roll, true) = -roll}，与路径 A 逐字同一个表达式。</li>
 * </ul>
 * <b>符号推导（与路径 A 的对照）</b>：路径 A 把角点绕「过枢轴、方向为水平前进轴 T」按右手旋转
 * {@code θ = -roll}；对横向偏移 {@code ρ} 的点，`a×v` 的竖直分量是 {@code +(T×N)_y·ρ}，
 * 而 MTR 坐标下 {@code T×N = +Y}（{@code getPositionXZ} 里 x 用 {@code +radiusOffset}、
 * z 用 {@code -radiusOffset}，故 {@code N = (k,0,-h)}、{@code T = (h,0,k)}，叉乘得 {@code (0,h²+k²,0)}），
 * 所以偏移 {@code +ρN} 的点竖直位移 {@code = ρ·sin θ = -ρ·sin(roll)}。
 * NTE 一侧：局部 {@code +X} 就是 {@code N}，{@code M·Rz(φ)} 把局部 {@code (ρ,0,0)} 送到
 * {@code ρ(cos φ, sin φ, 0)}，竖直位移 {@code = +ρ·sin φ}。两者相等 ⇒ <b>{@code φ = -roll}</b>，
 * 与路径 A 的 {@code θ} 是同一个数，无需再取反。
 * <p>
 * <b>逐位无操作保证</b>：下列任一情况，{@link #tiltBakedSection(Object)} <b>把收到的同一个对象原样返回</b>，
 * 一个浮点运算都不做、一次反射都不做：
 * <ul>
 *   <li>不在 NTE 的烘焙窗口内（{@code BAKED_RAIL_INIT} 的 HEAD 钩子没挂上）；</li>
 *   <li>该轨道没有附加姿态，或姿态没有滚转（{@link RailTiltRenderHelper#frameFor} 返回 {@code null}，
 *       它在这一步之前就返回，连内核都不构建）；</li>
 *   <li>该相位处滚转为 0、角度非有限、或矩阵类上找不到 {@code rotateZ(float)}。</li>
 * </ul>
 * 因此「没有滚转的轨道」在 NTE 路径上与原版<b>逐位相同</b>（同一个矩阵对象、同样的字节）。
 * <p>
 * <b>两条路径不会同时画</b>：见 {@link #tiltBakedSection(Object)} 与类注释的调用链 —— NTE 只在
 * {@code railRenderLevel ≥ 2} 且 {@code registerRail(rail)} 返回 true 时取消 MTR 的轨面，
 * 那时路径 A 的钩子根本不会被调用；反之 {@code railRenderLevel == 1} 或非 TRAIN 运输方式
 * （{@code getModelKeyForRender} 返回空串）时 NTE 既不取消也不画，只有路径 A 生效。
 * 本类只改 NTE 已经烘焙好的矩阵，不碰 {@code registerRail}/{@code cancel} 的任何判断。
 */
public final class NteRailTiltHelper {

    // ==================== 目标类名（forgix 三个前缀） ====================

    /** 未 forgix 的 NTE 构建里的包名。 */
    public static final String BAKED_RAIL_TARGET_CN =
            "cn.zbx1425.mtrsteamloco.render.rail.BakedRail";

    /** forgix 打出来的 Fabric 侧包名（本地 NTE 0.5.2+1.20.1 的 jar 里就是这个）。 */
    public static final String BAKED_RAIL_TARGET_FABRIC =
            "fabric.cn.zbx1425.mtrsteamloco.render.rail.BakedRail";

    /** forgix 打出来的 Forge 侧包名。 */
    public static final String BAKED_RAIL_TARGET_FORGE =
            "forge.cn.zbx1425.mtrsteamloco.render.rail.BakedRail";

    /** 供诊断打印用（<b>不能</b>用于注解：数组不是常量表达式，注解里逐个写常量）。 */
    public static final String[] BAKED_RAIL_TARGETS = {
            BAKED_RAIL_TARGET_CN, BAKED_RAIL_TARGET_FABRIC, BAKED_RAIL_TARGET_FORGE
    };

    /**
     * {@code BakedRail.<init>(mtr.data.Rail)} —— 烘焙窗口（以及「当前正在烘焙哪条轨道」的来源）。
     * <p>
     * {@code javap -p -s} 核实（NTE 0.5.2+1.20.1，{@code fabric}/{@code forge} 两份相同）：
     * {@code public fabric.cn.zbx1425.mtrsteamloco.render.rail.BakedRail(mtr.data.Rail)}
     * → 描述符 {@code (Lmtr/data/Rail;)V}。<b>写完整描述符</b>：NTE 将来多一个同名重载时，
     * 裸名字会静默指错（这正是本仓库在 P5-3 上踩过的坑）。
     */
    public static final String BAKED_RAIL_INIT = "<init>(Lmtr/data/Rail;)V";

    /**
     * {@code BakedRail.lambda$new$1} —— 真正攒出逐截面矩阵的那个 {@code Rail$RenderRail} 回调。
     * <p>
     * {@code javap -p -s} 核实：
     * {@code private void lambda$new$1(float, float, boolean, double×10)} →
     * {@code (FFZDDDDDDDDDD)V}。后 10 个 double 就是 MTR 的
     * {@code renderRail(x1,z1,x2,z2,x3,z3,x4,z4,y1,y2)}；
     * 前两个 float 是捕获进去的 {@code RailModelProperties.yOffset} 与 {@code repeatInterval}，
     * 布尔是 {@code RailExtraSupplier.getRenderReversed()}。
     * <p>
     * synthetic lambda 只能写名字（注解处理器解析不了 synthetic 描述符），所以这里
     * {@code method} 写名字 + 描述符的组合不受支持，只能写裸名字 —— 但注入点用
     * {@code @At(INVOKE)} 钉死在具体调用上，名字错位只降级为「不倾斜」并由一次性诊断报出。
     */
    public static final String BAKED_RAIL_SECTION = "lambda$new$1(FFZDDDDDDDDDD)V";

    /**
     * ANTE 1.1.0+ 的<b>内联形态</b>：同一处逐截面 {@code ArrayList.add} 所在的方法变成了构造器本身。
     * <p>
     * <b>为什么必须有这条选择器</b>：ANTE（{@code mtrsteamloco} 的 fork，测试者实际装的是
     * {@code 1.1.0+1.20.1}）把「逐截面攒矩阵」的整个循环从 {@code lambda$new$1} 搬进了
     * {@code BakedRail.<init>(mtr.data.Rail)}，那一版<b>不再有 {@code lambda$new$1}</b>。
     * {@code javap -p -s} 核实（{@code fabric} / {@code forge} 两份逐字节相同）：
     * <pre>
     *   public fabric.cn.zbx1425.mtrsteamloco.render.rail.BakedRail(mtr.data.Rail);   // (Lmtr/data/Rail;)V
     *   private static java.util.ArrayList lambda$new$0(java.lang.Long);               // 只剩这一个 lambda
     * </pre>
     * 于是 {@link #BAKED_RAIL_SECTION} 在那版上找不到目标，而
     * {@code @ModifyArg(require = 0)} 对「注入点数量为 0」是<b>完全静默</b>的
     * （sponge-mixin {@code TargetSelectors.validate(found, expected, required)}：
     * {@code found == 0} 时先看 {@code DEBUG_INJECTORS}，再看 {@code expectedCallbackCount <= 0} 与
     * {@code requiredCallbackCount <= 0}，两者都走「直接 return」分支 ⇒ 一条日志都不打）——
     * 这就是测试者日志里路径 B「既不生效也不报警」的机制。
     * <p>
     * 描述符与 {@link #BAKED_RAIL_INIT} <b>逐字符相同</b>（{@code (Lmtr/data/Rail;)V}），
     * 但两者是<b>两个不同的注入点</b>：{@code BAKED_RAIL_INIT} 用于 HEAD/RETURN 的烘焙窗口，
     * 本常量用于「截面矩阵落库的那次 {@code add}」。<b>刻意不合并成一个常量</b>：
     * 合并之后代码上就看不出「到底哪个版本的哪个钩子没挂上」，而本次故障的教训正是
     * 「静默失效必须能从代码与日志上看出来」。
     */
    public static final String BAKED_RAIL_SECTION_INLINE = "<init>(Lmtr/data/Rail;)V";

    /**
     * 截面矩阵落库的那次调用：{@code ArrayList.add(Object)}。
     * <p>
     * {@code javap -p -c} 核实两个形态里<b>各自只有一处</b>
     * {@code invokevirtual java/util/ArrayList.add:(Ljava/lang/Object;)Z}，且整类全文也只有这一处：
     * <pre>
     *   NTE 0.5.2+1.20.1（{@code lambda$new$1}，fabric/forge 两份相同）：
     *       88: fload_2
     *       89: iload_3
     *       90: invokestatic  // Method getLookAtMat:(FFFFFFFZ)L.../Matrix4f;
     *       93: invokevirtual // Method java/util/ArrayList.add:(Ljava/lang/Object;)Z   ← add #0（全类唯一）
     *       96: pop
     *       97: return
     *   ANTE 1.1.0+1.20.1（{@code BakedRail.<init>(Lmtr/data/Rail;)V}，fabric/forge 两份相同）：
     *      239: fload         18                  // ANTE 自带的 getRollAngle(rail, t) 结果
     *      241: fload         6                   // yOffset
     *      243: iload         4                   // reversed
     *      245: invokestatic  // Method getLookAtMat:(LVec3;LVec3;LVec3;FFZ)L.../Matrix4f;
     *      248: invokevirtual // Method java/util/ArrayList.add:(Ljava/lang/Object;)Z   ← add #0（全类唯一）
     *      251: pop
     *      252: aload         16
     *      263: goto          147                 // 循环回边
     *      266: return
     * </pre>
     * 实参类型是 {@code java.lang.Object}（泛型擦除后），因此处理器签名可以写成
     * {@code (Object)Object} 而与目标逐字符相同 —— <b>不需要</b>出现任何 NTE 类型。
     * 两者都是 {@code public}（{@code javap -p} 已核实），不存在可见性问题。
     */
    public static final String SECTION_ADD_TARGET = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z";

    /**
     * {@link #SECTION_ADD_TARGET} 的 ordinal：钉死在第一处，避免将来 NTE 多一处 {@code add} 而造成重复旋转。
     * <p>
     * <b>为什么两个形态可以共用同一个 ordinal</b>：Mixin 的 ordinal 是<b>按方法</b>计数的，
     * 而 {@code lambda$new$1}（NTE 0.5.2）与 {@code <init>(Lmtr/data/Rail;)V}（ANTE 1.1.0）
     * 各自都<b>恰好一处</b>该调用（见 {@link #SECTION_ADD_TARGET} 的 {@code javap -p -c} 摘录），
     * 所以两边的 {@code add #0} 都是唯一的、可无歧义区分的注入点。
     * <p>
     * <b>什么时候必须停下来</b>：如果将来某一版在这两个方法里出现<b>第二处</b>
     * {@code ArrayList.add(Object)}，ordinal 就会退化成「猜」，那时必须改用
     * 「按实参/接收者做运行时校验」或改走 ANTE 原生的
     * {@code RailExtraSupplier.getRollAngleMap()} 滚转通道，<b>绝不能</b>留下一个可能旋转错矩阵的 ordinal。
     */
    public static final int SECTION_ADD_ORDINAL = 0;

    /** 滚转矩阵的反射句柄（按运行时类缓存；NTE 的类名因此不必在本类里出现）。 */
    private static final Object ROTATE_LOCK = new Object();

    /** 「NTE 在不在」只报一次。 */
    private static boolean presenceReported = false;

    /** NTE 是否存在（由 {@code MainClient.initClient()} 经 {@link #reportNtePresence} 告知）。 */
    private static boolean ntePresent = false;

    /** {@code BakedRail.<init>} 的 HEAD 钩子是否触发过（= 烘焙窗口建立过）。 */
    private static volatile boolean bakeHookAlive = false;

    /**
     * 「截面矩阵」的 {@code @ModifyArg} 钩子是否触发过（= 逐截面注入真的挂上了）。
     * <p>
     * <b>两个版本形态共用这一个标志</b>：NTE 0.5.2 与 ANTE 1.1.0 的注入点分别是
     * {@code lambda$new$1} 与 {@code <init>}，但两者都汇进 {@link #tiltBakedSection(Object, int)}，
     * 所以「有没有任何一个形态挂上」由它表达；「挂上的是哪一个形态」由
     * {@link #sectionHookForm} 表达。看门狗判据必须包含本标志 —— 只有它才会随
     * NTE 内部结构变化而归零，{@link #bakeHookAlive} 在 ANTE 上照常为真。
     */
    private static volatile boolean sectionHookAlive = false;

    /** 截面钩子还没有被任何形态调用过。 */
    public static final int SECTION_FORM_NONE = 0;

    /** NTE 0.5.2 形态：{@code lambda$new$1} 里的 {@code ArrayList.add}（偏移 93）。 */
    public static final int SECTION_FORM_LAMBDA = 1;

    /** ANTE 1.1.0 内联形态：{@code BakedRail.<init>} 里的 {@code ArrayList.add}（偏移 248）。 */
    public static final int SECTION_FORM_INLINE = 2;

    /**
     * 已经观测到的截面钩子<b>版本形态</b>（{@link #SECTION_FORM_NONE} /
     * {@link #SECTION_FORM_LAMBDA} / {@link #SECTION_FORM_INLINE}）。
     * <p>
     * 只用于一次性诊断的文案：两个形态的选择器字符串完全不同，
     * 日志里报出与实际安装版本对不上的那个名字，会把排查方向直接带偏。
     */
    private static volatile int sectionHookForm = SECTION_FORM_NONE;

    /** 真正旋转过的截面数（>0 即「NTE 路径的倾斜已生效」）。 */
    private static volatile int tiltedSections = 0;

    /** 已经解析过 {@code rotateZ(float)} 的那个矩阵运行时类。 */
    private static volatile Class<?> rotateZResolvedFor = null;

    /** 解析出来的 {@code rotateZ(float)}；{@code null} = 该矩阵类上没有这个 API。 */
    private static volatile Method rotateZMethod = null;

    private static boolean bakeLivenessChecked = false;
    private static boolean firstTiltLogged = false;
    private static boolean rollApiWarned = false;
    private static boolean watchdogWarned = false;

    /** 看门狗计数的帧数（NTE 在场且从未观察到烘焙时才告警）。 */
    private static volatile int watchdogFrames = 0;

    /**
     * 看门狗阈值：60 秒左右（渲染帧，60 fps 下约 3600 帧）。
     * 取大值是为了避免「玩家视野里本来就没有轨道」被误报成钩子失效。
     */
    private static final int WATCHDOG_FRAMES = 3600;

    /** 烘焙窗口内「当前正在烘焙的轨道」。 */
    private static final ThreadLocal<Rail> BAKING_RAIL = new ThreadLocal<>();

    private NteRailTiltHelper() {
    }

    // ==================== 一次性诊断 ====================

    /**
     * 由 {@code MainClient.initClient()} 调用一次，把「NTE 在不在」固定下来并打一条 INFO。
     * <p>
     * 为什么要在这里报：本 mixin 是 {@code @Pseudo} 的可选目标 —— 未安装 NTE 时 Mixin
     * <b>静默跳过</b>整条路径 B，日志里不会有任何痕迹。没有这条日志，就分不清
     * 「没装 NTE（走路径 A）」和「装了 NTE 但路径 B 的钩子没挂上（轨面平）」。
     */
    public static void reportNtePresence(boolean present) {
        if (presenceReported) {
            return;
        }
        presenceReported = true;
        ntePresent = present;
        if (present) {
            Main.LOGGER.info("[P5-4] 检测到 NTE（mtrsteamloco）：MTR 的轨面由 NTE 接管，"
                    + "路径 A（P5-3）不生效属预期；外轨超高改由路径 B（NTE BakedRail 的逐截面矩阵）实现。");
        } else {
            Main.LOGGER.info("[P5-4] 未检测到 NTE：外轨超高走 MTR 路径（路径 A / P5-3）；"
                    + "路径 B 的 NTE 目标不存在，Mixin 静默跳过（@Pseudo），本项为正常状态。");
        }
    }

    /**
     * 每帧一次的看门狗（由 {@code RenderTrainsMixin} 在 {@code RenderTrains.render} 的 HEAD 调用）。
     * <p>
     * 需要被看见的失效有<b>两种</b>，且都必须由「不依赖注入器自身」的时钟来报：
     * <ol>
     *   <li><b>连烘焙窗口都没建立</b>：{@code BakedRail.<init>} 的 HEAD 钩子从未触发
     *       （目标类不存在、类名变了、构造器描述符变了……）；</li>
     *   <li><b>烘焙窗口活了，但截面钩子没活</b>：这正是 ANTE 1.1.0 上的真实故障 ——
     *       ANTE 把逐截面循环从 {@code lambda$new$1} 内联进了 {@code <init>}，
     *       老的选择器找不到目标，而 {@code require = 0} 把它静默跳过；
     *       同时 {@code <init>} 的 HEAD/RETURN 钩子照常挂得上，于是只看
     *       {@code bakeHookAlive} 的判据<b>永远不会响</b>，日志一片安静。</li>
     * </ol>
     * 所以判据必须是 {@code bakeHookAlive && sectionHookAlive} 的<b>合取</b>：
     * 少看任何一项，都会留下一种「轨面永远是平的、日志却没事」的静默失效。
     * <p>
     * 阈值取得很大（{@link #WATCHDOG_FRAMES}），并且措辞明确写着「若此时视野内有轨道」，
     * 避免「视野里没有轨道」被误判成钩子失效。<b>本方法最多打一条日志</b>
     * （{@link #watchdogWarned} 门闩），不随帧数刷屏。
     */
    public static void tickFrameWatchdog(boolean nteLoaded) {
        if (!nteLoaded || (bakeHookAlive && sectionHookAlive) || watchdogWarned) {
            return;
        }
        if (++watchdogFrames < WATCHDOG_FRAMES) {
            return;
        }
        watchdogWarned = true;
        if (!bakeHookAlive) {
            Main.LOGGER.warn("[P5-4] 已安装 NTE/ANTE，但约 {} 帧内没有观察到任何 NTE 轨道烘焙"
                            + "（{} 上的 {} 钩子未触发）。若这段时间里视野内确实有轨道，说明路径 B 的入口钩子没生效，"
                            + "轨面会保持水平（中心线与车体仍按 P5-1/P5-6 变化）。",
                    WATCHDOG_FRAMES, BAKED_RAIL_TARGETS[1], BAKED_RAIL_INIT);
            return;
        }
        // 走到这里说明：烘焙窗口建立过（说明目标类与 <init>(Lmtr/data/Rail;)V 都被命中了），
        // 但「截面矩阵」钩子一次都没被调用 —— 两个版本形态的截面注入器一个都没挂上。
        Main.LOGGER.warn("[P5-4] 已安装 NTE/ANTE、烘焙窗口也已建立（{} 钩子生效），但约 {} 帧内从未观察到"
                        + "「逐截面矩阵」钩子被调用：两个版本形态的截面注入器都没挂上 —— "
                        + "NTE 0.5.2 形态是 {} 上的 {}，ANTE 1.1.0 内联形态是 {} 内偏移 248 处的同一个调用。"
                        + "这两处都写了 require = 0，「目标不存在」会被 Mixin 静默跳过（不报错、不打日志）。"
                        + "后果：NTE/ANTE 画的轨面保持水平（轨道中心线仍按 P5-1 抬高、车体仍按 P5-6 倾斜）。"
                        + "若这段时间里视野内确实有轨道，请对 {} 重新做 javap -p -c 取证。",
                BAKED_RAIL_INIT, WATCHDOG_FRAMES,
                BAKED_RAIL_SECTION, SECTION_ADD_TARGET,
                BAKED_RAIL_SECTION_INLINE,
                BAKED_RAIL_TARGETS[1]);
    }

    /**
     * 烘焙窗口的活性自检：由「第一条真的需要倾斜的轨道」触发一次，之后永不再跑。
     * <p>
     * <b>为什么这里不再检查 {@code sectionHookAlive}</b>：本方法只被
     * {@link #tiltBakedSection(Object, int)} 调用，而后者第一件事就是把
     * {@code sectionHookAlive} 置真 —— 原先写在下面的
     * {@code if (!sectionHookAlive) { warn(… NTE 逐截面矩阵钩子未生效 …) }}
     * 因此是一段<b>永远不可达</b>的死代码（这正是 ANTE 1.1.0 上
     * 「逐截面钩子没挂上」连一条告警都没有的原因之一）。该判断已经<b>整体搬到</b>
     * {@link #tickFrameWatchdog}：那是唯一一条「不需要截面钩子参与」的路径，
     * 因此也才是唯一能观察到「截面钩子缺席」的地方。
     * <p>
     * 保留在这里的三件事都仍然有意义：报告实际生效的<b>版本形态</b>（NTE 0.5.2 的
     * {@code lambda$new$1} 还是 ANTE 1.1.0 的 {@code <init>} 内联）、检查弧长相位钩子、
     * 以及记录「烘焙窗口内轨道为空」这种兜底情况。
     */
    private static void verifyHooksAlive(Rail rail) {
        if (bakeLivenessChecked) {
            return;
        }
        bakeLivenessChecked = true;
        Main.LOGGER.info("[P5-4] NTE 轨道烘焙窗口已建立（{} 生效），逐截面钩子形态 = {}；"
                        + "该轨道带滚转，开始逐截面旋转。",
                BAKED_RAIL_INIT, describeSectionHookForm());
        if (!RailTiltRenderHelper.wasSegmentHookAlive()) {
            Main.LOGGER.warn("[P5-4] 轨道截面弧长钩子未生效（Rail.render / renderSegment 注入失败）。"
                    + "NTE 的轨面仍会倾斜，但逐轨道超高（三点剖面）的相位可能落错位置。");
        }
        // 只为把「这条轨道确实有滚转」记进日志，参数本身不参与判断
        if (rail == null) {
            Main.LOGGER.warn("[P5-4] NTE 烘焙窗口内轨道为空：相位/滚转将按兜底值处理。");
        }
    }

    /**
     * 把「这次真正生效的是哪个版本形态」写成人话，供一次性诊断使用。
     * <p>
     * 为什么不写死一个选择器名字：NTE 0.5.2 用 {@code lambda$new$1}，ANTE 1.1.0 把同一个循环
     * 内联进了 {@code <init>}，两者<b>只有一处</b>会被真正注入；日志里报出与实际安装版本
     * 对不上的那个名字，会把排查方向直接带偏（这正是本次故障里「日志一直没动静」的代价）。
     */
    private static String describeSectionHookForm() {
        switch (sectionHookForm) {
            case SECTION_FORM_LAMBDA:
                return BAKED_RAIL_SECTION + "（NTE 0.5.2 形态）";
            case SECTION_FORM_INLINE:
                return BAKED_RAIL_SECTION_INLINE + " 内偏移 248 的 " + SECTION_ADD_TARGET
                        + "（ANTE 1.1.0 内联形态）";
            default:
                return "尚未观测到任何形态";
        }
    }

    /** 「矩阵类上没有 rotateZ(float)」的一次性告警 —— 这是「NTE 的变换无法表达滚转」的唯一现实失效方式。 */
    private static void warnRollTransformUnavailable(Class<?> matrixType, Throwable cause) {
        if (rollApiWarned) {
            return;
        }
        rollApiWarned = true;
        Main.LOGGER.warn("[P5-4] NTE 的截面矩阵类型 {} 上没有可用的 rotateZ(float)：无法把滚转写进"
                        + "NTE 的逐实例变换，NTE 路径的轨面不会倾斜（中心线与车体不受影响）。原因：{}",
                matrixType == null ? "?" : matrixType.getName(), String.valueOf(cause));
    }

    /**
     * 「第一次真的旋转成功」的一次性 INFO：路径 B 已接上的正向证据。
     * <p>
     * 刻意把<b>矩阵的运行时类名</b>打进日志：forgix 会把
     * {@code cn.} / {@code fabric.cn.} / {@code forge.cn.} 三套类同时塞进同一个 jar，
     * 只有运行时类名能告诉人类「这次游戏里真正生效的是哪一个前缀」。
     */
    private static void markTilted(Class<?> matrixType) {
        tiltedSections++;
        if (firstTiltLogged) {
            return;
        }
        firstTiltLogged = true;
        Main.LOGGER.info("[P5-4] NTE 路径的轨道横断面滚转已生效：逐截面矩阵（{}）已绕该处中心线按内核滚转角"
                        + "旋转；注入点是 {} → {}，实例化与 mesh 两条 chunk 路径共用同一批矩阵。",
                matrixType == null ? "?" : matrixType.getName(), BAKED_RAIL_INIT, describeSectionHookForm());
    }

    // ==================== 钩子 1 / 2：烘焙窗口 ====================

    /**
     * {@code BakedRail.<init>(Rail)} 的 {@code @At("HEAD")}：记下「正在烘焙哪条轨道」。
     * <p>
     * <b>严格只读</b>：只压一个 {@link ThreadLocal}，不碰构造器参数，也不碰目标对象的任何字段
     * （此刻 {@code coveredChunks} 还没初始化）。烘焙一定发生在客户端渲染线程，
     * 而这个 {@link ThreadLocal} 只被 {@link #tiltBakedSection(Object, int)}（同一个线程、同一次构造过程）读取。
     */
    public static void beginBake(Rail rail) {
        bakeHookAlive = true;
        BAKING_RAIL.set(rail);
    }

    /**
     * {@code BakedRail.<init>(Rail)} 的 {@code @At("RETURN")}：结束烘焙窗口。
     * <p>
     * 目标构造器有<b>两处</b> {@code return}（{@code modelKey.equals("null")} 时的早退与正常返回），
     * {@code @At("RETURN")} 会覆盖全部返回点，因此不会留下残值。
     */
    public static void endBake() {
        BAKING_RAIL.remove();
    }

    // ==================== 钩子 3a / 3b：逐截面旋转（两个版本形态） ====================

    /**
     * <b>形态 A</b>：{@code ArrayList.add(Object)} 的 {@code @ModifyArg} 处理器，目标
     * {@link #BAKED_RAIL_SECTION}（NTE 0.5.2 的 {@code lambda$new$1}）。
     * <p>
     * 把 NTE 刚造出来的截面矩阵按内核的滚转角就地旋转。
     *
     * @return <b>需要倾斜时返回被就地旋转的同一个对象；不需要时原样返回同一个对象</b>
     *         —— 无滚转轨道因此与原版逐位相同（同一个引用、同样的矩阵字节）。
     */
    public static Object tiltBakedSection(Object bakedSectionMatrix) {
        return tiltBakedSection(bakedSectionMatrix, SECTION_FORM_LAMBDA);
    }

    /**
     * <b>形态 B</b>：同一个 {@code ArrayList.add(Object)} 的 {@code @ModifyArg} 处理器，目标
     * {@link #BAKED_RAIL_SECTION_INLINE}（ANTE 1.1.0 把循环内联进去之后的 {@code BakedRail.<init>}）。
     * <p>
     * <b>为什么与形态 A 分成两个入口，而不是共用一个</b>：处理器本身完全一样
     * （都转调 {@link #tiltBakedSection(Object, int)}，安全不变量也完全一样），
     * 分开只是为了把「这次生效的是哪个版本形态」记进 {@link #sectionHookForm}，
     * 让一次性诊断能报出与玩家实际安装的 NTE/ANTE 对得上的选择器名字。
     * <p>
     * 两个形态<b>互斥</b>：NTE 0.5.2 的 {@code <init>} 里一处 {@code ArrayList.add} 都没有
     * （{@code javap -p -c} 核实：该版本全类唯一那处 {@code add} 在 {@code lambda$new$1} 偏移 93），
     * 所以形态 B 的注入器在 NTE 0.5.2 上会因为 {@code require = 0} 静默跳过、
     * <b>不会</b>额外旋转任何矩阵；反之 ANTE 1.1.0 上 {@code lambda$new$1} 不存在，
     * 形态 A 静默跳过。同一时间最多只有一个入口被调用。
     *
     * @return 同 {@link #tiltBakedSection(Object)}：不需要倾斜时返回入参本身。
     */
    public static Object tiltBakedSectionInline(Object bakedSectionMatrix) {
        return tiltBakedSection(bakedSectionMatrix, SECTION_FORM_INLINE);
    }

    /**
     * 两个形态共用的实现（<b>全类唯一</b>的截面旋转逻辑，安全不变量只在这里定义一次）。
     *
     * @param form 调用方属于哪个版本形态（{@link #SECTION_FORM_LAMBDA} / {@link #SECTION_FORM_INLINE}），
     *             只用于诊断文案与「第一次生效」的日志。
     */
    private static Object tiltBakedSection(Object bakedSectionMatrix, int form) {
        sectionHookAlive = true;
        if (sectionHookForm == SECTION_FORM_NONE) {
            sectionHookForm = form;
        }
        if (bakedSectionMatrix == null) {
            return null;
        }
        final Rail rail = BAKING_RAIL.get();
        if (rail == null) {
            return bakedSectionMatrix;
        }
        // frameFor 在「无姿态 / 无滚转 / 内核拒绝构建」时返回 null，
        // 且它在构建内核之前就返回，所以这里连内核都不会碰。
        final RailTiltRenderHelper.RollFrame frame = RailTiltRenderHelper.frameFor(rail);
        if (frame == null) {
            return bakedSectionMatrix;
        }
        verifyHooksAlive(rail);
        // 相位：与路径 A 完全同一个累加器（由 RailGeometryMixin 的相位钩子在 renderSegment 上推进），
        // 也就是 MTR 交给 Rail.getPositionY 的那个值。NTE 的烘焙同样经过 Rail.render → renderSegment。
        final double parameter = RailTiltRenderHelper.quadParameterRange();
        return tiltSectionMatrix(bakedSectionMatrix, frame, parameter);
    }

    /**
     * 纯函数核心：给定滚转快照与沿轨相位，把一个截面矩阵按轨道滚转移旋转。
     * <p>
     * 生产路径（{@link #tiltBakedSection(Object)} / {@link #tiltBakedSectionInline(Object)}）
     * 与探针共用这一个实现，因此
     * 「探针证明的性质」与「游戏里跑的性质」是同一段代码。
     *
     * @param matrix    NTE 的 {@code sowcer.math.Matrix4f}（以 {@code Object} 传入，
     *                  因为本 mixin 是可选目标，编译期不能出现 NTE 类型）
     * @param frame     滚转快照（与路径 A 同源，见 {@link RailTiltRenderHelper#frameFor}）
     * @param parameter 沿轨弧长参数（与 {@code Rail.getPositionY} 同参数化）
     * @return 需要倾斜时返回<b>被就地旋转的同一个对象</b>；否则返回入参本身
     */
    public static Object tiltSectionMatrix(Object matrix, RailTiltRenderHelper.RollFrame frame, double parameter) {
        if (matrix == null || frame == null || !Double.isFinite(parameter)) {
            return matrix;
        }
        final double roll = frame.core.getRollRadians(parameter);
        // 全类唯一的角度表达式在 RailTiltRenderHelper.railFrameAngle 里（与路径 A 是同一个数）
        final double angle = RailTiltRenderHelper.railFrameAngle(roll, frame.parameterIsPosition1ToPosition2);
        if (!Double.isFinite(angle) || angle == 0.0D) {
            return matrix;
        }
        final Method rotateZ = resolveRotateZ(matrix.getClass());
        if (rotateZ == null) {
            return matrix;
        }
        try {
            // 后乘 Rz：绕矩阵的局部原点（= 该截面中心线上的枢轴）与局部 Z 轴（= 轨道前进轴）旋转
            rotateZ.invoke(matrix, (float) angle);
        } catch (Throwable t) {
            warnRollTransformUnavailable(matrix.getClass(), t);
            return matrix;
        }
        markTilted(matrix.getClass());
        return matrix;
    }

    /**
     * 解析并缓存 {@code Matrix4f.rotateZ(float)}。
     * <p>
     * 用<b>实例的运行时类</b>查找，于是 {@code cn.}/{@code fabric.cn.}/{@code forge.cn.}
     * 三个前缀、乃至将来 NTE 换包名，都不需要在本类里再写一遍类名。
     * 失败只降级为「不倾斜」并打一条一次性告警，绝不抛异常。
     */
    private static Method resolveRotateZ(Class<?> matrixType) {
        if (matrixType == rotateZResolvedFor) {
            return rotateZMethod;
        }
        synchronized (ROTATE_LOCK) {
            if (matrixType == rotateZResolvedFor) {
                return rotateZMethod;
            }
            Method resolved = null;
            try {
                resolved = matrixType.getMethod("rotateZ", float.class);
            } catch (Throwable t) {
                warnRollTransformUnavailable(matrixType, t);
            }
            rotateZMethod = resolved;
            rotateZResolvedFor = matrixType;
            return resolved;
        }
    }

    // ==================== 供探针 / 诊断读取 ====================

    /** 供诊断读取：{@code BakedRail.<init>} 的钩子是否触发过。 */
    public static boolean wasBakeHookAlive() {
        return bakeHookAlive;
    }

    /** 供诊断读取：{@code ArrayList.add} 的 {@code @ModifyArg} 是否触发过（两个版本形态共用）。 */
    public static boolean wasSectionHookAlive() {
        return sectionHookAlive;
    }

    /**
     * 供诊断读取：截面钩子实际生效的是<b>哪个版本形态</b>
     * （{@link #SECTION_FORM_NONE} / {@link #SECTION_FORM_LAMBDA} / {@link #SECTION_FORM_INLINE}）。
     */
    public static int sectionHookForm() {
        return sectionHookForm;
    }

    /** 供诊断读取：真正旋转过多少个截面。 */
    public static int tiltedSectionCount() {
        return tiltedSections;
    }

    /** 供诊断读取：是否判定 NTE 在场（由 {@code MainClient} 告知）。 */
    public static boolean isNtePresent() {
        return ntePresent;
    }

    /** 供诊断读取：看门狗是否已经打出过「NTE 在场但从未烘焙」的告警。 */
    public static boolean wasWatchdogWarned() {
        return watchdogWarned;
    }

    /**
     * 探针专用：把看门狗帧计数推到阈值前一帧。
     * <p>
     * 用于在不开游戏的前提下验证「装了 NTE 但 {@code BakedRail} 钩子从未生效」这条告警路径
     * 真的会触发（阈值本身要 3600 帧，探针里推不动）。
     */
    public static void armWatchdogForProbe() {
        watchdogFrames = WATCHDOG_FRAMES - 1;
    }

    /** 探针专用：清零统计量，便于多次断言。 */
    public static void resetDiagnosticsForProbe() {
        presenceReported = false;
        ntePresent = false;
        bakeHookAlive = false;
        sectionHookAlive = false;
        sectionHookForm = SECTION_FORM_NONE;
        tiltedSections = 0;
        bakeLivenessChecked = false;
        firstTiltLogged = false;
        rollApiWarned = false;
        watchdogWarned = false;
        watchdogFrames = 0;
    }

    /** 探针专用：把 {@code rotateZ} 的缓存清掉，强制重新解析。 */
    public static void resetRotateZCacheForProbe() {
        synchronized (ROTATE_LOCK) {
            rotateZResolvedFor = null;
            rotateZMethod = null;
        }
    }
}
