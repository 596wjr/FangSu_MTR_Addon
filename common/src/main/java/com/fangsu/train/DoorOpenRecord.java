package com.fangsu.train;

import java.util.Arrays;

/**
 * 一条「站台 + 列车配置」的开门方向观测记录。
 * <p>
 * 语义：{@code sides} 按车节下标（0 = 靠发车车头的那一节）保存观测到的开门方向；
 * {@code reversed} 只是「最近一次观测时的反向状态」注解，<b>不参与匹配</b>，查询方自己的车反向时自行
 * {@link DoorOpenSide#toOpposite(DoorOpenSide)}。存储/下发/快照中<b>禁止出现 {@code UNKNOWN}</b>。
 * <p>
 * 注意：record 自动生成的 {@code equals} 对数组字段做引用比较，比较内容请用 {@link #samePayload(DoorOpenRecord)}。
 */
public record DoorOpenRecord(
        DoorOpenSide[] sides,      // 下标 = 车节索引；长度 = 观测到的编组数
        String trainTypeId,        // MTR3: trainId（空则 baseTrainType）；MTR4: 首车 vehicleId
        int trainCars,
        int lengthMeters,          // 总长度（米，四舍五入）
        boolean reversed,          // 最近一次观测时的反向状态（仅注解）
        long observedAt            // epoch millis（服务端盖章）
) {

    public static enum DoorOpenSide {
        LEFT, RIGHT, BOTH, NONE, UNKNOWN;

        /** 左右互换；BOTH/NONE/UNKNOWN 原样返回（保留用户原有实现）。 */
        public DoorOpenSide toOpposite() {
            return switch (this) {
                case LEFT -> RIGHT;
                case RIGHT -> LEFT;
                default -> this;
            };
        }

        /** 静态版本，便于服务端/网络层以函数式方式调用；null 视为 UNKNOWN。 */
        public static DoorOpenSide toOpposite(DoorOpenSide side) {
            return side == null ? UNKNOWN : side.toOpposite();
        }

        /** 由「左开 / 右开」两个布尔值归一为单值：两侧 -> BOTH，单侧 -> LEFT/RIGHT，都 false -> NONE。 */
        public static DoorOpenSide of(boolean left, boolean right) {
            if (left && right) return BOTH;
            if (left) return LEFT;
            if (right) return RIGHT;
            return NONE;
        }

        /** 按 ordinal 还原，越界（含负数）一律返回 UNKNOWN，绝不抛异常。 */
        public static DoorOpenSide fromOrdinalSafe(int ordinal) {
            DoorOpenSide[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : UNKNOWN;
        }

        /** 是否允许入库/上线/进快照：只有 LEFT/RIGHT/BOTH/NONE 允许，UNKNOWN 与 null 不允许。 */
        public static boolean isStorable(DoorOpenSide side) {
            return side != null && side != UNKNOWN;
        }
    }

    /**
     * 紧凑构造器：防御性拷贝数组，保证记录不可变（外部传入的数组之后被改动不会影响本记录）。
     * null 数组归一为空数组，null 型号归一为空串，避免后续判空分支。
     */
    public DoorOpenRecord {
        sides = sides == null ? new DoorOpenSide[0] : sides.clone();
        trainTypeId = trainTypeId == null ? "" : trainTypeId;
    }

    /** 访问器返回拷贝，防止调用方改到内部数组；类内部一律直接读字段 {@code sides}。 */
    @Override
    public DoorOpenSide[] sides() {
        return sides.clone();
    }

    /**
     * 判断本记录是否适用于给定列车配置：型号相同、车节数相同、长度差不超过 2 米。
     * 反向状态不参与匹配。
     */
    public boolean matchesConsist(String type, int cars, int lengthMeters) {
        if (type == null || !trainTypeId.equals(type)) return false;
        if (trainCars != cars) return false;
        return Math.abs(this.lengthMeters - lengthMeters) <= 2;
    }

    /**
     * 返回「按发车车头、非反向帧」的规范记录：{@code reversed} 为真时逐元素取反，否则原样返回 this。
     * {@code reversed} 字段本身保留，仍作为「最近一次观测时的反向状态」注解。
     */
    public DoorOpenRecord normalized() {
        if (!reversed) return this;
        DoorOpenSide[] flipped = new DoorOpenSide[sides.length];
        for (int i = 0; i < sides.length; i++) {
            flipped[i] = DoorOpenSide.toOpposite(sides[i]);
        }
        return new DoorOpenRecord(flipped, trainTypeId, trainCars, lengthMeters, true, observedAt);
    }

    /**
     * 取某车节的开门方向。下标越界不返回 UNKNOWN：负数夹到首节，超出取末尾元素；
     * 末尾也没有合法值时兜底 NONE。
     */
    public DoorOpenSide sideAt(int carIndex) {
        if (sides.length == 0) return DoorOpenSide.NONE;
        int index = carIndex;
        if (index < 0) {
            index = 0;
        } else if (index >= sides.length) {
            index = sides.length - 1;
        }
        DoorOpenSide side = sides[index];
        return DoorOpenSide.isStorable(side) ? side : DoorOpenSide.NONE;
    }

    /** 是否含有 UNKNOWN（或 null）元素；入库/下发前应拒绝这类记录。 */
    public boolean hasUnknownSide() {
        for (DoorOpenSide side : sides) {
            if (!DoorOpenSide.isStorable(side)) return true;
        }
        return false;
    }

    /**
     * 比较「载荷」是否相同：列车配置字段逐个比较 + sides 逐元素比较。
     * <p>
     * 刻意<b>不比较</b> {@code observedAt}：它是服务端盖章的时间戳，用于 30s 去重广播的正是「载荷」，
     * 若把时间戳算进来则永远不会相等。
     */
    public boolean samePayload(DoorOpenRecord other) {
        if (other == null) return false;
        if (!trainTypeId.equals(other.trainTypeId)) return false;
        if (trainCars != other.trainCars) return false;
        if (lengthMeters != other.lengthMeters) return false;
        if (reversed != other.reversed) return false;
        return Arrays.equals(sides, other.sides);
    }
}
