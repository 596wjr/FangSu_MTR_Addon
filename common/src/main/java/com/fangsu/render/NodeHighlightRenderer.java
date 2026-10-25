package com.fangsu.render;

import com.fangsu.Main;
import com.fangsu.items.ItemDisplacementTool;
import com.fangsu.turnout.TurnoutTools;
import com.fangsu.turnout.marker.MarkerColor;
import com.fangsu.turnout.marker.NodeMarkerStore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;

/** 节点线框与真正的实心圆盘。每一种顶点格式独立写完、提交后才切换缓冲。 */
public final class NodeHighlightRenderer {
    private static final long MAX_DRAW_DISTANCE_SQUARED=64L*64;
    private static final float MARKER_RADIUS=0.25F,MARKER_Y_OFFSET=1F;
    private static final int MARKER_SEGMENTS=32;
    // 只用公开构造器；不依赖 RenderType 私有 create 的平台访问转换。
    private static final RenderType FILL=new RenderType("fangsu_turnout_marker_fill",
            DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.TRIANGLES,4096,false,false,()->{
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.disableBlend();RenderSystem.enableDepthTest();RenderSystem.depthFunc(515);
                RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.colorMask(true,true,true,true);
            },()->{RenderSystem.depthMask(true);RenderSystem.enableCull();}) { };
    private static boolean warned;
    private static Set<BlockPos> visibleNodes=Set.of();
    private static BlockPos nodeOrigin;
    private static net.minecraft.resources.ResourceLocation nodeDimension;
    private static long lastNodeTick=Long.MIN_VALUE;
    private NodeHighlightRenderer() { }
    public static void renderLevel(PoseStack matrices,Camera camera,MultiBufferSource.BufferSource buffers) {
        Player player=Minecraft.getInstance().player;if(player==null)return;
        boolean marking=TurnoutTools.showNodes(player);
        if(!marking&&!holdingTool(player))return;
        matrices.pushPose();Vec3 eye=camera.getPosition();matrices.translate(-eye.x,-eye.y,-eye.z);
        try {
            VertexConsumer lines=buffers.getBuffer(RenderType.lines());Set<BlockPos> nodes=nearbyNodes(player);
            for(BlockPos pos:nodes)if(pos!=null&&player.blockPosition().distSqr(pos)<=MAX_DRAW_DISTANCE_SQUARED)drawNodeBox(lines,matrices.last(),pos);
            buffers.endBatch(RenderType.lines());
            if(marking) {
                // getBuffer 会结束共享 builder 的上一批；填充全部完成前不获取其他 RenderType。
                VertexConsumer fill=buffers.getBuffer(FILL);
                for(var marker:NodeMarkerStore.getInstance().snapshot()) {
                    if(player.blockPosition().distSqr(marker.pos())>MAX_DRAW_DISTANCE_SQUARED)continue;
                    float cx=marker.pos().getX()+0.5F,cy=marker.pos().getY()+MARKER_Y_OFFSET,cz=marker.pos().getZ()+0.5F;
                    float[] basis=markerBasis(eye,cx,cy,cz);int rgb=marker.color()==MarkerColor.RED?0xF04040:0x4080FF;
                    for(int i=0;i<MARKER_SEGMENTS;i++) {
                        double a=Math.PI*2*i/MARKER_SEGMENTS,b=Math.PI*2*(i+1)/MARKER_SEGMENTS;
                        markerVertex(fill,matrices.last(),cx,cy,cz,rgb);
                        float x=(float)Math.cos(a)*MARKER_RADIUS,y=(float)Math.sin(a)*MARKER_RADIUS;
                        markerVertex(fill,matrices.last(),cx+basis[0]*x+basis[3]*y,cy+basis[1]*x+basis[4]*y,cz+basis[2]*x+basis[5]*y,rgb);
                        x=(float)Math.cos(b)*MARKER_RADIUS;y=(float)Math.sin(b)*MARKER_RADIUS;
                        markerVertex(fill,matrices.last(),cx+basis[0]*x+basis[3]*y,cy+basis[1]*x+basis[4]*y,cz+basis[2]*x+basis[5]*y,rgb);
                    }
                }
                buffers.endBatch(FILL);
            }
        }catch(RuntimeException e){if(!warned){warned=true;Main.LOGGER.warn("[turnout] 节点/标记绘制失败",e);}}
        finally{matrices.popPose();}
    }
    /** 工具显示期间每五 tick 刷新附近节点；不再每帧复制整个世界的轨道表。 */
    private static Set<BlockPos> nearbyNodes(Player player) {
        var level=Minecraft.getInstance().level;if(level==null)return Set.of();
        long tick=level.getGameTime();BlockPos origin=player.blockPosition();var dimension=level.dimension().location();
        if(nodeOrigin!=null&&dimension.equals(nodeDimension)&&tick>=lastNodeTick&&tick-lastNodeTick<5&&origin.distSqr(nodeOrigin)<16)return visibleNodes;
        Set<BlockPos> nodes=new HashSet<>();
        synchronized(mtr.client.ClientData.RAILS) {
            for(BlockPos pos:mtr.client.ClientData.RAILS.keySet())if(pos!=null&&pos.distSqr(origin)<=MAX_DRAW_DISTANCE_SQUARED)nodes.add(pos);
            for(var links:mtr.client.ClientData.RAILS.values())if(links!=null)for(BlockPos pos:links.keySet())if(pos!=null&&pos.distSqr(origin)<=MAX_DRAW_DISTANCE_SQUARED)nodes.add(pos);
        }
        nodeOrigin=origin;nodeDimension=dimension;lastNodeTick=tick;visibleNodes=Set.copyOf(nodes);return visibleNodes;
    }
    private static void markerVertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,int rgb){out.vertex(pose.pose(),x,y,z).color((rgb>>16)&255,(rgb>>8)&255,rgb&255,255).endVertex();}
    private static void drawNodeBox(VertexConsumer vc, PoseStack.Pose pose, BlockPos pos) {
        final float x0 = pos.getX() + 0.02F, y0 = pos.getY() + 0.02F, z0 = pos.getZ() + 0.02F;
        final float x1 = pos.getX() + 0.98F, y1 = pos.getY() + 0.98F, z1 = pos.getZ() + 0.98F;
        // 底面
        line(vc, pose, x0, y0, z0, x1, y0, z0);
        line(vc, pose, x1, y0, z0, x1, y0, z1);
        line(vc, pose, x1, y0, z1, x0, y0, z1);
        line(vc, pose, x0, y0, z1, x0, y0, z0);
        // 顶面
        line(vc, pose, x0, y1, z0, x1, y1, z0);
        line(vc, pose, x1, y1, z0, x1, y1, z1);
        line(vc, pose, x1, y1, z1, x0, y1, z1);
        line(vc, pose, x0, y1, z1, x0, y1, z0);
        // 竖边
        line(vc, pose, x0, y0, z0, x0, y1, z0);
        line(vc, pose, x1, y0, z0, x1, y1, z0);
        line(vc, pose, x1, y0, z1, x1, y1, z1);
        line(vc, pose, x0, y0, z1, x0, y1, z1);
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2) {
        // 原版线条着色器把 normal 当作线段方向；固定写 (0,1,0) 会让横边挤出方向错误。
        float dx=x2-x1,dy=y2-y1,dz=z2-z1;
        float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(length<1e-6F)return;
        dx/=length;dy/=length;dz/=length;
        vc.vertex(pose.pose(),x1,y1,z1).color(255,255,255,255).normal(pose.normal(),dx,dy,dz).endVertex();
        vc.vertex(pose.pose(),x2,y2,z2).color(255,255,255,255).normal(pose.normal(),dx,dy,dz).endVertex();
    }

    private static float[] markerBasis(Vec3 camPos, float cx, float cy, float cz) {
        float fx = cx - (float) camPos.x;
        float fy = cy - (float) camPos.y;
        float fz = cz - (float) camPos.z;
        final float flen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (flen < 1.0E-4F) {
            // 相机正好落在圆心：随便给一个朝向即可（盘面仍然会被画出来）
            fx = 0.0F;
            fy = 0.0F;
            fz = 1.0F;
        } else {
            fx /= flen;
            fy /= flen;
            fz /= flen;
        }

        // right = cross(forward, worldUp(0,1,0)) = (-fz, 0, fx)
        float rx = -fz, ry = 0.0F, rz = fx;
        float rlen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rlen < 1.0E-4F) {
            // 正上/正下俯视的退化处理：改用 z 轴 cross(forward, (0,0,1)) = (fy, -fx, 0)
            rx = fy;
            ry = -fx;
            rz = 0.0F;
            rlen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (rlen < 1.0E-4F) {
                rx = 1.0F;
                ry = 0.0F;
                rz = 0.0F;
                rlen = 1.0F;
            }
        }
        rx /= rlen;
        ry /= rlen;
        rz /= rlen;

        // up = cross(right, forward)
        float ux = ry * fz - rz * fy;
        float uy = rz * fx - rx * fz;
        float uz = rx * fy - ry * fx;
        final float ulen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ulen < 1.0E-4F) {
            ux = 0.0F;
            uy = 1.0F;
            uz = 0.0F;
        } else {
            ux /= ulen;
            uy /= ulen;
            uz /= ulen;
        }
        return new float[] {rx, ry, rz, ux, uy, uz};
    }

    private static boolean holdingTool(Player player) {
        final ItemStack main = player.getMainHandItem();
        final ItemStack off = player.getOffhandItem();
        return main.getItem() instanceof ItemDisplacementTool || off.getItem() instanceof ItemDisplacementTool;
    }

}
