package com.fangsu.mtr.rail;

import com.fangsu.mappings.rail.RailGeometryCore;

/**
 * MTR3 轨道几何内核的持有者（由 {@code com.fangsu.mixin.RailGeometryMixin} 实现在
 * {@code mtr.data.Rail} 上）。
 * <p>
 * 存在的意义：内核里既有 {@code private final} 的输入，也有可缓存的包围盒，因此它必须挂在
 * {@code Rail} 实例上，而不能放静态表（会泄漏，且难以失效）。而 mixin 的 {@code @Unique} 字段
 * 只有该 mixin 能直接读，所以用这个接口把「按需重建并返回当前内核」暴露出来。
 * <p>
 * <b>语义</b>：{@link #getFangSuRailGeometryCore()} 每次调用都保证返回「与当前附加姿态一致」的
 * 内核（姿态变了就重建，没变就复用缓存）；调用方不需要也不应该自己判断缓存是否过期。
 * 当前姿态为默认、或几何退化到内核无意义时返回 {@code null}，此时调用方
 * （{@link RailGeometrySource#getY}）必须原样回退到 MTR 自己的值。
 */
public interface RailGeometryProvider {

    /** 当前与附加姿态一致的内核；无姿态 / 几何退化时为 {@code null}。 */
    RailGeometryCore getFangSuRailGeometryCore();

    /** 丢弃缓存的内核，使下一次 {@link #getFangSuRailGeometryCore()} 重建（姿态变更后必须调用）。 */
    void invalidateFangSuRailGeometry();
}
