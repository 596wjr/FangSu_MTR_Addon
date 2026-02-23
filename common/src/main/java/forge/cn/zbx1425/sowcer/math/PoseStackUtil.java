package forge.cn.zbx1425.sowcer.math;

import com.mojang.blaze3d.vertex.PoseStack;

public class PoseStackUtil {
    public static void rotY(PoseStack poseStack, float radians) {
        poseStack.mulPose(new org.joml.Quaternionf().rotateY(radians));
    }
}
