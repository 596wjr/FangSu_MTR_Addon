package com.fangsu.mixin;

import com.fangsu.platform.PlatformMtrDoorPlacement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** 以 Minecraft 统一入口适配 MTR3/4，避免硬编码 MTR 覆写方法的运行时混淆名。 */
@Mixin(ItemStack.class)
public abstract class PlatformOverlayMtrContextMixin {
    @ModifyVariable(method="useOn",at=@At("HEAD"),argsOnly=true)
    private UseOnContext fangsu$doorContext(UseOnContext context){return PlatformMtrDoorPlacement.adjust(context);}
}
