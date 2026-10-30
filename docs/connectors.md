# 列车风挡

通过车型的 `connector` 字段，可以为列车配置车内风挡、车外折棚，或组合多份不同的风挡配置。适用于方速 MTR3 与 MTR4 版本。

## 配置位置

在 `assets/mtr/mtr_custom_resources.json` 根对象的 `connectors` 中定义风挡，再由车型引用：

- MTR3：在 `custom_trains.<车型key>` 中填写 `connector`。
- MTR4：在 `vehicles[]` 对应车型条目中填写 `connector`。

也可使用 `assets/fangsu/custom_trains.json`。共享定义与车型引用应放在同一份 JSON 的根对象中。

## 字符串与数组

只使用一份配置时，填写共享名称：

```json
"connector": "my_bellows"
```

使用不同的内外风挡时，填写数组：

```json
"connector": ["my_exterior", "my_interior"]
```

数组中的每一项都会独立读取和绘制，可以有不同贴图、截面、位置及褶皱参数。也可以在 `connector` 或数组元素中直接填写完整配置对象。

## 绘制方向 side

`side` 填写在每份风挡定义中：

| 值 | 绘制范围 |
| --- | --- |
| `exterior` | 车外可见面 |
| `interior` | 车内可见面，绕序与法线朝向车内 |
| `both` | 同一份几何同时绘制车内和车外两面 |

默认值为 `both`。需要不同内外结构时，使用数组并分别指定 `exterior`、`interior`。例如外侧按贴图生成折棚，内侧配置为平整墙面与地板。

## 示例：外侧折棚，内侧平整

下面展示 `connectors` 定义和 MTR4 车型引用；车型的其它字段按 MTR 正常填写。

```json
{
  "connectors": {
    "my_exterior": {
      "side": "exterior",
      "texture": "mypack:textures/train/bellows.png",
      "points": [[-0.5, 0], [-0.5, 1], [0.5, 1], [0.5, 0]],
      "closed": true,
      "offset": 0.04,
      "segments": 32,
      "fold": {
        "source": "texture",
        "axis": "u",
        "samples": 256,
        "polarity": "bright_out",
        "amplitude": 0.10,
        "compress_gain": 0.5
      }
    },
    "my_interior_left": {
      "side": "interior",
      "texture": "mypack:textures/train/wall.png",
      "points": [[-0.5, 0], [-0.5, 1]],
      "closed": false,
      "fold": {"source": "none"},
      "light": "interior"
    },
    "my_interior_right": {
      "side": "interior",
      "texture": "mypack:textures/train/wall.png",
      "points": [[0.5, 1], [0.5, 0]],
      "closed": false,
      "fold": {"source": "none"},
      "light": "interior"
    },
    "my_interior_roof": {
      "side": "interior",
      "texture": "mypack:textures/train/roof.png",
      "points": [[-0.5, 1], [0.5, 1]],
      "closed": false,
      "fold": {"source": "none"},
      "light": "interior"
    },
    "my_interior_floor": {
      "side": "interior",
      "texture": "mypack:textures/train/floor.png",
      "points": [[0.5, 0], [-0.5, 0]],
      "closed": false,
      "fold": {"source": "none"},
      "light": "interior"
    }
  },
  "vehicles": [
    {
      "id": "my_vehicle",
      "connector": [
        "my_exterior",
        "my_interior_left",
        "my_interior_right",
        "my_interior_roof",
        "my_interior_floor"
      ]
    }
  ]
}
```

MTR3 可将相同数组填入 `custom_trains.<车型key>.connector`。

两点开口截面会沿两车之间展开为一条面带，适合单独配置墙、顶棚或地板。闭环至少需要三个点，开口至少需要两个不同的点。请按示例保持点的顺序，以得到正确的可见面朝向。

## 原版风挡的处理

成功绘制自定义配置后，方速按 `side` 屏蔽对应的原版风挡层，避免重叠：

| 成功接管的配置 | 原版外侧 | 原版内侧 |
| --- | --- | --- |
| 只有 `exterior` | 屏蔽 | 继续绘制 |
| 只有 `interior` | 继续绘制 | 屏蔽 |
| 数组同时包含两种方向，或使用 `both` | 屏蔽 | 屏蔽 |

内侧层包含原版侧壁、顶棚和地板。配置任何 `interior` 项后，需要自行提供希望保留的内侧面；上面的示例分别配置了四个面。未填写 `connector`、填写 `false` 或空数组时，使用原版风挡。

如需隐藏某一侧而不显示替代结构，可以给该侧配置使用一张**全透明贴图**，并设置 `fold.source: "none"`。配置仍需包含有效截面。透明像素不会形成褶皱，成功接管后对应原版层也会被屏蔽。

配置无效、贴图缺失或几何无法生成时，该连接处回退原版，避免留下不完整的风挡。挡板不受 `connector` 影响。

## 贴图明暗与褶皱

`fold.source: "texture"` 按贴图的亮度分布生成真实几何：

- `axis: "u"` 读取水平中线；`axis: "v"` 读取垂直中线。
- `bright_out` 表示亮处向截面外侧凸出；`bright_in` 反向。
- 窄亮带形成窄凸棱；长亮带形成持续凸出的平台，平台两端保持各自的位置。
- 宽暗带形成宽凹槽。凸棱和平台的间距随贴图分布，不会强制改成等间距。

采样按该条线上最暗、最亮的有效颜色归一化，因此不必使用纯黑和纯白。透明度小于8的像素不参与明暗统计，空缺由相邻有效值补齐。纯色或全透明贴图生成光滑表面。

建议使用128或256次采样，避免丢失窄条纹。几何会在贴图曲线的转折处增加环，保留长亮带、长暗带的起止边界。轴向贴图范围由 `uv.u_range` 指定；贴图驱动褶皱时，通常保持 `uv.per_segment: false`。

`amplitude` 控制谷底到凸起的径向高度。`offset` 控制整份配置沿截面径向移动，单位为米，默认0：正数向外，负数向内。可用正 `offset` 将外层折棚与内层平面错开。角点沿径向移动，数值不等同于各侧壁的法向间距。

两车靠近时，折距缩短，折深按 `compress_gain` 增加。`nominal_length` 可指定未压缩长度；省略时使用首次有效观测值。

其它褶皱模式：

| `fold.source` | 用途 |
| --- | --- |
| `texture` | 按贴图明暗确定不等间距折棱及平台 |
| `none` | 平整墙面、地板、顶棚或光滑风挡 |
| `accordion` | 固定等间距折棚，`count` 控制凸棱数量 |
| `sine` | 按正弦周期生成褶皱 |

## MTR4 的车型识别

MTR4 使用车型 `models[].modelPropertiesResource` 中的风挡贴图键识别整组 `connector`。请给自定义车型使用独立的 model properties 和外侧风挡贴图路径，例如：

```json
{
  "gangwayOuterSideResource": "mypack:textures/train/bellows.png",
  "gangwayOuterTopResource": "mypack:textures/train/bellows.png",
  "gangwayOuterBottomResource": "mypack:textures/train/bellows.png"
}
```

其它零件、内侧贴图和风挡尺寸按车型正常填写。避免多个需要不同配置的车型共用同一风挡识别贴图。单独的内饰配置可使用各自的墙面、地板贴图，无需将它们改为外侧识别贴图。

保留 MTR4 的 `hasGangway1/2` 或 MTR3 的 `has_gangway_connection`：这些字段控制连接锚点是否提交，关闭后自定义风挡也无法绘制。

相邻车厢应引用同一组共享配置。MTR4 会检查两端的配置 id 与绘制方向是否匹配；不一致时默认回退原版，`id_mismatch: "skip"` 可跳过整个连接处。

## 字段参考

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `id` | 共享名称；内联时为车型key | 连接兼容性标识；数组中的内联对象建议明确填写 |
| `side` | `both` | `interior` / `exterior` / `both` |
| `texture` | 必填 | 本份配置的贴图及 texture 模式的亮度来源 |
| `points` | 必填 | 截面点 `[u,v]`；u为-0.5..0.5，v为0..1 |
| `closed` | `true` | 是否闭环；重复末点会去除并强制闭合 |
| `offset` | `0` | 截面径向移动量，单位米 |
| `segments` | `10` | 基础轴向细分，2..64；texture模式另加入贴图转折环 |
| `inset` | `0` | 两端沿连接轴调整的位置，单位米 |
| `nominal_length` | 首次有效观测 | 未压缩长度，单位米 |
| `fold.source` | `texture` | texture / none / accordion / sine |
| `fold.axis` | `u` | 采样水平(u)或垂直(v)中线 |
| `fold.samples` | `64` | 明暗采样数，8..256 |
| `fold.amplitude` | `0.04` | 谷底到凸起的径向高度，单位米 |
| `fold.polarity` | `bright_out` | bright_out / bright_in |
| `fold.compress_gain` | `0.5` | 压缩时折深增加系数，0..2 |
| `fold.phase` | `0` | texture / sine 模式相位，0..1 |
| `fold.count` | `8` | accordion模式凸棱数量，1..32 |
| `fold.period` | `2` | sine模式每个周期占用的基础环数 |
| `uv.u_range` / `uv.v_range` | `[0,1]` | 轴向、周向UV范围 |
| `uv.per_segment` | `false` | 每个生成面段重置轴向UV |
| `light` | `outdoor` | outdoor / interior；后者在线路运营时使用内饰全亮 |
| `id_mismatch` | `fallback` | MTR4 两端配置不一致时 fallback / skip |
| `cap` | `false` | 预留字段，当前不封口 |

`points` 也接受 `{"u":0,"v":1,"uv":[0,0.5]}` 形式的逐点UV覆盖。闭环允许凹形截面，开口不会自动补上首尾之间的面。

## 资源重载

修改配置或贴图后按 **F3+T** 重新加载资源。方速会重读定义、贴图亮度与折棱位置。NTE/ANTE 兼容模式使用原版风挡。
