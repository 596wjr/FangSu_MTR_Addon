package com.fangsu.mtr;

import com.fangsu.customItem.CustomMtrLifts;
import com.fangsu.data.LiftExtraSupplier;
import com.mojang.blaze3d.vertex.PoseStack;
import mtr.data.Lift;
import mtr.data.NameColorDataBase;
import mtr.model.ModelLift1;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

public class ModernTexturedLift extends ModelLift1 {
    private final int heightCount;
    private final int heightOffset;
    private final int width;
    private final int depth;
    private final boolean isDoubleSided;
    private String model_key;
    private CustomMtrLifts.TexturedLiftSelectInfo select_info;

    public ModernTexturedLift(Lift lift, int height, int width, int depth, boolean isDoubleSided) {
        super(height, width, depth, isDoubleSided);
        this.heightCount = height;
        this.heightOffset = 0;
        this.width = width;
        this.depth = depth;
        this.isDoubleSided = isDoubleSided;
        this.model_key = ((LiftExtraSupplier) lift).fangsu$getModelKey();
        this.select_info = CustomMtrLifts.getInstance().getTexturedLiftSelectInfo(this.model_key);
    }

    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, NameColorDataBase data, int light, float doorLeftValue, float doorRightValue) {
        super.render(matrices, vertexConsumers, data, select_info.getTexture(), light, doorLeftValue, doorRightValue,
                false, 0, 1, false, true, false, false, false);
    }
}
