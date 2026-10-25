package com.fangsu.turnout;

import org.jetbrains.annotations.Nullable;

/** 校验结果。七种合法拓扑均携带完整绑定，失败结果绝不写入方块。 */
public record TurnoutValidation(TurnoutKind kind, String reasonKey, @Nullable TurnoutBinding binding) {

    /** 标记不是节点 / 颜色计数不合法 */
    public static final String HINT_INVALID_NODES = "fangsu.turnout.hint.invalid_nodes";
    /** 节点在服务端轨道表里没有连接，或两节点之间没有轨道 */
    public static final String HINT_NO_RAIL = "fangsu.turnout.hint.no_rail";
    /** 客户端尚未同步或服务端权威查询失败，均拒绝放置 */
    public static final String HINT_NO_RAIL_DATA = "fangsu.turnout.hint.no_rail_data";
    /** 标记节点离道岔方块超过 32 格 */
    public static final String HINT_TOO_FAR = "fangsu.turnout.hint.too_far";
    /** 原版放置条件不满足 */
    public static final String HINT_CANNOT_PLACE = "fangsu.turnout.hint.cannot_place";
    public static final String HINT_BAD_ANGLE = "fangsu.turnout.hint.bad_angle";
    /** 识别出拓扑但暂未支持（%s = 类型名，见 {@link TurnoutKind#langKey()}） */
    public static final String HINT_UNSUPPORTED = "fangsu.turnout.hint.unsupported";
    /** 构建成功提示 */
    public static final String BUILT = "fangsu.turnout.built";

    public static TurnoutValidation ok(TurnoutBinding binding) {
        return new TurnoutValidation(binding.kind(), BUILT, binding);
    }

    public static TurnoutValidation fail(TurnoutKind kind, String reasonKey) {
        return new TurnoutValidation(kind, reasonKey, null);
    }

    /** 识别出拓扑但暂未支持：只回 unsupported，不写绑定。 */
    public static TurnoutValidation unsupported(TurnoutKind kind) {
        return fail(kind, HINT_UNSUPPORTED);
    }

    /** 是否可直接写绑定。 */
    public boolean isSupported() {
        return kind != TurnoutKind.INVALID && binding != null;
    }
}
