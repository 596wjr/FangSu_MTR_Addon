package com.fangsu.levelcrossing.sample;

/**
 * 本树的轨道采样源选择点。
 * <p>
 * <b>为什么要这个小文件</b>：{@link LevelCrossingSampler} 是纯算法，两树必须逐字节同源，
 * 因此它<b>不能</b>出现任何一版的实现类名。把"本树用哪个实现"这一件事单独关进这个文件，
 * 采样器里只留一行 {@code RailSampleSources.get()}。
 * <p>
 * 这也是整个 sample 包里<b>唯一</b>允许两树内容不同的文件；
 * {@code RailSampleSource} / {@code LevelCrossingSampler} 两份必须保持一致。
 */
public final class RailSampleSources {

    private RailSampleSources() {
    }

    /** 本树的轨道采样源实现（单例，由实现类自己持有）。 */
    public static RailSampleSource get() {
        return RailSampleSourceMtr3.getInstance();
    }
}
