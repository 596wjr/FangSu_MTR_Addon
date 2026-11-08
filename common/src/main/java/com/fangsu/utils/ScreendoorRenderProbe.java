package com.fangsu.utils;

import com.fangsu.Main;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** 按需诊断：-Dfangsu.screendoorProfile=true。关闭时不计时、不记录、不打日志。 */
public final class ScreendoorRenderProbe {
    private static final boolean ENABLED = Boolean.getBoolean("fangsu.screendoorProfile");
    private static final LongAdder submitted = new LongAdder(), started = new LongAdder(), completed = new LongAdder();
    private static final LongAdder glass = new LongAdder(), reuse = new LongAdder(), auto = new LongAdder();
    private static final LongAdder rebuild = new LongAdder(), notify = new LongAdder(), packets = new LongAdder();
    private static final LongAdder uploads = new LongAdder(), waitNs = new LongAdder(), runNs = new LongAdder();
    private static final AtomicLong maxWait = new AtomicLong(), maxRun = new AtomicLong();
    // 以下字段仅由渲染线程在 DrawContext.resetFrameProfiler 中访问。
    private static final long[] intervals = new long[256];
    private static long lastFrame, windowStart, frames, intervalNs, maxInterval, draws, blazeFaces;
    private static long previousGcCount, previousGcMs;

    private ScreendoorRenderProbe() { }
    public static long submitted(boolean isGlass) {
        if (!ENABLED) return 0;
        submitted.increment();
        if (isGlass) glass.increment();
        return System.nanoTime();
    }
    public static long started(long queuedAt) {
        if (!ENABLED) return 0;
        long now = System.nanoTime(), elapsed = now - queuedAt;
        started.increment(); waitNs.add(elapsed); maxWait.accumulateAndGet(elapsed, Math::max);
        return now;
    }
    public static void completed(long startedAt) {
        if (!ENABLED) return;
        long elapsed = System.nanoTime() - startedAt;
        runNs.add(elapsed); maxRun.accumulateAndGet(elapsed, Math::max); completed.increment();
    }
    public static void reused() { if (ENABLED) reuse.increment(); }
    public static void autoRecomputed() { if (ENABLED) auto.increment(); }
    public static void neighborsNotified() { if (ENABLED) notify.increment(); }
    public static void shapeRebuilt() { if (ENABLED) rebuild.increment(); }
    public static void packetSent() { if (ENABLED) packets.increment(); }
    public static void modelUploaded() { if (ENABLED) uploads.increment(); }

    /** 世界渲染采样间隔包含限帧等待；不是 GPU 时间。绘制计数来自方速全局 DrawContext。 */
    public static void frame(int drawCalls, int directFaces) {
        if (!ENABLED) return;
        long now = System.nanoTime();
        if (lastFrame == 0) {
            lastFrame = windowStart = now;
            for (java.lang.management.GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                previousGcCount += Math.max(0, bean.getCollectionCount());
                previousGcMs += Math.max(0, bean.getCollectionTime());
            }
            return;
        }
        long delta = now - lastFrame; lastFrame = now;
        frames++; intervalNs += delta; maxInterval = Math.max(maxInterval, delta);
        intervals[(int) Math.min(255, delta / 1_000_000)]++;
        draws += drawCalls; blazeFaces += directFaces;
        if (now - windowStart < 5_000_000_000L) return;
        long target = (long) Math.ceil(frames * 0.95), sum = 0;
        int p95 = 0;
        while (p95 < 255 && (sum += intervals[p95]) < target) p95++;
        long gcCount = 0, gcMs = 0;
        for (java.lang.management.GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, bean.getCollectionCount()); gcMs += Math.max(0, bean.getCollectionTime());
        }
        long done = completed.sum(), begun = started.sum(), sent = submitted.sum();
        Main.LOGGER.info("[screendoor-profile] windowMs={} frames={} intervalAvgMs={} intervalP95BucketMs={} intervalMaxMs={} sowcerDrawsPerFrame={} blazeFacesPerFrame={} submittedTotal={} glassTotal={} queuedNow={} runningNow={} waitAvgMs={} waitMaxMs={} runAvgMs={} runMaxMs={} reuseTotal={} autoTotal={} rebuildTotal={} notifyTotal={} c2sTotal={} uploadTotal={} gcCountDelta={} gcMsDelta={}",
                (now-windowStart)/1_000_000, frames, intervalNs/1e6/frames, p95, maxInterval/1e6,
                (double)draws/frames, (double)blazeFaces/frames, sent, glass.sum(), com.fangsu.blockEntities.BaseObjBlockEntity.getRenderingQueueSize(), begun-done,
                waitNs.sum()/1e6/Math.max(1,begun), maxWait.get()/1e6,
                runNs.sum()/1e6/Math.max(1,done), maxRun.get()/1e6,
                reuse.sum(), auto.sum(), rebuild.sum(), notify.sum(), packets.sum(), uploads.sum(),
                gcCount-previousGcCount, gcMs-previousGcMs);
        previousGcCount = gcCount; previousGcMs = gcMs;
        java.util.Arrays.fill(intervals, 0);
        windowStart = now; frames = intervalNs = maxInterval = draws = blazeFaces = 0;
    }
}
