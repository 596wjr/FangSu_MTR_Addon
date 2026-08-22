package com.fangsu.render.lift;

import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.lift.LiftAssemblyProperties.Part;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义电梯拼装器（MTR4 列车 properties 风格）。
 *
 * <p>系统按电梯宽/深/高自动生成一组"命名位置"（floor/wall/ceiling 的 corner/edge/middle 网格，
 * 以及 door_corner / door_left / door_right / door_top），用户只需在 properties 的每个部位里
 * 用 {@code positionDefinitions} 引用这些命名位置。拼装器把每个部位对应组模型放到这些位置上，
 * 门扇（door_left/door_right）独立输出供开合动画，DISPLAY 部位收集显示信息供楼层屏渲染。
 *
 * <p>坐标系与 MTR ModelLift1 一致：以轿厢中心为原点、Y 向下（地板 Y=0，天花板为负），
 * X/Z 居中，模型单位 1 格 = 16，格子大小 cell 默认 8。
 */
public class LiftModelAssembler {

    /** 电梯当前状态，供 GOING_UP / GOING_DOWN / STOPPED 条件判断。 */
    public static class LiftConditionContext {
        public static final LiftConditionContext NORMAL = new LiftConditionContext(false, false, false);
        public boolean goingUp;
        public boolean goingDown;
        public boolean stopped;

        public LiftConditionContext(boolean goingUp, boolean goingDown, boolean stopped) {
            this.goingUp = goingUp;
            this.goingDown = goingDown;
            this.stopped = stopped;
        }
    }

    /** 一个坐标（模型单位）。 */
    public static final class PartPosition {
        public final float x, y, z;
        public PartPosition(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** 显示屏部位信息（楼层号文字 / 上下行箭头）。 */
    public static final class DisplayInfo {
        public final PartPosition position;
        public final String displayType;
        public final String displayColor;
        public final String defaultText;
        public final boolean light;   // true = 灯/箭头，false = 文字屏
        public final String condition; // GOING_UP/GOING_DOWN 等，用于箭头方向

        public DisplayInfo(PartPosition position, String displayType, String displayColor, String defaultText, boolean light, String condition) {
            this.position = position;
            this.displayType = displayType;
            this.displayColor = displayColor;
            this.defaultText = defaultText;
            this.light = light;
            this.condition = condition;
        }
    }

    /** 拼装结果：静态部位 + 可滑动门扇 + 显示屏部位。 */
    public static class AssembledLift {
        public final RawModel body;
        @Nullable public final RawModel doorLeft;
        @Nullable public final RawModel doorRight;
        public final List<DisplayInfo> displays;

        AssembledLift(RawModel body, @Nullable RawModel doorLeft, @Nullable RawModel doorRight, List<DisplayInfo> displays) {
            this.body = body;
            this.doorLeft = doorLeft;
            this.doorRight = doorRight;
            this.displays = displays;
        }
    }

    private LiftModelAssembler() {
    }

    public static AssembledLift assemble(Map<String, RawModel> groups, LiftAssemblyProperties props,
                                         int width, int depth, int height, LiftConditionContext condition) {
        width = Math.max(width, props.getMinWidth());
        depth = Math.max(depth, props.getMinDepth());
        height = Math.max(height, props.getMinHeight());

        final float cell = props.getCellSize();
        final int cols = Math.max(1, Math.round(width * 16F / cell));
        final int rows = Math.max(1, Math.round(depth * 16F / cell));
        final int layers = Math.max(1, Math.round(height * 16F / cell));
        final float halfX = (cols - 1) * cell / 2F;
        final float halfZ = (rows - 1) * cell / 2F;
        final float ceilY = -layers * cell;

        final Map<String, List<PartPosition>> posDefs = generatePositions(cell, cols, rows, layers, halfX, halfZ, ceilY);

        RawModel body = new RawModel();
        RawModel doorLeft = null;
        RawModel doorRight = null;
        List<DisplayInfo> displays = new ArrayList<>();

        for (Part part : props.getParts()) {
            if (!conditionMet(part.condition, condition)) continue;
            if ("DISPLAY".equalsIgnoreCase(part.type) || "LIGHT".equalsIgnoreCase(part.type)) {
                final boolean light = "LIGHT".equalsIgnoreCase(part.type);
                for (LiftAssemblyProperties.PositionRef ref : part.positionDefinitions) {
                    for (PartPosition p : resolve(posDefs.getOrDefault(ref.name, Collections.emptyList()), ref.distance)) {
                        displays.add(new DisplayInfo(p, part.displayType, part.displayColor, part.displayDefaultText, light, part.condition));
                    }
                }
                continue;
            }

            boolean leftDoor = hasRef(part.positionDefinitions, "door_left");
            boolean rightDoor = hasRef(part.positionDefinitions, "door_right");
            if (leftDoor && doorLeft == null) doorLeft = new RawModel();
            if (rightDoor && doorRight == null) doorRight = new RawModel();

            for (LiftAssemblyProperties.PositionRef ref : part.positionDefinitions) {
                final RawModel target = leftDoor ? doorLeft : rightDoor ? doorRight : body;
                for (PartPosition p : resolve(posDefs.getOrDefault(ref.name, Collections.emptyList()), ref.distance)) {
                    for (String group : part.names) {
                        RawModel groupModel = groups.get(group);
                        if (groupModel == null) continue;
                        target.appendTransformed(groupModel, Matrix4f.translation(p.x, p.y, p.z), 0xFFFFFFFF, 0);
                    }
                }
            }
        }

        return new AssembledLift(body, doorLeft, doorRight, displays);
    }

    private static boolean hasRef(List<LiftAssemblyProperties.PositionRef> refs, String name) {
        for (LiftAssemblyProperties.PositionRef ref : refs) {
            if (name.equals(ref.name)) return true;
        }
        return false;
    }

    /** 应用 distance 偏移：向下偏移 distance 格（模型单位 16/格）。 */
    private static List<PartPosition> resolve(List<PartPosition> base, double distance) {
        if (base.isEmpty() || distance == 0) return base;
        List<PartPosition> out = new ArrayList<>(base.size());
        for (PartPosition p : base) {
            out.add(new PartPosition(p.x, p.y + (float) (distance * 16), p.z));
        }
        return out;
    }

    private static boolean conditionMet(String condition, LiftConditionContext ctx) {
        if (condition == null) return true;
        switch (condition.toUpperCase(java.util.Locale.ROOT)) {
            case "NORMAL":
                return true;
            case "GOING_UP":
                return ctx != null && ctx.goingUp;
            case "GOING_DOWN":
                return ctx != null && ctx.goingDown;
            case "STOPPED":
                return ctx != null && ctx.stopped;
            default:
                return true;
        }
    }

    // ==================== 位置生成 ====================

    private static Map<String, List<PartPosition>> generatePositions(float cell, int cols, int rows, int layers,
                                                                     float halfX, float halfZ, float ceilY) {
        final Map<String, List<PartPosition>> defs = new HashMap<>();
        for (String name : new String[]{
                "floor_corner", "floor_edge", "floor_middle",
                "ceiling_corner", "ceiling_edge", "ceiling_middle",
                "wall_corner", "wall_edge", "wall_middle",
                "door_corner", "door_left", "door_right", "door_top"}) {
            defs.put(name, new ArrayList<>());
        }

        // 地板 / 天花板网格
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                float x = i * cell - halfX;
                float z = j * cell - halfZ;
                String floorKind = classify(cols, rows, i, j, "floor");
                String ceilKind = classify(cols, rows, i, j, "ceiling");
                defs.get(floorKind).add(new PartPosition(x, 0, z));
                defs.get(ceilKind).add(new PartPosition(x, ceilY, z));
            }
        }

        // 前后墙（前墙 +Z 含门洞）
        final int doorCols = Math.min(Math.max(propsDoorCols(), 2), cols);
        final int doorRows = Math.min(Math.max(propsDoorRows(), 3), layers);
        final int doorStart = (cols - doorCols) / 2;
        for (int i = 0; i < cols; i++) {
            for (int y = 0; y < layers; y++) {
                float x = i * cell - halfX;
                float yy = -y * cell;
                boolean inDoor = i >= doorStart && i < doorStart + doorCols && y < doorRows;
                defs.get(classify1D(i, cols, "wall")).add(new PartPosition(x, yy, halfZ));
                if (inDoor) {
                    defs.get("door_corner").add(new PartPosition(x, yy, halfZ));
                } else {
                    defs.get(classify1D(i, cols, "wall")).add(new PartPosition(x, yy, -halfZ));
                }
            }
        }
        // 左右墙
        for (int j = 0; j < rows; j++) {
            for (int y = 0; y < layers; y++) {
                float z = j * cell - halfZ;
                float yy = -y * cell;
                defs.get(classify1D(j, rows, "wall")).add(new PartPosition(-halfX, yy, z));
                defs.get(classify1D(j, rows, "wall")).add(new PartPosition(halfX, yy, z));
            }
        }

        // 门扇 / 门顶
        float doorX = (doorStart + doorCols / 2F) * cell - halfX;
        float zDoor = halfZ - cell / 2F;
        for (int y = 0; y < doorRows; y++) {
            defs.get("door_left").add(new PartPosition(doorX - cell * (doorCols / 2F), -y * cell, zDoor));
            defs.get("door_right").add(new PartPosition(doorX + cell * ((doorCols - 1) / 2F), -y * cell, zDoor));
        }
        defs.get("door_top").add(new PartPosition(doorX, -doorRows * cell, halfZ));

        return defs;
    }

    // 用占位门参数（v1 简化：门默认 2 列 3 行，后续可配置）
    private static int propsDoorCols() { return 2; }
    private static int propsDoorRows() { return 3; }

    private static String classify(int cols, int rows, int i, int j, String prefix) {
        boolean corner = (i == 0 || i == cols - 1) && (j == 0 || j == rows - 1);
        boolean edge = (i == 0 || i == cols - 1) || (j == 0 || j == rows - 1);
        return prefix + (corner ? "_corner" : edge ? "_edge" : "_middle");
    }

    private static String classify1D(int i, int n, String prefix) {
        return prefix + (i == 0 || i == n - 1 ? "_corner" : "_edge");
    }
}
