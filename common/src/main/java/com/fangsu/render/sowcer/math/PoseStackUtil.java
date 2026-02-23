package com.fangsu.render.sowcer.math;

import com.mojang.blaze3d.vertex.PoseStack;

public class PoseStackUtil {

    public static void rotX(PoseStack matrices, float rad) {
        matrices.mulPose(com.mojang.math.Axis.XP.rotation(rad));
    }

    public static void rotY(PoseStack matrices, float rad) {
        matrices.mulPose(com.mojang.math.Axis.YP.rotation(rad));
    }

    public static void rotZ(PoseStack matrices, float rad) {
        matrices.mulPose(com.mojang.math.Axis.ZP.rotation(rad));
    }

}
