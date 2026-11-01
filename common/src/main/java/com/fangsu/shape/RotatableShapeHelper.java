package com.fangsu.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ShapeCollection 的统一旋转入口，支持同一方块的多个独立形状。 */
public final class RotatableShapeHelper {
    private static final RotatableShapeHelper INSTANCE = new RotatableShapeHelper();
    private record Key(Level level, long pos, Object slot) { }
    private record Source(ShapeCollection collection, List<AABB> boxes) { }
    private final Map<Key, Source> sources = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Source> eldest) {
            if (size() <= ShapeRotationCache.capacity()) return false;
            ShapeRotationCache.remove(eldest.getKey().level, eldest.getKey().pos, eldest.getKey().slot);
            return true;
        }
    };

    public static RotatableShapeHelper getInstance() { return INSTANCE; }

    public synchronized void initForBlock(@Nullable Level level, BlockPos pos,
                                          float tx, float ty, float tz, float rx, float ry, float rz,
                                          ShapeCollection shape) {
        List<AABB> boxes = shape.toAabbs();
        sources.put(new Key(level, pos.asLong(), "collection"), new Source(shape, boxes));
        getShapeForBlock(level, pos, tx, ty, tz, rx, ry, rz);
    }

    public synchronized VoxelShape getOrInitShapeForBlock(@Nullable Level level, BlockPos pos, ShapeCollection shape,
                                                          float tx, float ty, float tz, float rx, float ry, float rz) {
        if (shape == null || shape.isEmpty()) return Shapes.empty();
        Source source = sources.get(new Key(level, pos.asLong(), "collection"));
        if (source == null || source.collection != shape) initForBlock(level, pos, tx, ty, tz, rx, ry, rz, shape);
        return getShapeForBlock(level, pos, tx, ty, tz, rx, ry, rz);
    }

    /**
     * 多部件方块使用固定 slot（如主体/门扇/标志分段）区分缓存。
     * 旋转围绕方块中心，位移由调用方叠加；形状实例变化时重新生成快照。
     */
    public synchronized VoxelShape getRotatedShape(@Nullable Level level, BlockPos pos, Object slot,
                                                   ShapeCollection shape, float rx, float ry, float rz) {
        if (shape == null || shape.isEmpty()) return Shapes.empty();
        Key key = new Key(level, pos.asLong(), slot);
        Source source = sources.get(key);
        if (source == null || source.collection != shape) {
            source = new Source(shape, shape.toAabbs());
            sources.put(key, source);
        }
        return ShapeRotationCache.get(slot, source.boxes, Vec3.ZERO, rx, ry, rz,
                CollisionShapeContext.current(level, pos.asLong()));
    }

    @Nullable
    public synchronized VoxelShape getShapeForBlock(@Nullable Level level, BlockPos pos,
                                                    float tx, float ty, float tz, float rx, float ry, float rz) {
        Source source = sources.get(new Key(level, pos.asLong(), "collection"));
        if (source == null) return null;
        // 位移由调用方 move 叠加，不参与旋转缓存，纯平移不会重复细分。
        return ShapeRotationCache.get("collection", source.boxes, Vec3.ZERO, rx, ry, rz,
                CollisionShapeContext.current(level, pos.asLong()));
    }

    public synchronized void removeCache(@Nullable Level level, BlockPos pos) {
        sources.keySet().removeIf(key -> key.level == level && key.pos == pos.asLong());
        ShapeRotationCache.remove(level, pos.asLong());
    }

    public synchronized void clearAll() {
        sources.clear();
        ShapeRotationCache.clear();
    }
}
