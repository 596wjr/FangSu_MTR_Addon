# Potato Platform 1.1.2

原作者：Potato 土豆泥；Copyright (c) 2026 Potato Curved Platform contributors。MIT 授权，按用户与原作者许可移植。

`common/libs/potato-platform-geometry-1.1.2.jar` 保留原模组与 Minecraft 无关的 geometry 字节码，排除加载器入口、Mixin、网络及平台专用类。PlatformSource 改用手动 JSON 解码以兼容旧 MC 自带 Gson；其余几何算法保持原字节码。几何库和其 JTS 依赖在 Fabric / Forge 成品中内嵌并重定位，不要求安装 Potato Platform。反编译核对材料与提取脚本见工作区 deliverables/potato-platform-reference。

JTS 1.20.0 来自参考模组内嵌依赖；同时保留官方许可文本及说明：[JTS 1.20.0](https://github.com/locationtech/jts/tree/1.20.0)。
