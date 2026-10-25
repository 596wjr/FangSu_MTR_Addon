package com.fangsu.render.sowcerext.model.integration;

import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcerext.model.RawMesh;
import com.fangsu.render.sowcerext.model.Face;
import java.nio.FloatBuffer;
import com.fangsu.render.sowcerext.model.Vertex;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.ArrayList;
import java.util.List;

public class FaceList {
    private final RenderType renderType;
    private final boolean needSorting;
    private final List<TransformedFace> queuedFaces = new ArrayList<>();
    private final List<MeshCall> queuedMeshes=new ArrayList<>();

    public boolean addUnsortedMesh(RawMesh mesh,Matrix4f matrix,int color,int light) {
        if(needSorting||!mesh.isUploadPrepared())return false;
        queuedMeshes.add(new MeshCall(mesh,matrix,color,light));return true;
    }

    private static final class MeshCall {
        final RawMesh mesh;
        final float[] matrix=new float[16];
        final int color,light;
        MeshCall(RawMesh mesh,Matrix4f pose,int color,int light) {
            this.mesh=mesh;this.color=color;this.light=light;pose.store(FloatBuffer.wrap(matrix));
        }
        void write(VertexConsumer out) {
            float[] m=matrix;
            for(Face face:mesh.faces)for(int index:face.vertices) {
                Vertex vertex=mesh.vertices.get(index);
                float x=vertex.position.x(),y=vertex.position.y(),z=vertex.position.z();
                float nx=vertex.normal.x(),ny=vertex.normal.y(),nz=vertex.normal.z();
                out.vertex(m[0]*x+m[4]*y+m[8]*z+m[12],m[1]*x+m[5]*y+m[9]*z+m[13],m[2]*x+m[6]*y+m[10]*z+m[14])
                        .color((color>>>24)&255,(color>>>16)&255,(color>>>8)&255,color&255)
                        .uv(vertex.u,vertex.v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(m[0]*nx+m[4]*ny+m[8]*nz,m[1]*nx+m[5]*ny+m[9]*nz,m[2]*nx+m[6]*ny+m[10]*nz).endVertex();
            }
        }
    }

    public FaceList(RenderType renderType, boolean needSorting) {
        this.renderType = renderType;
        this.needSorting = needSorting;
    }

    public void addFace(Vertex[] vertices, int color, int light) {
        queuedFaces.add(new TransformedFace(vertices, color, light));
    }

    public void commit(MultiBufferSource bufferSource) {
        try { commitFaces(bufferSource); }
        finally { queuedMeshes.clear();queuedFaces.clear(); }
    }

    private void commitFaces(MultiBufferSource bufferSource) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
        for(MeshCall mesh:queuedMeshes)mesh.write(vertexConsumer);
        if (needSorting) {
            queuedFaces.sort((a, b) ->
                    -Float.compare(a.sortingVector.distanceSq(Vector3f.ZERO), b.sortingVector.distanceSq(Vector3f.ZERO))
            );
        }
        for (TransformedFace face : queuedFaces) {
            for (Vertex vertex : face.vertices) {
                vertexConsumer
                        .vertex(vertex.position.x(), vertex.position.y(), vertex.position.z())
                        .color((byte) (face.color >>> 24), (byte) (face.color >>> 16), (byte) (face.color >>> 8), (byte) (int) face.color)
                        .uv(vertex.u, vertex.v)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(face.light)
                        .normal(vertex.normal.x(), vertex.normal.y(), vertex.normal.z())
                        .endVertex();
            }
        }
    }

    private static class TransformedFace {
        private final Vertex[] vertices;
        private final Vector3f sortingVector;
        int color;
        int light;

        public TransformedFace(Vertex[] vertices, int color, int light) {
            this.vertices = vertices;
            this.sortingVector = new Vector3f(0, 0, 0);
            for (Vertex vertex : vertices) this.sortingVector.add(vertex.position);
            this.color = color;
            this.light = light;
        }
    }
}
