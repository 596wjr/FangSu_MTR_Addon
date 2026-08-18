package com.fangsu.mixin;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.PathGenerationStatusManager;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import mtr.data.Depot;
import mtr.data.NameColorDataBase;
import mtr.screen.EditDepotScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 车厂编辑界面状态文本：MTR3 原版生成中用 {@code gui.mtr.generating_path}（固定文案
 * 「正在生成路径」），且 zh_CN 缺该 key 时显示原始 key；成功/失败文本全为白色
 * （渲染固定 ARGB_WHITE）。统一改为通知同款格式（多行 {@code |} 分隔、§ 码着色）：
 * <pre>
 * 生成中：[黄]车厂名 刷新线路中（已用时 %s）...
 * 成功：  [黄]车厂名 [绿]线路刷新成功！
 * 失败：  [黄]车厂名 [红]线路刷新失败：| [红]原因 | [红]> 在 [黄]A [红]与 [黄]B [红] 之间找不到路径
 * </pre>
 * <p>
 * {@code getSuccessfulSegmentsText()} 是实例方法，{@code data} 为父类
 * {@code EditNameColorScreenBase} 的泛型字段（字节码擦除为 {@code NameColorDataBase}，
 * 实际对象为 {@code Depot}）。渲染处 {@code getString().split("\\|")} 后以固定
 * ARGB_WHITE 绘制，故多行用 {@code |} 分隔、颜色用 § 码内嵌（literal 的
 * {@code getString()} 保留 § 码，font 渲染会解析）。
 */
@Mixin(value = EditDepotScreen.class, remap = false)
public abstract class EditDepotScreenMixin {

    @Shadow
    protected NameColorDataBase data;

    @ModifyReturnValue(method = "getSuccessfulSegmentsText", at = @At("RETURN"))
    private Component fangsu$dashboardText(Component original) {
        return ComponentHelper.literal(PathGenerationStatusManager.getDashboardText((Depot) data, original.getString()));
    }
}
