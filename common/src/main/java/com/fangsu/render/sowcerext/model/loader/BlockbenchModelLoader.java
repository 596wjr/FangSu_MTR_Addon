package com.fangsu.render.sowcerext.model.loader;

import com.fangsu.render.sowcer.batch.MaterialProp;
import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.render.sowcerext.model.Face;
import com.fangsu.render.sowcerext.model.RawMesh;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.Vertex;
import com.fangsu.render.sowcerext.reuse.AtlasManager;
import com.fangsu.render.sowcerext.util.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 Blockbench (.bbmodel) 加载器：把 bbmodel 中的"分组"作为模型的组返回。
 *
 * <p>兼容两种格式：</p>
 * <ul>
 *   <li><b>现代（format_version 5.x）</b>：组名与变换在 {@code groups} 数组，{@code outliner}
 *       只含 uuid + children（元素 uuid 字符串 / 嵌套分组 uuid）。</li>
 *   <li><b>旧版（format_version 4.x）</b>：组名与 children 直接在 {@code outliner} 条目上。</li>
 * </ul>
 *
 * <p>元素类型：{@code cube}（立方体，支持 box_uv 与逐面 uv）与 {@code mesh}（网格，含逐顶点 UV）。</p>
 *
 * <p>贴图：从 {@code textures} 数组按元素/面的 {@code texture} 索引取 {@code relative_path}，
 * 相对 bbmodel 文件解析为资源路径并写入 {@link MaterialProp#texture}；无贴图时为 null。</p>
 */
public class BlockbenchModelLoader {

	private BlockbenchModelLoader() {
	}

	/**
	 * 读取整个 bbmodel 合并为一个模型（所有分组拼在一起）。
	 */
	public static RawModel loadModel(ResourceManager resourceManager, ResourceLocation location, AtlasManager atlasManager) throws IOException {
		Map<String, RawModel> groups = loadModels(resourceManager, location, atlasManager);
		RawModel merged = new RawModel();
		merged.sourceLocation = location;
		for (RawModel model : groups.values()) {
			merged.append(model);
		}
		return merged;
	}

	/**
	 * 按 bbmodel 的分组返回 {@code Map<组名, RawModel>}，组名与 OBJ 分组语义一致。
	 */
	public static Map<String, RawModel> loadModels(ResourceManager resourceManager, ResourceLocation location, AtlasManager atlasManager) throws IOException {
		JsonObject root = readRoot(resourceManager, location);

		float texW = 64, texH = 64;
		if (root.has("resolution") && root.get("resolution").isJsonObject()) {
			JsonObject res = root.getAsJsonObject("resolution");
			texW = res.has("width") ? res.get("width").getAsFloat() : 64;
			texH = res.has("height") ? res.get("height").getAsFloat() : 64;
		}

		// ---- 贴图表：索引 -> 候选纹理文件名（优先 name，其次 relative_path 文件名部分，忽略绝对路径）----
		List<String[]> textureCandidates = new ArrayList<>();
		if (root.has("textures") && root.get("textures").isJsonArray()) {
			for (JsonElement t : root.getAsJsonArray("textures")) {
				JsonObject tex = t.getAsJsonObject();
				String name = tex.has("name") && tex.get("name").isJsonPrimitive()
						? tex.get("name").getAsString() : null;
				String rp = tex.has("relative_path") && tex.get("relative_path").isJsonPrimitive()
						? tex.get("relative_path").getAsString() : null;
				if (rp != null) {
					// 只取文件名部分，丢弃可能存在的目录/绝对路径
					int idx = Math.max(rp.lastIndexOf('/'), rp.lastIndexOf('\\'));
					rp = idx >= 0 ? rp.substring(idx + 1) : rp;
				}
				// 去重：name 与 relative_path 相同只留一个
				if (name != null && name.equals(rp)) rp = null;
				textureCandidates.add(new String[]{name, rp});
			}
		}
		// 解析每个纹理为可用的资源路径（按候选顺序找第一个存在的）
		List<ResourceLocation> textureLocations = new ArrayList<>(textureCandidates.size());
		for (String[] candidates : textureCandidates) {
			textureLocations.add(resolveTextureLocation(resourceManager, location, candidates));
		}
		// 元素 uuid -> 元素对象
		Map<String, JsonObject> elementsByUuid = new HashMap<>();
		if (root.has("elements") && root.get("elements").isJsonArray()) {
			for (JsonElement e : root.getAsJsonArray("elements")) {
				JsonObject el = e.getAsJsonObject();
				if (el.has("uuid")) elementsByUuid.put(el.get("uuid").getAsString(), el);
			}
		}

		// ---- 组名表：uuid -> 组对象（现代格式来自 groups，旧版来自 outliner）----
		Map<String, JsonObject> groupsByUuid = new HashMap<>();
		if (root.has("groups") && root.get("groups").isJsonArray()) {
			for (JsonElement g : root.getAsJsonArray("groups")) {
				JsonObject group = g.getAsJsonObject();
				if (group.has("uuid")) groupsByUuid.put(group.get("uuid").getAsString(), group);
			}
		}

		// ---- 解析分组树：组名 -> 其下所有元素 uuid（扁平收集，嵌套分组并入外层）----
		Map<String, List<String>> groupElementUuids = new LinkedHashMap<>();
		Map<String, JsonObject> outlinerByUuid = new HashMap<>();
		if (root.has("outliner") && root.get("outliner").isJsonArray()) {
			for (JsonElement entry : root.getAsJsonArray("outliner")) {
				if (entry.isJsonObject()) {
					JsonObject outline = entry.getAsJsonObject();
					if (outline.has("uuid")) outlinerByUuid.put(outline.get("uuid").getAsString(), outline);
				}
			}
		}

		if (root.has("outliner") && root.get("outliner").isJsonArray() && root.getAsJsonArray("outliner").size() > 0) {
			int unnamed = 0;
			for (JsonElement entry : root.getAsJsonArray("outliner")) {
				if (entry.isJsonObject()) {
					JsonObject outline = entry.getAsJsonObject();
					String name = outlineName(outline, groupsByUuid);
					if (StringUtils.isEmpty(name)) name = "group" + (unnamed++);
					List<String> elements = new ArrayList<>();
					collectOutlineElements(outline, groupsByUuid, outlinerByUuid, elementsByUuid, elements);
					if (!elements.isEmpty()) {
						groupElementUuids.computeIfAbsent(name, k -> new ArrayList<>()).addAll(elements);
					}
				} else if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
					// 旧版 outliner 直接是元素 uuid 字符串：并入默认组
					String uuid = entry.getAsString();
					if (elementsByUuid.containsKey(uuid)) {
						groupElementUuids.computeIfAbsent("all", k -> new ArrayList<>()).add(uuid);
					}
				}
			}
			// outliner 存在但没收集到任何分组（例如全是裸元素字符串）：退回整组
			if (groupElementUuids.isEmpty()) {
				groupElementUuids.put("all", new ArrayList<>(elementsByUuid.keySet()));
			}
		} else {
			// 无 outliner：全部元素合并为一组
			groupElementUuids.put("all", new ArrayList<>(elementsByUuid.keySet()));
		}

		Map<String, RawModel> result = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : groupElementUuids.entrySet()) {
			RawModel model = new RawModel();
			model.sourceLocation = new ResourceLocation(location.getNamespace(),
					location.getPath() + "/" + sanitize(entry.getKey()));
			// 组名可带 #渲染类型 后缀，作为该组元素的默认渲染类型
			String groupRenderType = renderTypeSuffix(entry.getKey());
			for (String uuid : entry.getValue()) {
				JsonObject element = elementsByUuid.get(uuid);
				if (element == null) continue;
				List<RawMesh> meshes = buildElement(element, texW, texH, textureLocations, groupRenderType);
				for (RawMesh mesh : meshes) {
					if (!mesh.faces.isEmpty()) model.append(mesh);
				}
			}
			if (!model.meshList.isEmpty()) {
				// bbmodel 坐标是像素尺寸，OBJ 导出已 ÷16 为方块单位，这里做同样换算
				model.applyScale(1F / 16F, 1F / 16F, 1F / 16F);
				model.generateNormals();
				model.distinct();
				result.put(entry.getKey(), model);
			}
		}
		return result;
	}

	// ==================== 读取 ====================

	private static JsonObject readRoot(ResourceManager resourceManager, ResourceLocation location) throws IOException {
		String json = com.fangsu.render.sowcerext.util.ResourceUtil.readResource(resourceManager, location);
		if (json == null || json.isEmpty()) {
			throw new IOException("bbmodel not found or empty: " + location);
		}
		JsonElement parsed = com.fangsu.Main.JSON_PARSER.parse(json);
		if (parsed == null || !parsed.isJsonObject()) {
			throw new IOException("bbmodel is not a JSON object: " + location);
		}
		return parsed.getAsJsonObject();
	}

	/** 按候选文件名（name 优先，其次 relative_path 文件名）在 bbmodel 同目录解析第一个存在的贴图资源。 */
	private static ResourceLocation resolveTextureLocation(ResourceManager resourceManager, ResourceLocation bbmodelLocation, String[] candidates) {
		for (String candidate : candidates) {
			if (StringUtils.isEmpty(candidate)) continue;
			ResourceLocation tex = ResourceUtil.resolveRelativePath(bbmodelLocation, candidate, ".png");
			if (hasResource(resourceManager, tex)) return tex;
		}
		return null;
	}

	private static boolean hasResource(ResourceManager resourceManager, ResourceLocation location) {
		try {
			//#if MC_VERSION >= 11900
			return resourceManager.getResource(location).isPresent();
			//#else
			//$$ resourceManager.getResource(location); return true;
			//#endif
		} catch (Exception e) {
			return false;
		}
	}

	// ==================== 分组 ====================

	/** 取 outliner 条目的组名：现代格式从 groups 表按 uuid 取，旧版直接用 outliner.name。 */
	private static String outlineName(JsonObject outline, Map<String, JsonObject> groupsByUuid) {
		if (outline.has("name") && outline.get("name").isJsonPrimitive()) {
			return outline.get("name").getAsString();
		}
		String uuid = outline.has("uuid") ? outline.get("uuid").getAsString() : "";
		if (!uuid.isEmpty()) {
			JsonObject group = groupsByUuid.get(uuid);
			if (group != null && group.has("name") && group.get("name").isJsonPrimitive()) {
				return group.get("name").getAsString();
			}
		}
		return "";
	}

	/**
	 * 递归收集某个 outliner 条目下所有元素 uuid。
	 * children 可为：元素/分组的 uuid 字符串，或嵌套的分组对象（含 children）。
	 */
	private static void collectOutlineElements(JsonObject node, Map<String, JsonObject> groupsByUuid,
											   Map<String, JsonObject> outlinerByUuid,
											   Map<String, JsonObject> elementsByUuid, List<String> out) {
		if (!node.has("children") || !node.get("children").isJsonArray()) return;
		for (JsonElement child : node.getAsJsonArray("children")) {
			if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
				String uuid = child.getAsString();
				if (elementsByUuid.containsKey(uuid)) {
					if (!out.contains(uuid)) out.add(uuid);
				} else if (outlinerByUuid.containsKey(uuid)) {
					// 现代格式嵌套分组：递归
					collectOutlineElements(outlinerByUuid.get(uuid), groupsByUuid, outlinerByUuid, elementsByUuid, out);
				}
				// 其他 uuid 忽略
			} else if (child.isJsonObject()) {
				JsonObject childObj = child.getAsJsonObject();
				if (childObj.has("children")) {
					// 旧版嵌套分组对象
					collectOutlineElements(childObj, groupsByUuid, outlinerByUuid, elementsByUuid, out);
				} else if (childObj.has("uuid")) {
					String uuid = childObj.get("uuid").getAsString();
					if (elementsByUuid.containsKey(uuid) && !out.contains(uuid)) out.add(uuid);
				}
			}
		}
	}

	// ==================== 元素 ====================

	private static List<RawMesh> buildElement(JsonObject element, float texW, float texH,
											  List<ResourceLocation> textureLocations, String groupRenderType) {
		String type = element.has("type") ? element.get("type").getAsString() : "cube";
		List<RawMesh> meshes;
		if ("cube".equals(type)) {
			meshes = buildCube(element, texW, texH, textureLocations);
		} else if ("mesh".equals(type)) {
			meshes = buildMesh(element, texW, texH, textureLocations);
		} else {
			meshes = java.util.Collections.emptyList();
		}
		if (meshes.isEmpty()) return meshes;
		// 渲染类型优先级：元素(块)名 #后缀 > 组名 #后缀 > 默认 exterior
		String renderType = resolveRenderType(element, groupRenderType);
		for (RawMesh mesh : meshes) {
			// 必须设置渲染类型，否则 shaderName 为 null，绘制时 ShaderManager 找不到 shader
			mesh.setRenderType(renderType);
		}
		return meshes;
	}

	/** 从名字中提取 # 后的渲染类型后缀（如 group#interior → interior），无 # 返回 null。 */
	private static String renderTypeSuffix(String name) {
		if (name == null) return null;
		int idx = name.lastIndexOf('#');
		if (idx < 0 || idx == name.length() - 1) return null;
		String suffix = name.substring(idx + 1).trim();
		return suffix.isEmpty() ? null : suffix;
	}

	/** 解析元素渲染类型：元素名 #后缀 > 组名 #后缀 > 默认 exterior。 */
	private static String resolveRenderType(JsonObject element, String groupRenderType) {
		String elementRenderType = element.has("name") && element.get("name").isJsonPrimitive()
				? renderTypeSuffix(element.get("name").getAsString()) : null;
		if (elementRenderType != null) return elementRenderType;
		if (groupRenderType != null) return groupRenderType;
		return "exterior";
	}

	/** 把贴图索引对应的资源写入 materialProp.texture。 */
	private static void resolveMeshTexture(RawMesh mesh, int texIndex, List<ResourceLocation> textureLocations) {
		if (texIndex < 0 || texIndex >= textureLocations.size()) return;
		ResourceLocation tex = textureLocations.get(texIndex);
		if (tex != null) mesh.materialProp.texture = tex;
	}

	// ---------- cube ----------

	private static List<RawMesh> buildCube(JsonObject element, float texW, float texH,
										   List<ResourceLocation> textureLocations) {
		double[] from = {0, 0, 0}, to = {1, 1, 1};
		getArray(from, element, "from");
		getArray(to, element, "to");

		float x0 = (float) from[0], y0 = (float) from[1], z0 = (float) from[2];
		float x1 = (float) to[0], y1 = (float) to[1], z1 = (float) to[2];

		boolean boxUv = element.has("box_uv") && element.get("box_uv").getAsBoolean();

		// 按面贴图索引拆分 mesh：不同贴图的面分开（RawMesh 只能有一个材质）
		Map<Integer, RawMesh> meshesByTexture = new LinkedHashMap<>();

		addCubeFace(meshesByTexture, boxUv, element, texW, texH, x0, y0, z0, x1, y1, z1, "north", 0, 0, -1);
		addCubeFace(meshesByTexture, boxUv, element, texW, texH, x0, y0, z0, x1, y1, z1, "south", 0, 0, 1);
		addCubeFace(meshesByTexture, boxUv, element, texW, texH, x0, y0, z0, x1, y1, z1, "west", -1, 0, 0);
		addCubeFace(meshesByTexture, boxUv, element, texW, texH, x0, y0, z0, x1, y1, z1, "east", 1, 0, 0);
		addCubeFace(meshesByTexture, boxUv, element, texW, texH, x0, y0, z0, x1, y1, z1, "up", 0, 1, 0);
		addCubeFace(meshesByTexture, boxUv, element, texW, texH, x0, y0, z0, x1, y1, z1, "down", 0, -1, 0);

		List<RawMesh> result = new ArrayList<>();
		for (Map.Entry<Integer, RawMesh> entry : meshesByTexture.entrySet()) {
			RawMesh mesh = entry.getValue();
			mesh.materialProp.attrState.setColor(255, 255, 255, 255);
			// 面级贴图（无则留空，由 buildElement 用元素顶层 texture 兜底）
			if (entry.getKey() >= 0) {
				resolveMeshTexture(mesh, entry.getKey(), textureLocations);
			}
			result.add(mesh);
		}
		return result;
	}

	private static void addCubeFace(Map<Integer, RawMesh> meshesByTexture, boolean boxUv, JsonObject element, float texW, float texH,
									float x0, float y0, float z0, float x1, float y1, float z1,
									String faceName, float nx, float ny, float nz) {
		JsonObject faces = element.has("faces") && element.get("faces").isJsonObject()
				? element.getAsJsonObject("faces") : null;
		JsonObject face = faces != null && faces.has(faceName) && faces.get(faceName).isJsonObject()
				? faces.getAsJsonObject(faceName) : null;

		// 面贴图索引（暂存为假路径，稍后解析成真实贴图）
		int texIndex = face != null && face.has("texture") && face.get("texture").isJsonPrimitive()
				? face.get("texture").getAsInt() : -1;

		// 面 UV：box_uv 用盒展开（按 6 面布局从 uv_offset 起步）；否则读面自身的 uv
		float u0, v0, u1, v1;
		double[] faceUv = {0, 0, 0, 0};
		boolean hasFaceUv = face != null && face.has("uv") && face.get("uv").isJsonArray();
		if (hasFaceUv) getArray(faceUv, face, "uv");

		if (boxUv && !hasFaceUv) {
			// 盒展开：按标准顺序 north/south/west/east/up/down 摆放
			double[] uvOffset = {0, 0};
			getArray(uvOffset, element, "uv_offset");
			float du = x1 - x0, dv = y1 - y0, dw = z1 - z0;
			float ox = (float) uvOffset[0], oy = (float) uvOffset[1];
			switch (faceName) {
				case "north" -> { u0 = ox; v0 = oy; u1 = ox + du; v1 = oy + dv; }
				case "south" -> { u0 = ox + du + dw; v0 = oy; u1 = ox + 2 * du + dw; v1 = oy + dv; }
				case "west" -> { u0 = ox + du + 2 * dw; v0 = oy; u1 = ox + du + 3 * dw; v1 = oy + dv; }
				case "east" -> { u0 = ox + 2 * du + 2 * dw; v0 = oy; u1 = ox + 3 * du + 2 * dw; v1 = oy + dv; }
				case "up" -> { u0 = ox + du; v0 = oy + dv; u1 = ox + 2 * du; v1 = oy + dv + dw; }
				case "down" -> { u0 = ox + du + dw; v0 = oy + dv; u1 = ox + 2 * du + dw; v1 = oy + dv + dw; }
				default -> { u0 = ox; v0 = oy; u1 = ox + du; v1 = oy + dv; }
			}
		} else if (hasFaceUv) {
			u0 = (float) faceUv[0];
			v0 = (float) faceUv[1];
			u1 = (float) faceUv[2];
			v1 = (float) faceUv[3];
		} else {
			// 无任何 uv：整张贴图
			u0 = 0; v0 = 0; u1 = texW; v1 = texH;
		}

		// 归一化到 [0,1]，并翻转 V（与 Blockbench OBJ 导出一致：obj v = 1 - bbmodel v）
		float nu0 = u0 / texW, nv0 = 1 - v0 / texH, nu1 = u1 / texW, nv1 = 1 - v1 / texH;
		// 标准 Blockbench 盒面 UV 表：uvIdx 形如 {uIdx, vIdx}，0=u0/v0，1=u1/v1
		int[][] uvSel = CUBE_FACE_UV.get(faceName);
		if (uvSel == null) return;
		float[][] corners = cubeFaceCorners(faceName, x0, y0, z0, x1, y1, z1);
		if (corners == null) return;
		// 跳过退化面（零厚度立方体的某些面面积为 0，如 debug 面板 dy=0 时的南北西东面）
		if (faceAreaSquared(corners) <= 0) return;

		RawMesh mesh = meshesByTexture.computeIfAbsent(texIndex, k -> new RawMesh(new MaterialProp()));
		int base = mesh.vertices.size();
		for (int i = 0; i < 4; i++) {
			float[] pos = corners[i];
			Vertex vtx = new Vertex(new Vector3f(pos[0], pos[1], pos[2]), new Vector3f(nx, ny, nz));
			float u = uvSel[i][0] == 0 ? nu0 : nu1;
			float v = uvSel[i][1] == 0 ? nv0 : nv1;
			vtx.u = u;
			vtx.v = v;
			mesh.vertices.add(vtx);
		}
		mesh.faces.add(new Face(new int[]{base, base + 1, base + 2}));
		mesh.faces.add(new Face(new int[]{base, base + 2, base + 3}));
	}

	/**
	 * 标准 Blockbench 盒面 UV 表：每个面 4 个角点的 {u,v} 索引（0=u0/v0，1=u1/v1）。
	 * 顺序与 {@link #cubeFaceCorners} 返回的角点顺序一致（从立方体外部看 CCW）。
	 * 该表由 Blockbench OBJ 导出的同一立方体贴图反推得到，保证 uv 长宽方向与贴图一致（不拉伸）。
	 */
	private static final Map<String, int[][]> CUBE_FACE_UV = buildCubeFaceUv();

	private static Map<String, int[][]> buildCubeFaceUv() {
		Map<String, int[][]> m = new HashMap<>();
		// 每个面的角点顺序见 cubeFaceCorners：
		// north: (x0,y1),(x1,y1),(x1,y0),(x0,y0)
		m.put("north", new int[][]{{1, 0}, {0, 0}, {0, 1}, {1, 1}});
		// south: (x0,y0),(x1,y0),(x1,y1),(x0,y1)
		m.put("south", new int[][]{{0, 1}, {1, 1}, {1, 0}, {0, 0}});
		// west: (x0,y0,z1),(x0,y1,z1),(x0,y1,z0),(x0,y0,z0)
		m.put("west", new int[][]{{1, 1}, {1, 0}, {0, 0}, {0, 1}});
		// east: (x1,y0,z0),(x1,y1,z0),(x1,y1,z1),(x1,y0,z1)
		m.put("east", new int[][]{{1, 1}, {1, 0}, {0, 0}, {0, 1}});
		// up: (x0,y1,z0),(x0,y1,z1),(x1,y1,z1),(x1,y1,z0)
		m.put("up", new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}});
		// down: (x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)
		m.put("down", new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}});
		return m;
	}

	/** 返回指定面的 4 个角点，顺序为从外部看 CCW（保证外向法线与背面剔除正确）。 */
	private static float[][] cubeFaceCorners(String faceName, float x0, float y0, float z0, float x1, float y1, float z1) {
		return switch (faceName) {
			// 各面顺序为从立方体外部看逆时针（配合显式外向法线）
			case "north" -> new float[][]{
					{x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}};
			case "south" -> new float[][]{
					{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
			case "west" -> new float[][]{
					{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}};
			case "east" -> new float[][]{
					{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
			case "up" -> new float[][]{
					{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
			case "down" -> new float[][]{
					{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
			default -> null;
		};
	}

	// ---------- mesh ----------

	private static List<RawMesh> buildMesh(JsonObject element, float texW, float texH,
										   List<ResourceLocation> textureLocations) {
		if (!element.has("vertices") || !element.get("vertices").isJsonObject()) return java.util.Collections.emptyList();
		if (!element.has("faces") || !element.get("faces").isJsonObject()) return java.util.Collections.emptyList();

		// mesh 顶点是相对元素 origin 的局部坐标，需平移 origin 后再 ÷16（与 OBJ 导出一致）
		double[] origin = {0, 0, 0};
		if (element.has("origin") && element.get("origin").isJsonArray()) getArray(origin, element, "origin");
		float ox = (float) origin[0], oy = (float) origin[1], oz = (float) origin[2];

		Map<String, double[]> vertexPositions = new HashMap<>();
		for (Map.Entry<String, JsonElement> v : element.getAsJsonObject("vertices").entrySet()) {
			if (v.getValue().isJsonArray()) {
				vertexPositions.put(v.getKey(), toDoubleArray(v.getValue().getAsJsonArray()));
			}
		}

		// 元素顶层贴图索引（face 未单独指定时使用）
		int elementTexIndex = element.has("texture") && element.get("texture").isJsonPrimitive()
				? element.get("texture").getAsInt() : -1;

		// 按面贴图索引拆分 mesh：不同贴图的面分开（RawMesh 只能有一个材质）
		Map<Integer, RawMesh> meshesByTexture = new LinkedHashMap<>();

		for (Map.Entry<String, JsonElement> f : element.getAsJsonObject("faces").entrySet()) {
			JsonObject face = f.getValue().getAsJsonObject();
			if (!face.has("vertices") || !face.get("vertices").isJsonArray()) continue;
			JsonArray vertexKeys = face.getAsJsonArray("vertices");
			if (vertexKeys.size() < 3) continue;

			// 该面贴图索引（优先 face.texture，否则元素顶层）
			int texIndex = face.has("texture") && face.get("texture").isJsonPrimitive()
					? face.get("texture").getAsInt() : elementTexIndex;
			RawMesh mesh = meshesByTexture.computeIfAbsent(texIndex, k -> {
				RawMesh m = new RawMesh(new MaterialProp());
				m.materialProp.attrState.setColor(255, 255, 255, 255);
				return m;
			});

			// 该面的逐顶点 UV
			Map<String, double[]> vertexUvs = new HashMap<>();
			if (face.has("uv") && face.get("uv").isJsonObject()) {
				for (Map.Entry<String, JsonElement> uv : face.getAsJsonObject("uv").entrySet()) {
					if (uv.getValue().isJsonArray()) {
						vertexUvs.put(uv.getKey(), toDoubleArray(uv.getValue().getAsJsonArray()));
					}
				}
			}

			int base = mesh.vertices.size();
			int[] indices = new int[vertexKeys.size()];
			for (int i = 0; i < vertexKeys.size(); i++) {
				String key = vertexKeys.get(i).getAsString();
				double[] pos = vertexPositions.get(key);
				if (pos == null || pos.length < 3) continue;
				// 局部坐标 + origin（÷16 在 loadModels 统一 applyScale）
				Vertex vtx = new Vertex(new Vector3f((float) (pos[0] + ox), (float) (pos[1] + oy), (float) (pos[2] + oz)),
						new Vector3f(0, 0, 0));
				double[] uv = vertexUvs.get(key);
				if (uv != null && uv.length >= 2) {
					// V 翻转与 OBJ 导出一致
					vtx.u = (float) (uv[0] / texW);
					vtx.v = (float) (1 - uv[1] / texH);
				} else {
					vtx.u = 0;
					vtx.v = 0;
				}
				mesh.vertices.add(vtx);
				indices[i] = base + i;
			}
			// 三角化（扇面）
			for (int i = 2; i < vertexKeys.size(); i++) {
				mesh.faces.add(new Face(new int[]{indices[0], indices[i - 1], indices[i]}));
			}
		}

		// 为每个拆分出的 mesh 解析贴图资源
		for (Map.Entry<Integer, RawMesh> entry : meshesByTexture.entrySet()) {
			if (entry.getKey() >= 0) resolveMeshTexture(entry.getValue(), entry.getKey(), textureLocations);
		}
		return new ArrayList<>(meshesByTexture.values());
	}

	// ==================== 工具 ====================

	private static void getArray(double[] target, JsonObject obj, String key) {
		if (obj.has(key) && obj.get(key).isJsonArray()) {
			JsonArray arr = obj.getAsJsonArray(key);
			for (int i = 0; i < target.length && i < arr.size(); i++) {
				target[i] = arr.get(i).getAsDouble();
			}
		}
	}

	private static double[] toDoubleArray(JsonArray arr) {
		double[] result = new double[arr.size()];
		for (int i = 0; i < arr.size(); i++) {
			result[i] = arr.get(i).getAsDouble();
		}
		return result;
	}

	/** 计算四边形两条对角线的叉积长度平方，用于判断面是否退化（面积为 0）。 */
	private static double faceAreaSquared(float[][] corners) {
		if (corners.length < 4) return 0;
		// 两条对角线：c0->c2 与 c1->c3
		double ax = corners[2][0] - corners[0][0];
		double ay = corners[2][1] - corners[0][1];
		double az = corners[2][2] - corners[0][2];
		double bx = corners[3][0] - corners[1][0];
		double by = corners[3][1] - corners[1][1];
		double bz = corners[3][2] - corners[1][2];
		double cx = ay * bz - az * by;
		double cy = az * bx - ax * bz;
		double cz = ax * by - ay * bx;
		return cx * cx + cy * cy + cz * cz;
	}

	private static String sanitize(String s) {
		return s.toLowerCase(Locale.ROOT).replace('\\', '/').replaceAll("[^a-z0-9/._-]", "_");
	}
}
