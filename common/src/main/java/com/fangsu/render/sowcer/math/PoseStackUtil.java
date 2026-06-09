package com.fangsu.render.sowcer.math;

import com.mojang.blaze3d.vertex.PoseStack;

public class PoseStackUtil {

    public static void rotX(PoseStack matrices, float rad) {
        //#if MC_VERSION >= 11903
        matrices.mulPose(com.mojang.math.Axis.XP.rotation(rad));
        //#else
        //$$ matrices.mulPose(new com.mojang.math.Vector3f(1.0F, 0.0F, 0.0F).rotation(rad));
        //#endif
    }

    public static void rotY(PoseStack matrices, float rad) {
        //#if MC_VERSION >= 11903
        matrices.mulPose(com.mojang.math.Axis.YP.rotation(rad));
        //#else
        //$$ matrices.mulPose(new com.mojang.math.Vector3f(0.0F, 1.0F, 0.0F).rotation(rad));
        //#endif
    }

    public static void rotZ(PoseStack matrices, float rad) {
        //#if MC_VERSION >= 11903
        matrices.mulPose(com.mojang.math.Axis.ZP.rotation(rad));
        //#else
        //$$ matrices.mulPose(new com.mojang.math.Vector3f(0.0F, 0.0F, 1.0F).rotation(rad));
        //#endif
    }

}
