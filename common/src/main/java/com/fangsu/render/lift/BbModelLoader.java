package com.fangsu.render.lift;

import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Blockbench (.bbmodel) 加载器（电梯专用入口）。
 *
 * <p>委托给通用 {@link com.fangsu.render.sowcerext.model.loader.BlockbenchModelLoader}，
 * 支持现代（groups + outliner）与旧版（outliner 带组名）两种格式，以及 cube / mesh 元素，
 * 并把 bbmodel 中的分组作为模型组返回。
 *
 * <p>注意：通用加载器把 bbmodel 像素坐标 ÷16 换算为方块单位（与 OBJ 一致）；而电梯
 * 拼装器使用模型单位（1 格 = 16，见 {@link LiftModelAssembler}），因此此处缩放回 ×16
 * 保持旧版像素行为不变。</p>
 */
public class BbModelLoader {

	private BbModelLoader() {
	}

	/**
	 * 按 bbmodel 的分组加载为 {@code Map<组名, RawModel>}（电梯模型单位，像素尺寸）。
	 */
	public static Map<String, RawModel> loadModels(ResourceLocation location) throws IOException {
		Map<String, RawModel> models = ResourceUtil.loadPartedModel(location, false);
		Map<String, RawModel> result = new LinkedHashMap<>();
		for (Map.Entry<String, RawModel> entry : models.entrySet()) {
			RawModel model = entry.getValue().copy();
			model.applyScale(16F, 16F, 16F);
			result.put(entry.getKey(), model);
		}
		return result;
	}
}
