package com.fangsu.render.model.integration;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class RawMeshBuilder {
    private final List<float[]> vertices = new ArrayList<>();

    public RawMeshBuilder(int expectedVertices, String material, ResourceLocation texture) {
    }

    public VertexBuilder vertex(double x, double y, double z) {
        return new VertexBuilder(this, (float) x, (float) y, (float) z);
    }

    public Object getMesh() { return vertices; }

    public static class VertexBuilder {
        private final RawMeshBuilder owner;
        private float x, y, z, nx, ny, nz, u, v;

        private VertexBuilder(RawMeshBuilder owner, float x, float y, float z) {
            this.owner = owner;
            this.x = x; this.y = y; this.z = z;
        }

        public VertexBuilder normal(float nx, float ny, float nz) { this.nx = nx; this.ny = ny; this.nz = nz; return this; }
        public VertexBuilder uv(float u, float v) { this.u = u; this.v = v; return this; }
        public RawMeshBuilder endVertex() {
            owner.vertices.add(new float[]{x, y, z, nx, ny, nz, u, v});
            return owner;
        }
    }
}
