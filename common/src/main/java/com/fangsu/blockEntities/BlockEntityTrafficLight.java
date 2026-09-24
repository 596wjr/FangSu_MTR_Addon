package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.contents.TrafficLightContent;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.EnumConfig;
import com.fangsu.extraConfig.ProgramConfig;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.modular.*;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.shape.RotatableShapeHelper;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.ContentInfoUtil;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_TRAFFIC_LIGHT;

/**
 * 交通灯方块实体。
 * <p>
 * 移植自旧版 {@code traffic_light.js}：加载 {@code traffic_light/<mainModel>.json} 指定的内容，
 * 解析 {@code subModels} 映射，把分段 OBJ 模型的每个分组上传为独立的 {@link DynamicModelHolder},
 * 渲染时始终绘制主体子模型（main），并按灯状态绘制其余灯石子模型。
 * <p>
 * 当前阶段只移植「加载 + 渲染」，旧版脚本里的自定义程序（light.wait 等表达式）与
 * 配置界面暂缓实现，待后续再接入网络同步与动态开关逻辑。
 */
public class BlockEntityTrafficLight extends FunctionalObjBlockEntity {

    public static final String DEFAULT_MAIN_MODEL = "fangsu:traffic_light/basic_traffic_light.json";
    public static final String DEFAULT_SUB_MODEL = "trafficlight_3";
    public static final String MAIN_MODEL_KEY = "traffic_light";

    private TrafficLightContent content;

    /**
     * 「子模型标签 -> 已上传的动态模型」，标签如 main/red/yellow/green。
     */
    private Map<String, DynamicModelHolder> subModelDmhs = new LinkedHashMap<>();

    /**
     * 各灯标签的亮灭状态，默认全部熄灭（程序功能后续接入）。
     */
    private Map<String, Boolean> lightState = new ConcurrentHashMap<>();

    /**
     * 模型是否加载完成（服务端/未加载时保持 false）。
     */
    private boolean modelLoaded = false;

    /** 显示类型：program（程序控制）/ mtr（MTR 信号联动，占位）。 */
    private String dispType = "program";
    private static final String KEY_DISP_TYPE = "dispType";
    private static final String KEY_PROGRAM = "program";
    private static final String DEFAULT_PROGRAM = "";

    /** 程序运行时缓存（首次/配置变化后重建）。 */
    private TrafficLightProgram player = new TrafficLightProgram();

    public BlockEntityTrafficLight(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_TRAFFIC_LIGHT.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        markedError = false;
        modelLoaded = false;

        // whenLoading 可能改变形状，清除形状缓存使 setShape 重新计算
        RotatableShapeHelper.getInstance().removeCache(getLevel(), getWorldPos());

        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        // 服务端不需要加载模型，跳过客户端专属操作
        if (level == null || !level.isClientSide) return;

        try {
            TrafficLightContent newContent = ContentInfoUtil.getTrafficLightContent(mainModel, subModel);
            if (newContent == null) {
                markedError = true;
                return;
            }
            this.content = newContent;

            Map<String, RawModel> rawModels = ResourceUtil.loadPartedModel(
                    new ResourceLocation(content.getModel()), content.isFlipV());

            // 关闭旧的动态模型
            for (DynamicModelHolder dmh : subModelDmhs.values()) {
                if (dmh != null) dmh.close();
            }
            subModelDmhs.clear();

            for (Map.Entry<String, String> entry : content.getSubModels().entrySet()) {
                String label = entry.getKey();
                String part = entry.getValue();
                RawModel raw = rawModels.get(part);
                if (raw == null) continue;
                DynamicModelHolder dmh = new DynamicModelHolder();
                dmh.uploadLater(raw);
                subModelDmhs.put(label, dmh);
            }

            // 数码管显示段：按 displays 配置加载每个段名(digit_*)为独立动态模型，供按段亮灭绘制。
            if (content.getDisplays() != null) {
                for (Map.Entry<String, List<Map<String, String>>> ge : content.getDisplays().entrySet()) {
                    for (Map<String, String> digitSegs : ge.getValue()) {
                        for (Map.Entry<String, String> se : digitSegs.entrySet()) {
                            String segName = se.getValue();
                            if (segName == null || segName.isEmpty() || subModelDmhs.containsKey(segName)) continue;
                            RawModel raw = rawModels.get(segName);
                            if (raw == null) continue;
                            DynamicModelHolder dmh = new DynamicModelHolder();
                            dmh.uploadLater(raw);
                            subModelDmhs.put(segName, dmh);
                        }
                    }
                }
            }

            // 灯状态默认熄灭（程序功能后续实现）
            for (String label : content.getSubModels().keySet()) {
                if (!label.equals(content.getMainSubModel())) {
                    lightState.put(label, false);
                }
            }

            modelLoaded = true;

            // 加载已保存的图形化程序（从 NBT 恢复后应立即重载运行时，无需用户先打开配置界面）
            rebuildProgram();
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load traffic light content: {}", e.getMessage());
            if (level != null && level.isClientSide) markedError = true;
        }
    }

    @Override
    public void whenRendering() {
        if (markedError || !modelLoaded || content == null) return;

        // 驱动图形化程序（若配置过 dispType=program）
        if ("program".equals(dispType)) {
            drive();
        } else {
            // mtr 占位：保持现有静态状态
        }

        ObjBlockScriptContext ctx = this.scriptContext;
        // 遍历所有已上传子模型（灯石 main/r/y/g + 数码管段 digit_*），按 lightState 亮灭绘制。
        // main 常亮；其余仅在对应键为 true 时绘制。
        for (Map.Entry<String, DynamicModelHolder> e : subModelDmhs.entrySet()) {
            String label = e.getKey();
            boolean alwaysOn = label.equals(content.getMainSubModel());
            Boolean on = lightState.get(label);
            if (!alwaysOn && (on == null || !on)) continue;
            DynamicModelHolder dmh = e.getValue();
            if (dmh != null && dmh.getUploadedModel() != null) {
                ctx.drawModel(dmh, null);
            }
        }
    }

    @Override
    public void whenDisposing() {
        for (DynamicModelHolder dmh : subModelDmhs.values()) {
            if (dmh != null) dmh.close();
        }
        subModelDmhs.clear();
        super.whenDisposing();
    }

    @Override
    public String getMainModelKey() {
        return MAIN_MODEL_KEY;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("traffic_light", DEFAULT_SUB_MODEL));
        return infos;
    }

    // ==================== 配置界面 ====================

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        Map<String, String> extra = this.extraConfigs;

        // 显示类型：program / mtr 占位
        List<Component> dispEntries = List.of(
                ComponentHelper.translatable("modular.fangsu.tl.dispProgram"),
                ComponentHelper.translatable("modular.fangsu.tl.dispMtr")
        );
        List<String> dispVals = List.of("program", "mtr");
        configs.add(new EnumConfig(
                ComponentHelper.translatable("modular.fangsu.tl.dispType"),
                new ConfigSpec("list"),
                dispEntries,
                () -> Math.max(0, dispVals.indexOf(dispType)),
                idx -> {
                    dispType = dispVals.get(idx);
                    extra.put(KEY_DISP_TYPE, dispType);
                    setConfigChanged();
                }
        ).setSaveOnChange(true));

        // 图形化程序（白名单：由交通灯程序积木库提供钩子）
        configs.add(new ProgramConfig(
                ComponentHelper.translatable("modular.fangsu.tl.program"),
                new ConfigSpec("program"),
                () -> extra.getOrDefault(KEY_PROGRAM, DEFAULT_PROGRAM),
                json -> {
                    Main.LOGGER.info("[TrafficLight] program setter: receiving length={}", json == null ? -1 : json.length());
                    extra.put(KEY_PROGRAM, json);
                    setConfigChanged();
                },
                TrafficLightProgramBlocks.ALLOWED
        ));

        return configs;
    }

    /** 配置变化：重新解析程序并标记实体数据变更。 */
    private void setConfigChanged() {
        if (level != null && !level.isClientSide) {
            setChanged();
        }
        rebuildProgram();
    }    /** 由 whenRendering 每次调用；驱动程序与灯状态、倒计时。nowSeconds 用墙钟秒。 */
    private void drive() {
        player.tick(System.nanoTime() / 1e9);
        for (Map.Entry<String, Boolean> e : player.getLightStates().entrySet()) {
            lightState.put(e.getKey(), e.getValue());
        }
        applyDisplayStates();
    }
    /** 把解释器的显示组状态桥接进 lightState（键为段名，如 digit_r_1_a），供渲染按段亮灭。 */
    private void applyDisplayStates() {
        if (content == null || content.getDisplays() == null || player.getDisplayGroups() == null) return;
        for (Map.Entry<String, com.fangsu.modular.TrafficLightProgram.DisplayGroup> e : player.getDisplayGroups().entrySet()) {
            String group = e.getKey();
            com.fangsu.modular.TrafficLightProgram.DisplayGroup dg = e.getValue();
            List<Map<String, String>> digitSegs = content.getDisplays().get(group);
            if (digitSegs == null) continue;
            byte[] masks = dg.getMasks();
            boolean groupOn = dg.isOn();
            for (int digitIdx = 0; digitIdx < Math.min(masks.length, digitSegs.size()); digitIdx++) {
                Map<String, String> segMap = digitSegs.get(digitIdx);
                int m = masks[digitIdx] & 0xFF;
                for (Map.Entry<String, String> se : segMap.entrySet()) {
                    String segLetter = se.getKey();
                    String segName = se.getValue();
                    // 共阳 0=亮、1=灭；段字母 a..h 对应 bit0..bit7（hgfedcba 送数）。镜像时交换左右段。
                    int bit = bitIndex(segLetter, content.isDisplayMirrorX());
                    boolean lit = groupOn && bit >= 0 && ((m >> bit) & 1) == 0;
                    lightState.put(segName, lit);
                }
            }
        }
    }

    /** 段字母 a..h → bit0..bit7（hgfedcba 送数时，a=bit0）。
     * mirrorX 为 true 时做水平镜像交换：b↔f、c↔e（右上↔左上、右下↔左下），a/d/g/h 不变。 */
    private static int bitIndex(String segLetter, boolean mirrorX) {
        if (segLetter == null || segLetter.length() != 1) return -1;
        char c = Character.toLowerCase(segLetter.charAt(0));
        if (c < 'a' || c > 'h') return -1;
        int bit = c - 'a';
        if (mirrorX) {
            switch (bit) {
                case 1: return 5; // b(右上) -> f(左上)
                case 5: return 1; // f -> b
                case 2: return 4; // c(右下) -> e(左下)
                case 4: return 2; // e -> c
                default: return bit;
            }
        }
        return bit;
    }

    private void rebuildProgram() {
        String program = getExtraConfig(KEY_PROGRAM, DEFAULT_PROGRAM);
        java.util.Map<String, Integer> digits = displayDigits();
        Main.LOGGER.info("[TrafficLight] rebuildProgram: programLen={} digits={}",
                program == null ? -1 : program.length(), digits);
        player.reload(program, digits);
    }

    /** 各显示组(red/green)的显示位数，源自 displays 配置数组长度。 */
    private java.util.Map<String, Integer> displayDigits() {
        java.util.Map<String, Integer> digits = new java.util.LinkedHashMap<>();
        if (content != null && content.getDisplays() != null) {
            for (Map.Entry<String, List<Map<String, String>>> e : content.getDisplays().entrySet()) {
                digits.put(e.getKey(), e.getValue().size());
            }
        }
        if (digits.isEmpty()) {
            digits.put("red", 2);
            digits.put("green", 2);
        }
        return digits;
    }
}
