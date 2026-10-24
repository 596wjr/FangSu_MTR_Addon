package com.fangsu.levelcrossing;

import net.minecraft.resources.ResourceLocation;

/**
 * 铺面样式。样式只决定贴图，不影响几何，因此可以做成纯枚举。
 */
public enum LevelCrossingStyles {
    ASPHALT("asphalt"),
    CONCRETE("concrete"),
    WOOD("wood");

    /** 钢轨贴图（所有样式共用；<b>兜底</b>：mtrsteamloco/fsrail 包没加载时用这张）。 */
    public static final ResourceLocation RAIL_TEXTURE =
            new ResourceLocation("fangsu", "textures/block/level_crossing_rail.png");
    /**
     * 钢轨<b>顶面</b>贴图：128×16 的条带（沿轨方向 × 横向），顶面（轨面/窄条顶/槽底/封口水平片）用它。
     * <p>
     * <b>必须是「全路径」形式：含 {@code textures/} 与 {@code .png}</b>。
     * <p>
     * 依据：{@code MaterialProp.setupCompositeState()} 走的是
     * {@code RenderSystem.setShaderTexture(0, texture)}，它把 RL 直接当贴图路径用，
     * 不会自动补 {@code textures/} 前缀或 {@code .png} 后缀。本文件其它能正常显示的贴图
     * （{@link #RAIL_TEXTURE}、{@link #GROUND_TEXTURE}、{@code topTexture()}）全是这个写法。
     * <p>
     * 历史坑（两轮都栽在这里）：第 42 轮写成 {@code mtrsteamloco:fsrail/rail_top}（缺 {@code textures/} 与 {@code .png}）；
     * 第 43 轮改成 {@code fangsu:block/lc_rail_top}（同样缺）→ 两次都显示成错误贴图。
     * 更隐蔽的是存在性检查当时用的是全路径、绑定用的是短路径，两者不一致 → 回退也不会触发。
     */
    public static final ResourceLocation RAIL_TOP_TEXTURE =
            new ResourceLocation("fangsu", "textures/block/lc_rail_top.png");
    /**
     * 钢轨<b>侧面</b>贴图：128×128 高分辨率（整张锈迹），只用于<b>竖直面</b>（槽壁 / 竖直外墙 / 端面）。
     * <p>
     * 用户口径（第 57 轮）：只有 {@link #RAIL_TOP_TEXTURE} 是「完整 0→1」，{@code rail_side} 依旧是
     * <b>每块随机偏移 + 裁切窗口</b>（uOff/vOff 随机、uSize/vSize 裁切），所以 0.045 宽的槽壁不会整片发棕。
     */
    public static final ResourceLocation RAIL_SIDE_TEXTURE =
            new ResourceLocation("fangsu", "textures/block/lc_rail_side.png");

    // ==================== 钢轨 UV 分区（按桶区分） ====================
    // · 水平面（railTop 桶，材质 rail_top 128×16）：v 原样 0→1（深-浅-深完整条带），u 沿轨 + 随机相位；
    // · 竖直面（railSide 桶，材质 rail_side 128×128）：随机窗口（uOff/vOff 随机 + uSize/vSize 裁切）。
    // 下面两个常量只用于历史断言/文档参考，不再参与 UV 计算（竖直面已改用 rail_side 的随机窗口）。
    public static final float RAIL_DARK_V_MIN = 0.0F;
    public static final float RAIL_DARK_V_MAX = 0.13F;
    public static final float RAIL_FACE_V_MIN = 0.18F;
    public static final float RAIL_FACE_V_MAX = 0.82F;

    /** 钢轨贴图是否已经检查过。 */
    private static volatile boolean railTexturesChecked;
    /** 钢轨贴图缺失 → 发射时两桶都回退 {@link #RAIL_TEXTURE}，绝不显示错误贴图。 */
    private static volatile boolean railTexturesMissing;

    public static boolean railTexturesMissing() {
        return railTexturesMissing;
    }

    /** 钢轨<b>顶面</b>材质（水平面：轨面 / 内板顶 / 槽底）：缺图时回退兜底贴图。 */
    public static ResourceLocation railTopTexture() {
        return railTexturesMissing ? RAIL_TEXTURE : RAIL_TOP_TEXTURE;
    }

    /** 钢轨<b>侧面</b>材质（竖直面：槽壁 / 外壳 / 端面）：缺图时回退兜底贴图。 */
    public static ResourceLocation railSideTexture() {
        return railTexturesMissing ? RAIL_TEXTURE : RAIL_SIDE_TEXTURE;
    }

    /**
     * 首次使用前检查两张贴图是否存在（缺则 warn 一次并置 {@link #railTexturesMissing}）。
     * <p>
     * 只在<b>主线程</b>问资源管理器（{@code getResourceManager().getResource} 不适合工作线程）；
     * 非主线程/取不到 Minecraft 时退回「类路径探测」——它检查的正是我们 jar 里自带的那张 png，
     * 线程安全且不依赖资源包状态，足够判断「我们自己有没有把文件放对」。
     */
    public static void ensureRailTextures() {
        if (railTexturesChecked) return;
        boolean missing;
        try {
            missing = !resourceExists("block/lc_rail_top") || !resourceExists("block/lc_rail_side");
        } catch (Throwable t) {
            // 服务端没有 Minecraft 类：直接按类路径判断（下面 resourceExists 已含该降级）
            missing = false;
        }
        if (missing) {
            railTexturesMissing = true;
            if (!warnedMissing) {
                warnedMissing = true;
                com.fangsu.Main.LOGGER.warn("[levelcrossing] rail texture missing: {} / {}",
                        RAIL_TOP_TEXTURE, RAIL_SIDE_TEXTURE);
            }
        } else {
            railTexturesMissing = false;
        }
        railTexturesChecked = true;
    }

    private static volatile boolean warnedMissing;

    // ==================== 像素风过滤（关掉 mipmap / 线性过滤） ====================
    // 用户现场：远处铺面糊成平滑渐变 + 块状斑。这是 MC 默认的 mipmap + 双线性过滤在掠射角的表现，
    // 而我们要的是「每格 16×16 像素」的硬边像素风，所以对本方块用到的贴图调用 setFilter(false, false)。
    // <b>不改 MaterialProp.setupCompositeState 的默认行为</b>（那会影响所有方速方块，包括 OBJ 模型），
    // 只在本方块首次构建/首次渲染前逐张处理一次。
    private static final java.util.Set<String> FILTERED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile boolean filterWarned;

    /** 本方块用到的全部贴图（含兜底与三种样式）。 */
    private static java.util.List<ResourceLocation> allTextures() {
        java.util.List<ResourceLocation> all = new java.util.ArrayList<>();
        all.add(RAIL_TOP_TEXTURE);
        all.add(RAIL_SIDE_TEXTURE);
        all.add(GROUND_TEXTURE);
        all.add(RAIL_TEXTURE);
        for (LevelCrossingStyles style : values()) {
            all.add(style.topTexture());
        }
        return all;
    }

    /**
     * 把本方块用到的贴图全部设为「无 mipmap + 无线性过滤」（像素风硬边）。
     * <p>
     * 必须在<b>主线程 / GL 线程</b>调用（{@code applyComputedResult} 就是主线程）；每个 RL 只处理一次；
     * 任何异常只 debug 一次，绝不影响渲染（贴图未加载时 getTexture 会创建占位）。
     */
    public static void ensureNearestFilter() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.getTextureManager() == null) return;
            for (ResourceLocation rl : allTextures()) {
                if (rl == null) continue;
                if (!FILTERED.add(rl.toString())) continue;
                try {
                    // 1.20.1 的 AbstractTexture 只有 setFilter(blur, mipmap)；
                    // setBlurMipmap 在该版本不存在（1.18 前后被移除），不要再用。
                    mc.getTextureManager().getTexture(rl).setFilter(false, false);
                } catch (Throwable failed) {
                    if (!filterWarned) {
                        filterWarned = true;
                        com.fangsu.Main.LOGGER.debug(
                                "[levelcrossing] nearest filter unavailable: {}", failed.toString());
                    }
                }
            }
        } catch (Throwable t) {
            if (!filterWarned) {
                filterWarned = true;
                com.fangsu.Main.LOGGER.debug("[levelcrossing] nearest filter skipped: {}", t.toString());
            }
        }
    }

    /** 自检用：已处理过的贴图数量。 */
    public static int filteredCount() {
        return FILTERED.size();
    }

    /** 自检用：清空去重集合。 */
    public static void resetFilterCacheForTest() {
        FILTERED.clear();
        filterWarned = false;
    }

    /** 自检/调试用：直接置「缺图」状态，验证回退路径（正式路径由 {@link #ensureRailTextures()} 设置）。 */
    public static void setRailTexturesMissingForTest(boolean missing) {
        railTexturesMissing = missing;
        railTexturesChecked = true;
    }

    /** 贴图是否存在：先在主线程问资源管理器，否则用类路径探测（同一个 jar 内文件）。 */
    private static boolean resourceExists(String pathWithoutExt) {
        String resPath = "textures/" + pathWithoutExt + ".png";
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getResourceManager() != null && mc.isSameThread()) {
                return mc.getResourceManager()
                        .getResource(new ResourceLocation("fangsu", resPath)).isPresent();
            }
        } catch (Throwable ignored) {
            // 非主线程 / 服务端 / 类缺失：走下面的类路径探测
        }
        return LevelCrossingStyles.class.getResource("/assets/fangsu/" + resPath) != null;
    }
    /** 地面层贴图（铺面之外的底色）。 */
    public static final ResourceLocation GROUND_TEXTURE =
            new ResourceLocation("fangsu", "textures/block/level_crossing_ground.png");

    private final String id;

    LevelCrossingStyles(String id) {
        this.id = id;
    }

    /** 存档/配置里使用的稳定 id，不要用 name()，否则以后改名会读坏旧存档。 */
    public String id() {
        return id;
    }

    public ResourceLocation topTexture() {
        return new ResourceLocation("fangsu", "textures/block/level_crossing_" + id + ".png");
    }

    /** 宽容解析：未知 id 一律回落到沥青，避免旧存档或手改配置把方块变成空样式。 */
    public static LevelCrossingStyles byId(String id) {
        if (id != null) {
            for (LevelCrossingStyles style : values()) {
                if (style.id.equals(id)) return style;
            }
        }
        return ASPHALT;
    }
}
