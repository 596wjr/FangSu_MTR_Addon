package com.fangsu.render.math;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

public class PoseStackUtil {
    public static void rotY(PoseStack poseStack, float radians) {
        poseStack.mulPose(Axis.YP.rotation(radians));
    }
}
