# OBJ 法线贴图（MTR3 / MTR4）

两版使用相同配置。OBJ 需要 UV，并通过 `mtllib` 引用 MTL。在对应材质中声明切线空间 RGB 法线：

```mtl
newmtl metal
Kd 1 1 1
map_Kd ../textures/metal.png
norm -bm 1.0 ../textures/metal_n.png
```

```obj
mtllib materials/model.mtl
usemtl metal
```

贴图路径相对于 **MTL 文件所在目录**，也可以直接写 `包名:textures/metal_n.png`。材质名仍可带 `#exterior`、`#interior`、`#exteriortranslucent`、`#interiortranslucent` 和 `flipv=1` 等现有选项；例如 MTL 的 `metal` 可供 OBJ 的 `metal#exterior` 使用，精确同名的 MTL 材质优先。

- 支持 `norm`、`map_Kn`，以及 `map_Bump` / `bump` 兼容别名；四者均按 **RGB 法线贴图**读取，不转换灰度高度图。
- 使用 OpenGL 风格（+Y）的 RGB 法线，平面颜色约为 `(128, 128, 255)`。DirectX 风格贴图需先反转绿色通道；不读取 LabPBR 的 RG 重建/高度等通道。
- 可省略 `-bm`，默认强度为 1。`-bm 0` 使用几何法线，正数控制凹凸强度；不接受负数、非有限数和其他 MTL 贴图选项。路径可用双引号包裹。
- 自带 GL 绘制和 BufferSource 绘制均支持，包括安装 NTE 后的兼容路径和半透明材质。保持原有光照贴图、透明裁剪和雾效。
- `light` / `lighttranslucent` 为原有自发光材质，不应用方向光法线效果。
- 外部光影包启用或 GL4ES 兼容模式下沿用原有渲染路径；此时方速自带法线效果不启用。外部光影包自身的材质规则仍由光影包决定。
- 声明法线的材质跳过仅包含颜色层的图集合并，以保持两张贴图 UV 一致。不要手工给颜色图合并图集却仍引用未合并的法线图。
- 法线贴图缺失时会记录警告并回退颜色材质；没有法线声明的模型保留原来的渲染行为。不会自动寻找 `_n.png`。
- 材质复制、批处理区分、二进制模型缓存和 OBJ/MTL 导出会保留法线资源与强度；旧缓存没有这些字段时按无额外法线处理。

脚本创建的 `RawMesh` 也可通过 `mesh.materialProp.normalTexture`（`ResourceLocation`）和 `mesh.materialProp.normalScale` 设置法线。若用 `RawModel.meshList`，按已有材质修改约定重建键，避免原地修改哈希键。颜色与法线必须共用同一套 UV。

## 手动验收

1. 对同一带 UV 的模型分别使用无 `norm`、平面法线和带明显凹凸的法线。无配置效果保持原样；平面法线接近原效果，凹凸图产生随表面朝向变化的明暗。
2. 检查 `-bm 0` / `0.5` / `2`、模型旋转、镜像 UV、`flipv=1`；没有 UV 的面应稳定回退几何法线。
3. 对颜色图相同、法线图不同或强度不同的两材质并排比较，确认不会被批处理错误合并；复制与缓存重载后保持各自效果。
4. 分别检查普通环境、安装 NTE、半透明材质，以及外部光影包启用后的基础材质回退。
5. 使用放在子目录的 MTL、`../textures` 路径和缺失法线资源，确认路径解析与回退；资源包重载后再检查。

按仓库约定未运行 Gradle 编译，以上游戏内验收需要手动完成。
