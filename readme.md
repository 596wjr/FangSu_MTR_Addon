# [MTR3] FangSu MTR Addon · 方速 MTR 扩展

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-1.18.2~1.20.1-brightgreen)
![Version](https://img.shields.io/badge/Version-1.0.0--rc5-orange)
[![Fabric](https://img.shields.io/badge/Platform-Fabric-dbd0b4)](https://fabricmc.net/)
[![Forge](https://img.shields.io/badge/Platform-Forge-e04e14)](https://minecraftforge.net/)

**FangSu MTR Addon** 是一个以 [Minecraft Transit Railway (MTR)](https://modrinth.com/mod/minecraft-transit-railway) 为前置的 Minecraft 模组，旨在为 MTR 模组增加更多实用的轨道交通附属设施，并提供一套简洁的接口以简化 MTR 扩展开发的流程。

> **官方网站：**[https://mtr.fangsu.top/](https://mtr.fangsu.top/)
>
> **源代码：**[https://github.com/596wjr/FangSu_MTR_Addon/](https://github.com/596wjr/FangSu_MTR_Addon/)

---

## 功能特性

### 站台屏蔽门系统

- **屏蔽门 (Screendoor)** — 带隔离指示灯与门状态灯的标准站台屏蔽门
- **屏蔽门玻璃 (Screendoor Glass)** — 透明玻璃版本屏蔽门
- **中央控制箱 (Central Control)** — 屏蔽门系统的中央控制方块
- **端门 (Duanmen)** — 站台两端的应急端门

### 检票闸机与票务系统

- **OBJ 格式 3D 检票闸机** — 使用自定义 OBJ 模型的高品质闸机，支持多种模型选择
- **售票机 (Ticket Machine)** — 交互式售票终端，支持购买单程票
- **单程票 (Single Journey Ticket)** & **IC 卡 (IcCard)** — 完整的票务物品体系
- **与 MTR 票务系统集成** — 无缝兼容 MTR 的计费与闸机逻辑

### 乘客信息系统

- **PIDS (列车到站信息屏)** — 实时显示列车到站信息，支持自定义布局与 JavaScript 脚本
- **吊板显示器 (Diaoban)** — 悬挂式信息显示屏，适合站厅/站台使用
- **路线信息牌 (RIS)** — 显示路线图的站内指示牌
- **车站信息牌 (SIS)** — 展示车站结构与出口信息

### 站牌与标志系统

- **可放置站牌 (Sign)** — 灵活放置的车站标识牌
- **墙上标志 (Sign on Wall)** — 贴墙安装的标志
- **高级告示板 (Adv Board)** — 支持自定义排版与内容的高级公告板

### 自定义列车

- **功能型自定义列车 (Functional Custom Trains)** — 可自定义外观与功能的列车
- **列车 LCD 显示屏** — 支持 MTR 风格 LCD 内容展示
- **自定义列车渲染器** — 灵活的渲染框架

### 自定义电梯

- **现代纹理电梯 (Modern Textured Lift)** — 基于 MTR 电梯扩展，支持自定义模型与纹理
- 通过自定义物品系统可加载不同的电梯外观

### JavaScript 脚本引擎

- 基于 GraalVM JavaScript (GraalJS) 的高性能脚本引擎
- PIDS、标志等方块支持 **JS 脚本** 实现动态内容渲染
- 内置 **GIF 支持**、图形纹理绘制、模型辅助等丰富 API
- 用户可通过脚本实现高度自定义的显示内容

### OBJ 3D 模型引擎

- 完整的 OBJ 格式加载、解析、写入库（基于 javagl）
- 闸机、电梯等方块支持自定义 OBJ 模型切换
- 高性能渲染，支持光影模组兼容

### 丰富的配置系统

- 基于 JSON 的灵活配置框架
- 支持滑块、开关、下拉选择、文本输入等多种控件
- 每个方块均可独立配置

### 配套工具

- **扳手 (Wrench)** — 用于配置和调整方块的便捷工具

---

## 前置依赖

| 依赖                                | 类型          | 说明                       |
| ----------------------------------- | ------------- | -------------------------- |
| **Minecraft Transit Railway (MTR)** | 必选          | 核心前置模组，版本 ≥ 3.2.2 |
| **Architectury API**                | 必选          | 多平台抽象层               |
| **Fabric Loader**                   | Fabric 端必选 | ≥ 0.18.4                   |
| **Fabric API**                      | Fabric 端必选 | 提供 Fabric 平台基础能力   |
| **Forge**                           | Forge 端必选  | Minecraft Forge 环境       |

---

## 快速开始

### 安装

1. 安装对应版本的 **Minecraft** 和对应平台的模组加载器（Fabric / Forge）
2. 下载安装 **Minecraft Transit Railway (MTR)** 模组
3. 下载安装 **Architectury API** 模组
4. 下载本模组的对应版本 jar 文件并放入 `mods` 文件夹
5. 启动游戏即可体验

### 构建

本项目使用 Gradle 构建，支持多平台：

```bash
# Fabric 端构建
gradlew fabric:build

# Forge 端构建
gradlew forge:build

# 指定 MC 版本（默认 1.20.1）
gradlew fabric:build -Pgame_version=1.19.2
```

构建产物位于各子项目的 `build/libs/` 目录下。

---

## 项目结构

```
fangsu/
├── common/          # 共享代码（核心逻辑、方块、实体、网络等）
│   └── src/main/java/com/fangsu/
│       ├── blocks/            # 方块定义
│       ├── blockEntities/     # 方块实体
│       ├── items/             # 物品
│       ├── train/             # 列车相关
│       ├── ticketSystem/      # 票务系统
│       ├── render/            # 渲染引擎（OBJ、脚本渲染等）
│       ├── drawing/           # 绘制系统（PIDS、标志等）
│       ├── scripting/         # JavaScript 脚本引擎
│       ├── ui/                # 用户界面
│       ├── network/           # 网络通信
│       ├── customItem/        # 自定义物品/内容系统
│       └── utils/             # 工具类
├── fabric/          # Fabric 平台入口与配置
├── forge/           # Forge 平台入口与配置
└── gradle/          # Gradle 构建配置
```

---

## 多版本支持

本项目通过自定义条件编译引擎，使用 **一套代码** 支持多个 Minecraft 版本：

| Minecraft 版本 | Fabric | Forge |
| -------------- | ------ | ----- |
| 1.18.2         | 是     | 是    |
| 1.19.2         | 是     | 是    |
| 1.19.3         | 是     | 是    |
| 1.19.4         | 是     | 是    |
| 1.20.1         | 是     | 是    |

通过 `-Pgame_version=<版本>` 参数切换目标版本。

---

## 贡献

欢迎提交 Issue 和 Pull Request！

- [问题追踪](https://github.com/596wjr/FangSu_MTR_Addon/issues)
- 提交 PR 前请确保代码风格一致，并通过编译测试

---

## 许可证

本项目基于 **MIT License** 开源。详见 [LICENSE](LICENSE) 文件。

---

## 赞助

如果您喜欢此项目，欢迎[赞助](https://afdian.com/a/596wjr)支持，您的支持将帮助项目持续发展！

---

## 致谢

- **Zbx1425** — 参考并使用了 [mtr-nte](https://github.com/zbx1425/mtr-nte) 的渲染组件（`sowcer` / `sowcerext`）及构建脚本逻辑
- **javagl** — OBJ 模型加载与解析库
- **Minecraft Transit Railway (MTR)** — 为本模组提供基础的轨道交通系统
- 所有参与测试与反馈的玩家和贡献者

---

## AIGC 声明

本项目部分代码和注释由 AI 生成，仅供辅助开发参考。所有代码均已人工审查和测试，确保符合项目质量标准。
