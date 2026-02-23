package fabric.cn.zbx1425.sowcerext.model;

import java.util.ArrayList;
import java.util.List;

public class RawModel {
    private final List<float[]> vertices = new ArrayList<>();

    public void applyUVMirror(boolean mirrorU, boolean mirrorV) {
        // Embedded lightweight implementation keeps UV mirroring as a no-op fallback.
    }

    public void append(RawModel other) {
        vertices.addAll(other.vertices);
    }

    public void generateNormals() {
        // No-op in lightweight embedded renderer.
    }

    public void addVertex(float x, float y, float z) {
        vertices.add(new float[]{x, y, z});
    }

    public List<float[]> getVertices() {
        return vertices;
    }
}
