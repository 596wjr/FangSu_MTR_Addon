package com.fangsu.train.connector;

import com.fangsu.MainClient;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.data.TrainClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import mtr.client.TrainClientRegistry;
import mtr.mappings.UtilitiesClient;
import mtr.model.ModelBogie;
import mtr.render.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/** Connector 在外，LCD 在内；基础 renderer 的车体、挡板保持委托。 */
public final class ConnectorTrainRendererMtr3 extends TrainRendererBase {
    public final TrainRendererBase baseRenderer;
    private final String key;
    private final TrainClient train;
    private ConnectorSet meshSet;
    private final java.util.Map<ConnectorDef,ConnectorGeometry.Mesh> meshes=new java.util.IdentityHashMap<>();
    private ConnectorGeometry.Mesh mesh;
    private final double[][] corners=new double[8][3];
    private static final ModelBogie JACOBS=new ModelBogie();
    public ConnectorTrainRendererMtr3(TrainRendererBase base,String key) { this(base,key,null); }
    private ConnectorTrainRendererMtr3(TrainRendererBase base,String key,TrainClient train) { baseRenderer=base; this.key=key; this.train=train; }
    @Override public TrainRendererBase createTrainInstance(TrainClient t) { return new ConnectorTrainRendererMtr3(baseRenderer==null?null:baseRenderer.createTrainInstance(t),key,t); }
    @Override public void renderCar(int car,double x,double y,double z,float yaw,float pitch,boolean left,boolean right) { if(baseRenderer!=null) baseRenderer.renderCar(car,x,y,z,yaw,pitch,left,right); }
    @Override public void renderBarrier(Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 e,Vec3 f,Vec3 g,Vec3 h,double x,double y,double z,float yaw,float pitch) { if(baseRenderer!=null) baseRenderer.renderBarrier(a,b,c,d,e,f,g,h,x,y,z,yaw,pitch); }
    @Override public void renderConnection(Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 e,Vec3 f,Vec3 g,Vec3 h,double x,double y,double z,float yaw,float pitch) {
        ConnectorSet set=ConnectorRegistry.train(key);
        if(set!=null && train!=null && !MainClient.is_nte_loaded) {
            boolean pushed=false;
            try {
                Vec3[] input={a,b,c,d,e,f,g,h};
                for(int i=0;i<8;i++){corners[i][0]=input[i].x;corners[i][1]=input[i].y;corners[i][2]=input[i].z;}
                if(meshSet!=set){meshSet=set;meshes.clear();}
                double distance=ConnectorGeometry.distance(corners);
                for(ConnectorDef def:set.definitions) {
                    float[] profile=ConnectorFoldProfile.get(def);
                    ConnectorGeometry.Mesh target=meshes.computeIfAbsent(def,k->new ConnectorGeometry.Mesh(def));
                    if(profile==null || !ConnectorGeometry.build(def,corners,profile,ConnectorRegistry.nominal(def,distance),target))throw new IllegalStateException("风挡配置或几何不可用");
                }
                BlockPos pos=applyAverageTransform(train.getViewOffset(),x,y,z);if(pos==null)return;pushed=true;
                int light=LightTexture.pack(world.getBrightness(LightLayer.BLOCK,pos),world.getBrightness(LightLayer.SKY,pos));
                int inside=train.getIsOnRoute()?RenderTrains.MAX_LIGHT_INTERIOR:light;
                drawNativeLayers(set.mask,a,b,c,d,e,f,g,h,light,inside);
                for(ConnectorDef def:set.definitions) {
                    mesh=meshes.get(def);
                    if((def.mask()&ConnectorSet.EXTERIOR)!=0)draw(def,vertexConsumers.getBuffer(MoreRenderLayers.getExterior(def.textureFor(false))),false,def.light.equals("interior")?inside:light);
                    if((def.mask()&ConnectorSet.INTERIOR)!=0)draw(def,vertexConsumers.getBuffer(MoreRenderLayers.getInterior(def.textureFor(true))),true,def.light.equals("interior")?inside:light);
                }
                // 原生 renderConnection 兼画 Jacobs 转向架，替换风挡后仍需保留。
                if(TrainClientRegistry.getTrainProperties(train.trainId).isJacobsBogie) {
                    matrices.pushPose();
                    try {
                        matrices.translate(x,y,z); UtilitiesClient.rotateY(matrices,(float)Math.PI+yaw);
                        UtilitiesClient.rotateX(matrices,(float)Math.PI+((pitch<0?train.transportMode.hasPitchAscending:train.transportMode.hasPitchDescending)?pitch:0));
                        JACOBS.render(matrices,vertexConsumers,light,0);
                    } finally { matrices.popPose(); }
                }
                return;
            } catch(Exception ex) { ConnectorRegistry.warn(key+":render","connector " + key + " 绘制失败，回退原生: " + ex.getMessage()); }
            finally { if(pushed) matrices.popPose(); }
        }
        if(baseRenderer!=null) baseRenderer.renderConnection(a,b,c,d,e,f,g,h,x,y,z,yaw,pitch);
    }
    /** 与 JonModelTrainRenderer 相同的四个内侧平面及 side/roof/floor 原生贴图。 */
    private void drawNativeLayers(int replaced,Vec3 p1,Vec3 p2,Vec3 p3,Vec3 p4,Vec3 t1,Vec3 t2,Vec3 t3,Vec3 t4,int exteriorLight,int light) {
        if(replaced==ConnectorSet.BOTH || baseRenderer==null)return;
        TrainRendererBase renderer=baseRenderer;
        while(renderer instanceof com.fangsu.train.FunctionalTrainRenderer wrapper) renderer=wrapper.baseRenderer;
        if(!(renderer instanceof JonModelTrainRenderer jon)) throw new IllegalStateException("原生内饰 renderer 不可用");
        if((replaced&ConnectorSet.EXTERIOR)==0) {
            ResourceLocation exterior=jon.resolveTexture(jon.gangwayConnectionId,id->id+"_connector_exterior.png");
            VertexConsumer buffer=vertexConsumers.getBuffer(MoreRenderLayers.getExterior(exterior));
            flat(buffer,t2,p3,p4,t1,exteriorLight);flat(buffer,p2,t3,t4,p1,exteriorLight);
            flat(buffer,p3,t2,t3,p2,exteriorLight);flat(buffer,p1,t4,t1,p4,exteriorLight);
        }
        if((replaced&ConnectorSet.INTERIOR)!=0)return;
        ResourceLocation side=jon.resolveTexture(jon.gangwayConnectionId,id->id+"_connector_side.png");
        ResourceLocation roof=jon.resolveTexture(jon.gangwayConnectionId,id->id+"_connector_roof.png");
        ResourceLocation floor=jon.resolveTexture(jon.gangwayConnectionId,id->id+"_connector_floor.png");
        flat(vertexConsumers.getBuffer(MoreRenderLayers.getInterior(side)),t3,p2,p1,t4,light);
        flat(vertexConsumers.getBuffer(MoreRenderLayers.getInterior(side)),p3,t2,t1,p4,light);
        flat(vertexConsumers.getBuffer(MoreRenderLayers.getInterior(roof)),p2,t3,t2,p3,light);
        flat(vertexConsumers.getBuffer(MoreRenderLayers.getInterior(floor)),p4,t1,t4,p1,light);
    }
    private void flat(VertexConsumer c,Vec3 a,Vec3 b,Vec3 e,Vec3 f,int light) {
        mtr.client.IDrawing.drawTexture(matrices,c,(float)a.x,(float)a.y,(float)a.z,(float)b.x,(float)b.y,(float)b.z,
                (float)e.x,(float)e.y,(float)e.z,(float)f.x,(float)f.y,(float)f.z,0,0,1,1,Direction.UP,-1,light);
    }
    private void draw(ConnectorDef d,VertexConsumer consumer,boolean inner,int light) {
        boolean reverse=mesh.reverse^inner;
        int n=d.points.length;
        for(int j=0;j<mesh.ringCount-1;j++) for(int i=0;i<(d.closed?n:n-1);i++) {
            int next=(i+1)%n;
            double[] normal=mesh.normals[j]; int ni=i*3;
            float sign=inner?-1:1;
            float nx=(float)normal[ni]*sign,ny=(float)normal[ni+1]*sign,nz=(float)normal[ni+2]*sign;
            int i0=reverse?next:i, i1=reverse?i:next;
            double v0=ConnectorGeometry.v(d,reverse?i+1:i),v1=ConnectorGeometry.v(d,reverse?i:i+1);
            vertex(consumer,d,j,i0,v0,light,false,nx,ny,nz);
            vertex(consumer,d,j+1,i0,v0,light,true,nx,ny,nz);
            vertex(consumer,d,j+1,i1,v1,light,true,nx,ny,nz);
            vertex(consumer,d,j,i1,v1,light,false,nx,ny,nz);
        }
    }
    private void vertex(VertexConsumer c,ConnectorDef d,int j,int i,double v,int light,boolean end,float nx,float ny,float nz) {
        double[] p=mesh.rings[j]; int ix=i*3;
        double u=d.perSegment ? d.uRange[end?1:0] : ConnectorGeometry.u(d,mesh.axis[j],i);
        c.vertex(matrices.last().pose(),(float)p[ix],(float)p[ix+1],(float)p[ix+2]).color(255,255,255,255)
                .uv((float)u,(float)v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(matrices.last().normal(),nx,ny,nz).endVertex();
    }
}
