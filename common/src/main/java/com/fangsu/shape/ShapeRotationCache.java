package com.fangsu.shape;

import com.fangsu.Main;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;

/** Shape 旋转的有界异步缓存，连续变换在停止 300ms 后合并计算。 */
public final class ShapeRotationCache {
    private static volatile int capacity = Math.max(1, Math.min(4096, Integer.getInteger("fangsu.shapeCacheSize", 512)));
    private static final ScheduledThreadPoolExecutor WORKER = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "FangSu-Shape-Compute");
        thread.setDaemon(true);
        return thread;
    });
    static { WORKER.setRemoveOnCancelPolicy(true); }

    // Level 使用对象身份，隔离单人游戏客户端/服务端以及不同世界的同坐标。
    private record Key(Level level, long pos, Object slot) { }
    private record Request(List<AABB> boxes, Vec3 origin, float rx, float ry, float rz,
                           boolean quality, double step, String revision) { }
    private static final class Entry {
        final Request request;
        final VoxelShape fallback;
        volatile VoxelShape result;
        Future<?> pending;
        Entry(Request request) {
            this.request = request;
            fallback = ShapeRotation.coarse(request.boxes, request.origin, request.rx, request.ry, request.rz);
        }
        void cancel() { if (pending != null) pending.cancel(true); }
    }
    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(128, 0.75f, true);

    private ShapeRotationCache() { }

    public static synchronized VoxelShape get(Object slot, List<AABB> boxes, Vec3 origin,
                                               float rx, float ry, float rz,
                                               CollisionShapeContext.Settings settings) {
        Key key = new Key(settings.level(), settings.pos(), slot);
        Request request = new Request(boxes.stream().filter(ShapeRotation::validBox).toList(), origin, rx, ry, rz,
                settings.quality(), settings.step(), settings.revision());
        Entry entry = CACHE.get(key);
        if (entry == null || !entry.request.equals(request)) {
            if (entry != null) entry.cancel();
            entry = new Entry(request);
            CACHE.put(key, entry);
            Entry submitted = entry;
            // 简化模式或正交旋转无需精细细分，但仍在后台合并原始多盒，避免主线程大规模布尔运算。
            entry.pending = WORKER.schedule(() -> {
                try {
                    VoxelShape shape = ShapeRotation.build(request.boxes, request.origin,
                            request.rx, request.ry, request.rz, request.quality, request.step);
                    if (!Thread.currentThread().isInterrupted()) submitted.result = shape;
                } catch (CancellationException ignored) {
                    // 过期任务正常退出，不写回任何新条目。
                } catch (Exception e) {
                    Main.LOGGER.warn("Failed to rotate collision shape; using coarse shape", e);
                }
            }, settings.delayMillis(), TimeUnit.MILLISECONDS);
            while (CACHE.size() > capacity) {
                var iterator = CACHE.entrySet().iterator();
                Entry removed = iterator.next().getValue();
                iterator.remove();
                removed.cancel();
            }
        }
        return entry.result == null ? entry.fallback : entry.result;
    }

    public static synchronized void remove(Level level, long pos) {
        var iterator = CACHE.entrySet().iterator();
        while (iterator.hasNext()) {
            var item = iterator.next();
            if (item.getKey().level == level && item.getKey().pos == pos) {
                item.getValue().cancel();
                iterator.remove();
            }
        }
    }

    /** 只淘汰一个部件，保留同一方块其他部件的已完成结果。 */
    public static synchronized void remove(Level level, long pos, Object slot) {
        Entry removed = CACHE.remove(new Key(level, pos, slot));
        if (removed != null) removed.cancel();
    }

    public static int capacity() { return capacity; }

    public static synchronized void setCapacity(int value) {
        if (value < 1) throw new IllegalArgumentException("capacity must be >= 1");
        capacity = Math.min(4096, value);
        clear();
    }

    public static synchronized void clear() {
        CACHE.values().forEach(Entry::cancel);
        CACHE.clear();
    }
}
