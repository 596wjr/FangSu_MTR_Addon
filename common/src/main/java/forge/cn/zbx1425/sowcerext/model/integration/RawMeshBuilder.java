package forge.cn.zbx1425.sowcerext.model.integration;

import forge.cn.zbx1425.sowcerext.model.RawModel;
import net.minecraft.resources.ResourceLocation;

public class RawMeshBuilder {
    private final RawModel mesh = new RawModel();

    public RawMeshBuilder(int expectedVertices, String renderType, ResourceLocation texture) {
    }

    public VertexBuilder vertex(double x, double y, double z) {
        mesh.addVertex((float) x, (float) y, (float) z);
        return new VertexBuilder(this);
    }

    public RawModel getMesh() {
        return mesh;
    }

    public static class VertexBuilder {
        private final RawMeshBuilder parent;

        VertexBuilder(RawMeshBuilder parent) {
            this.parent = parent;
        }

        public VertexBuilder normal(double x, double y, double z) {
            return this;
        }

        public VertexBuilder uv(double u, double v) {
            return this;
        }

        public RawMeshBuilder endVertex() {
            return parent;
        }
    }
}
