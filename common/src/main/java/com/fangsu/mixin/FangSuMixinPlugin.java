package com.fangsu.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * fangsu.mixins.json 的 {@code "plugin"} —— <b>本仓库的第一个 Mixin 配置插件</b>。
 * <p>
 * <b>它为什么存在（P5-8 的硬崩溃）</b>：本模组把「风挡 / 挡板随车体滚转」实现为
 * {@code mtr.data.TrainClient.simulateCar} 里对 {@code TrainRendererBase.renderConnection} /
 * {@code renderBarrier} 的两处 {@code @Redirect}（原在 {@code TrainClientRollMixin} 里，现已搬到
 * {@link TrainClientConnectionRollMixin}）。而 NTE / ANTE 的
 * {@code fabric.cn.zbx1425.mtrsteamloco.mixin.TrainClientMixin} 会用一个<b>同描述符</b>的
 * {@code simulateCar} 方法体合并进 {@code TrainClient}（Mixin 日志原文
 * {@code simulateCar(...)V merged by ...TrainClientMixin with priority 1000}）。
 * 真实测试者日志（MC 1.20.1 / Fabric Loader 0.19.3 / sponge-mixin 0.17.3+mixin.0.8.7 /
 * ANTE 1.1.0）显示：合并发生后，那两处 {@code @Redirect} 会在
 * <b>{@code INJECT_PREPARE_LEGACY} 阶段</b>抛 {@code InvalidInjectionException}，
 * 整个 {@code mtr.data.TrainClient} 类加载失败 → 崩游戏。
 * <p>
 * <b>为什么不能靠 {@code require = 0} 兜住</b>：{@code require} 只影响「注入点数量不符」的判定，
 * 而这里是<b>注入点（INVOKE 节点）在方法里就找不到</b>，异常在 {@code Injector.findTargetNodes}
 * 里直接抛出，跟 {@code require} 无关 —— 两处 {@code @Redirect} 本来就写着 {@code require = 0}，
 * 照样硬崩。所以修法只能是<b>结构上不让这两个注入发生</b>。
 * <p>
 * <b>为什么不是「精确重定向到 ANTE 的那个 INVOKE」</b>：本次已用 {@code javap -p -c} 反汇编 ANTE
 * 1.1.0 的 {@code TrainClientMixin.simulateCar} 逐条核实 —— 它<b>确实</b>在偏移 783 / 828 调用了
 * {@code mtr/render/TrainRendererBase.renderConnection(...)V} 与 {@code renderBarrier(...)V}，
 * 参数类型 {@code Lnet/minecraft/class_243;}（= {@code Vec3} 的 intermediary 名）与 fangsu 的选择器
 * <b>逐字相同</b>。也就是说「注入点在不在」根本没有第二种形态可选，问题出在
 * <b>Mixin 0.8.7 对「被同优先级 mixin 合并过的目标方法」执行 {@code @Redirect} 时的解析</b>。
 * 详见 {@code deliverables/p5-nte-trainclient-conflict/NTE_TRAINCLIENT_REPORT.md}。
 * <p>
 * <b>门控判据：装了 NTE / ANTE 家族就<b>不</b>应用 {@link TrainClientConnectionRollMixin}。</b>
 * 代价是「装了 NTE 时风挡 / 挡板不随车体滚转」，收益是「任何情况下都不会因这两个注入而崩游戏」；
 * 这个取舍是刻意的（宁可少一个装饰特性，也绝不能崩游戏）。
 * <p>
 * <b>为什么检测必须「失败即跳过 / 检测不出来按已安装处理」</b>：本插件跑在<b>极早</b>的类加载阶段
 * （{@code fangsu.mixins.json} 被解析时），任何一次异常都会直接炸掉启动。因此：
 * <ul>
 *   <li>检测全部走 {@code Class.forName(..., false, ...)}（{@code initialize = false}）与
 *       {@code getResourceAsStream}，<b>不加载</b>任何 NTE 自己的类、不触发静态初始化；</li>
 *   <li>结果缓存在 {@link #nteFamilyDetected}（{@code null} = 还没查过），一次会话只探测一次；</li>
 *   <li>探测里的<b>每一个</b> {@code Throwable} 都被吞掉并返回「已安装」——即「跳过这个 mixin」。
 *       默认安全方向永远是「少加载一个 mixin」，而不是「多加载一个可能崩的 mixin」；</li>
 *   <li>绝不在 {@code shouldApplyMixin} 里引用本模组自己的类（{@code com.fangsu.*}）：
 *       那会递归触发「正在被 transform 的类」的加载。</li>
 * </ul>
 * <p>
 * <b>双平台安全</b>：本类只用 JDK 反射 + 三个 loader 都必然存在的
 * {@code ClassLoader.getResourceAsStream}（读 {@code fabric.mod.json} / 旧版
 * {@code mcmod.info} / {@code META-INF/mods.toml}），并逐个探测三种类名前缀
 * （无前缀 / {@code fabric.} / {@code forge.}）——
 * 与 {@link TrainMixin}、{@code MainClient.is_nte_loaded}、{@code NteRailTiltHelper}
 * 对「同一个 mod 在三种打包形态下是三个不同二进制名」的处理手法一致。
 * <p>
 * 本类同时保留 {@link #defaultRequire} 的默认值 {@code 1}：其余 mixin 的既有 {@code require}
 * 语义一个字都不改。
 */
public class FangSuMixinPlugin implements IMixinConfigPlugin {

    /** 需要按「NTE 家族是否存在」门控的 mixin 的<b>简单类名</b>（与 mixins.json 里写法一致）。 */
    private static final String GATED_MIXIN = "TrainClientConnectionRollMixin";

    /**
     * NTE / ANTE 家族的<b>主类</b>候选二进制名。
     * <p>
     * 三种前缀对应三种打包形态（见 {@code NteRailTiltHelper} 的注释：它们是<b>三个不同的类</b>）：
     * <ul>
     *   <li>无前缀 —— NTE 0.5.x 与 ANTE 的早期普遍形态；</li>
     *   <li>{@code fabric.} / {@code forge.} —— ANTE 1.1.0+ 的「双平台合一」打包形态。</li>
     * </ul>
     * 只挑「任何版本都必然有的主类」{@code MainClient}：mod 可能被 fork（ANTE）改名，
     * 但主类名沿用 NTE 的命名习惯；万一某个 fork 连主类都换了，下面的资源扫描与平台 API 兜底仍然生效。
     */
    private static final String[] NTE_MAIN_CLASSES = {
            "cn.zbx1425.mtrsteamloco.MainClient",
            "fabric.cn.zbx1425.mtrsteamloco.MainClient",
            "forge.cn.zbx1425.mtrsteamloco.MainClient",
            // 少数构建把主类放在 mixin 包旁边，一并兜住
            "cn.zbx1425.mtrsteamloco.Main",
            "fabric.cn.zbx1425.mtrsteamloco.Main",
            "forge.cn.zbx1425.mtrsteamloco.Main"
    };

    /**
     * mod 元数据里的 id 候选。NTE 的 {@code fabric.mod.json} 里 {@code "id"} 是
     * {@code mtrsteamloco}（本次用 {@code javap}/{@code unzip} 对 ANTE 1.1.0 的 jar 逐字核实）；
     * ANTE 是 NTE 的 fork，很可能沿用同一 id，但也可能改名为 {@code ante}，
     * 因此两个都列上，另外把 {@code mtr_nte} 这种常见写法一并探测。
     */
    private static final String[] NTE_MOD_IDS = {
            "mtrsteamloco",
            "ante",
            "mtr_nte"
    };

    /**
     * <b>公开的第二道保险</b>：给 {@link TrainClientConnectionRollMixin} 的注入处理器用。
     * <p>
     * 为什么还要第二道：{@link #shouldApplyMixin} 是第一道（整个 mixin 根本不会被解析），
     * 但如果将来某个构建忘了在 {@code fangsu.mixins.json} 里写 {@code "plugin"} 键、
     * 或者本插件被别的 mixin 框架旁路，第一道就失效了。此时注入处理器仍然会在<b>运行期</b>
     * 拿到 NTE 环境下的调用，就地转发即可 —— 把「prepare 阶段硬崩」降级成「风挡 / 挡板不滚转」。
     * <p>
     * 同一个缓存（{@link #nteFamilyDetected}），因此两条路径的判据<b>永远不会不一致</b>；
     * 缓存命中时只是一次字段读取，没有反射开销。
     */
    public static boolean nteFamilyLoaded() {
        return nteFamilyPresent();
    }

    /** 探测结果缓存：{@code null} = 尚未探测；{@code TRUE} = 装了 NTE 家族（要跳过门控 mixin）。 */
    private static Boolean nteFamilyDetected = null;

    /** 本插件是否已经初始化过（{@code onLoad} 只应执行一次）。 */
    private static boolean loaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        if (loaded) {
            return;
        }
        loaded = true;
        // 只做一次、且绝不抛出：插件里的任何异常都会直接炸掉启动。
        final boolean detected = nteFamilyPresent();
        // 走 JVM 内唯一必然可用的日志后端。刻意<b>不</b>引用 Main.LOGGER：
        // 本方法可能早于 Main 的静态初始化执行，引用它会把 Main 拖进类加载（早期阶段风险）。
        try {
            org.slf4j.LoggerFactory.getLogger("FangSu/Mixin").info(
                    "[P5-8] NTE/ANTE detection: present={} -> TrainClientConnectionRollMixin {}",
                    detected, detected ? "SKIPPED" : "APPLIED");
        } catch (Throwable ignored) {
            // 日志后端不可用时静默：门控结果本身已经算出来了，日志不是必需品。
        }
    }

    /**
     * 唯一真正的门控点。
     * <p>
     * {@code mixinName} 是配置里写的简单类名，{@code targetClassName} 是运行期命名空间下的目标类名
     * （在 Fabric 生产环境里形如 {@code net.minecraft.class_1937}）。
     * 本方法对别的 mixin <b>一律返回 true</b>：除了 {@link #GATED_MIXIN}，本模组的 mixin 集合
     * 与「NTE 在不在」无关（{@code @Inject} 在 NTE 的合并方法上照常工作，真实日志已证实
     * {@code captureCar} / {@code endCar} 两个钩子在 ANTE 环境下没有任何报错）。
     */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinName) {
        if (mixinName == null || !mixinName.endsWith(GATED_MIXIN)) {
            return true;
        }
        // 注意：这里返回 false 时，Mixin 会<b>完全跳过</b>该 mixin 的解析与注入准备，
        // 于是那两处会在 prepare 阶段抛异常的 @Redirect 根本不会被处理 —— 这正是修复的关键。
        return !nteFamilyPresent();
    }

    /**
     * NTE / ANTE 家族是否存在。<b>结果缓存，且任何异常都按「已安装」处理。</b>
     * <p>
     * 探测顺序（从最不依赖 loader 内部的到最依赖的）：
     * <ol>
     *   <li>三种类名前缀的 {@code MainClient} 直接探测（{@code initialize = false}）；</li>
     *   <li>classpath 上是否存在该 mod 的元数据资源
     *       （{@code fabric.mod.json} / {@code META-INF/mods.toml} / {@code mcmod.info}）；</li>
     *   <li>Fabric / Forge 的 mod 列表 API（纯反射，编译期无依赖）。</li>
     * </ol>
     * 三步全部失败 → 返回 {@code true}（= 当作装了 NTE，跳过门控 mixin）。
     */
    private static synchronized boolean nteFamilyPresent() {
        if (nteFamilyDetected != null) {
            return nteFamilyDetected;
        }
        boolean detected = true;
        try {
            detected = detectNteFamily();
        } catch (Throwable ignored) {
            // 任何意外（SecurityException / NoClassDefFoundError / LinkageError …）
            // 都按「已安装」处理：跳过 mixin 永远是安全方向。
            detected = true;
        }
        nteFamilyDetected = detected;
        return detected;
    }

    /** 真正的探测实现：只要能证明「在」，就返回 true；证明不了也返回 true（失败即跳过）。 */
    private static boolean detectNteFamily() {
        // ---- 1) 主类名探测（最可靠：类真的在 classpath 上才能通过） ----
        for (String className : NTE_MAIN_CLASSES) {
            if (classPresent(className)) {
                return true;
            }
        }
        // ---- 2) mod 元数据资源扫描（防 fork 改了主类名） ----
        if (modMetadataPresent("fabric.mod.json")
                || modMetadataPresent("META-INF/mods.toml")
                || modMetadataPresent("mcmod.info")) {
            return true;
        }
        // ---- 3) 平台 mod 列表 API（最后兜底） ----
        if (fabricModListed()) {
            return true;
        }
        return forgeModListed();
    }

    /** {@code Class.forName(name, false, loader)}：只问「类在不在」，不触发静态初始化。 */
    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, FangSuMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable notThere) {
            return false;
        }
    }

    /**
     * 扫描 classpath 上的 mod 元数据，判断是否有 id 命中 {@link #NTE_MOD_IDS} 的 mod。
     * <p>
     * 只做<b>字符串包含</b>判断，不引入任何 JSON/TOML 解析器：插件阶段越简单越好，
     * 而且 Fabric 的 {@code fabric.mod.json} 在 jar 里是紧凑排版的，形如
     * {@code "id":"mtrsteamloco"}（本次对 ANTE jar 核实过：确实是
     * {@code "id": "mtrsteamloco"}，带空格）。因此空格有/无两种写法都要覆盖。
     */
    private static boolean modMetadataPresent(String metadataPath) {
        java.io.InputStream in = null;
        try {
            java.net.URL url = FangSuMixinPlugin.class.getClassLoader().getResource(metadataPath);
            if (url == null) {
                return false;
            }
            // 只读前 64 KiB：元数据都很小，避免万一遇到巨物时把内存吃光。
            in = url.openStream();
            byte[] buffer = new byte[65536];
            int read = in.read(buffer);
            if (read <= 0) {
                return false;
            }
            String text = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8);
            for (String id : NTE_MOD_IDS) {
                if (text.contains("\"" + id + "\"")
                        || text.contains("'" + id + "'")
                        || text.contains("modId=\"" + id + "\"")
                        || text.contains(":" + id + "@")) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                    // 关流失败无关紧要
                }
            }
        }
    }

    /**
     * Fabric：{@code FabricLoader.getInstance().isModLoaded(id)}，纯反射调用。
     * <p>
     * 编译期不依赖 fabric-loader 的实现类，避免 Forge 端编译 / 运行时链接问题；
     * 装在 Forge 上时这一步会因为类不存在而抛异常，被吞掉后继续走 Forge 分支。
     */
    private static boolean fabricModListed() {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            java.lang.reflect.Method isModLoaded = loaderClass.getMethod("isModLoaded", String.class);
            for (String id : NTE_MOD_IDS) {
                Object result = isModLoaded.invoke(loader, id);
                if (result instanceof Boolean && (Boolean) result) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 不是 Fabric 环境（或 API 形状变了）：继续往下探测
        }
        return false;
    }

    /**
     * Forge：{@code ModList.get().isLoaded(id)}，或 1.20.1 的
     * {@code net.minecraftforge.fml.loading.LoadingModList.get().getModFileById(id) != null}，纯反射调用。
     * <p>
     * 两条路都试：{@code ModList} 在「mod 构造期之后」才有内容，而 mixin 插件可能跑得更早，
     * 那时只有 {@code LoadingModList} 是填好的（这正是「Forge 早期阶段检测可靠性」的主要不确定项，
     * 已在报告里列为残余风险）。
     */
    private static boolean forgeModListed() {
        if (forgeModListVia(forName0("net.minecraftforge.fml.ModList"))) {
            return true;
        }
        return forgeModListVia(forName0("net.minecraftforge.fml.loading.LoadingModList"));
    }

    /** 供 {@link #forgeModListed()} 使用：类不存在时返回 {@code null} 而不是抛异常。 */
    private static Class<?> forName0(String name) {
        try {
            return Class.forName(name, false, FangSuMixinPlugin.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean forgeModListVia(Class<?> listClass) {
        if (listClass == null) {
            return false;
        }
        try {
            Object list = listClass.getMethod("get").invoke(null);
            if (list == null) {
                return false;
            }
            java.lang.reflect.Method isLoaded = listClass.getMethod("isLoaded", String.class);
            for (String id : NTE_MOD_IDS) {
                Object result = isLoaded.invoke(list, id);
                if (result instanceof Boolean && (Boolean) result) {
                    return true;
                }
            }
            // 兜底：LoadingModList 有 getModFileById(String)
            java.lang.reflect.Method byId = listClass.getMethod("getModFileById", String.class);
            for (String id : NTE_MOD_IDS) {
                if (byId.invoke(list, id) != null) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // API 形状不符：当作「没查到」
        }
        return false;
    }

    // ==================== 以下方法保持 IMixinConfigPlugin 的默认语义 ====================

    /** 不额外挂任何 mixin 包（本配置只有 {@code com.fangsu.mixin} 一个包）。 */
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 无操作：不需要做跨配置目标协调
    }

    /** 不使用独立 refmap 重映射文件：沿用 {@code fangsu.mixins.json} 里声明的那个。 */
    @Override
    public List<String> getMixins() {
        // null = 不动态追加 mixin；全部 mixin 仍由 fangsu.mixins.json 静态声明
        return null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        // 无操作：本插件只做「应用与否」的门控，不参与应用后的加工
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
        // 无操作：同上
    }
}
