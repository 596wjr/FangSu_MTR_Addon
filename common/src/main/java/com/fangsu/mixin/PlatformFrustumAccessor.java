package com.fangsu.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 与原生区块使用同一个视锥，避免覆盖层在身后仍逐面提交。 */
@Mixin(LevelRenderer.class)
public interface PlatformFrustumAccessor {
    @Accessor("cullingFrustum") Frustum fangsu$platformFrustum();
}
