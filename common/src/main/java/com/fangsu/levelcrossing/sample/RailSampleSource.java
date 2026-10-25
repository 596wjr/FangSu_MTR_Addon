package com.fangsu.levelcrossing.sample;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 轨道几何的版本适配缝：平交路口采样器只认这个接口，不认识任何一版 MTR 的具体类型。
 * <p>
 * 之所以要有这层缝：MTR3 的轨道是 {@code mtr.data.Rail}（{@code getPosition(double)} / {@code getLength()}），
 * MTR4 换成了 {@code railWrapperList} + {@code Rail.railMath}，两版的类型完全不能互相赋值。
 * 采样算法（命中判定、主轨选择、俯仰/超高求解）与版本无关，因此留在
 * {@link LevelCrossingSampler}，只有"怎么枚举轨道"这一件事下沉到本接口的实现里。
 */
public interface RailSampleSource {

    /**
     * 列出中心点附近的轨道。
     *
     * @param centerX 世界坐标 X（格）
     * @param centerZ 世界坐标 Z（格）
     * @param radius  水平搜索半径（格）
     */
    List<RailPath> nearby(Level level, double centerX, double centerZ, double radius);

    /**
     * 一条候选轨道。
     *
     * @param railId 用于反查 {@code LevelCrossingGaugeTable} 的轨道 id
     * @param handle 交给采样器读几何的对象，必须实现 {@link RailGeometry}
     */
    record RailPath(String railId, Object handle) {
    }

    /**
     * 版本无关的轨道几何读取口。
     * <p>
     * 放在这个文件里而不是另开一个顶层文件，是为了让 P2 的新增文件清单保持最小——
     * 它只服务于 {@link RailPath#handle()}，生命周期与 {@link RailPath} 完全一致。
     */
    interface RailGeometry {

        /** 轨道总长（格）。 */
        double length();

        /**
         * 弧长参数处的世界坐标点。
         * <p>
         * {@code t} 会被采样器钳到 {@code [0, length]}；越界行为由实现自己兜住。
         */
        Vec3 position(double t);

        /** 轨道起点（MTR 的 position1 语义，用于读节点姿态）。 */
        BlockPos pos1();

        /** 轨道终点（MTR 的 position2 语义，用于读节点姿态）。 */
        BlockPos pos2();

        /** 旧消费者使用的平均滚转值（度）；逐位置断面对接请使用下面的带参数入口。 */
        double tiltDegrees();

        /** 指定轨道原生参数处的滚转；旧采样消费者继续使用上面的平均值接口。 */
        default double tiltDegrees(double distance) { return tiltDegrees(); }
    }
}
