# Data Agent (Pi Agent Mod)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.2-blue)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-red)](https://minecraft.net/)

---

## 📖 简介 / Introduction

### 中文
**Data Agent**（又称 **Pi Agent Mod**）是一个专为 AI 辅助 Minecraft 开发而设计的 NeoForge 开发工具 Mod，而非普通的玩法内容 Mod。

它的核心定位是作为 **Minecraft 运行时的统一数据访问层**，为外部 AI 智能体（如 VSCode、Codex、Claude Code 等）提供准确、实时且结构化的游戏运行时数据（如注册表、配方、战利品表、进度、村民交易等）。

通过它，AI 能够直接获取经过所有 Mod 动态处理后的最终运行时状态，并在本地编写或修改整合包配置、KubeJS / CraftTweaker 脚本，最后通过 API 触发重载进行闭环的自动化验证。

### English
**Data Agent** (also known as **Pi Agent Mod**) is a NeoForge developer tool mod designed specifically for AI-assisted Minecraft development, rather than a traditional content mod.

It serves as a **unified data access layer for the Minecraft Runtime**, providing external AI agents (e.g., VSCode, Codex, Claude Code) with accurate, real-time, and structured game runtime data (registries, recipes, loot tables, advancements, villager trades, etc.).

With this mod, AI can directly fetch the final runtime state of the game after all dynamic modifications by other mods, generate or modify modpack configurations, KubeJS, or CraftTweaker scripts locally, and trigger reloading via API for automated verification.

---

## ✨ 核心特性 / Key Features

### 中文
- **运行时优先 (Runtime First)**：所有数据直接从游戏内 Registry 和 Manager 提取，确保获取的是经过所有 Mod、数据包、脚本动态修改后的最终状态。
- **丰富的查询接口**：
  - **注册表查询**：获取物品（富集堆叠、耐久、组件、属性等）、方块（硬度、抗暴等）、流体、实体类型、魔咒、生物群系和维度信息。
  - **合成配方查询**：支持按输入、输出或配方类型过滤，返回完整的配方 JSON。
  - **其他数据查询**：支持战利品表、进度、以及村民交易。
- **事件订阅与监听**：支持通过 WebSocket 或事件系统监控游戏重载、聊天消息等。
- **本地批量导出 (Export Service)**：支持一键将运行时数据批量导出为本地 JSON 缓存文件。
- **自动化重载 (Hot Reload)**：提供 API 接口异步触发游戏重载，便于 AI 实现“生成-重载-验证”的闭环开发流。
- **高性能 & Java 25 虚拟线程**：内置轻量级 HTTP 服务器，默认运行在端口 `24482`，基于 Java 25 虚拟线程提供非阻塞高并发处理。

### English
- **Runtime First**: All data is retrieved directly from the in-game Registry and Manager, ensuring it represents the final state modified by all mods, datapacks, and scripts.
- **Rich Query APIs**:
  - **Registry Queries**: Retrieve items (enriched with stack size, durability, components, attributes), blocks (hardness, resistance, etc.), fluids, entities, enchantments, biomes, and dimensions.
  - **Recipe Queries**: Filter recipes by input, output, or recipe type, returning complete JSON structures.
  - **Other Queries**: Retrieve loot tables, advancements, and villager trades.
- **Event Subscriptions**: Monitor game reloads, chat messages, and server statuses via WebSocket or event systems.
- **Local Bulk Exporting**: Export all runtime data into local JSON cache files with one command.
- **Hot Reloading**: Expose an API endpoint to asynchronously trigger game reloading, enabling AI agents to complete the "generate-reload-verify" closed-loop development flow.
- **High Performance & Java 25**: Built-in lightweight HTTP server running on port `24482` by default, leveraging Java 25 Virtual Threads for non-blocking concurrent request handling.

---

## 🛠️ 安装与构建 / Installation & Building

### 中文
#### 运行要求
- **Minecraft**: `1.21.4`
- **NeoForge**: `26.2`
- **Java**: `25` 及以上

#### 构建步骤
1. 克隆或下载本仓库。
2. 使用命令行或您喜爱的 IDE（推荐 IntelliJ IDEA / VSCode）打开项目。
3. 运行以下 Gradle 命令构建 Mod：
   ```bash
   ./gradlew build
   ```
4. 编译完成的 jar 文件将位于 `build/libs/` 目录下，将其放入 Minecraft 的 `mods` 文件夹即可。

### English
#### Requirements
- **Minecraft**: `1.21.4`
- **NeoForge**: `26.2`
- **Java**: `25` or higher

#### Building
1. Clone or download this repository.
2. Open the project in your terminal or IDE (IntelliJ IDEA / VSCode recommended).
3. Run the Gradle build command:
   ```bash
   ./gradlew build
   ```
4. The compiled JAR file will be located in the `build/libs/` directory. Move it to your Minecraft `mods` folder.

---

## 🎮 游戏内指令 / In-game Commands

### 中文
- `/pi-agent ping`：检查内置 HTTP API 服务器的运行状态及当前端口。
- `/pi-agent export [targetDir]`：将游戏内的所有运行时数据批量导出为 JSON 缓存到指定目录（默认导出至 `./.pi-agent/dumps`）。

### English
- `/pi-agent ping`: Check the status and active port of the built-in HTTP API server.
- `/pi-agent export [targetDir]`: Export all runtime data into a target directory (defaults to `./.pi-agent/dumps`).

---

## ⚙️ 配置项 / Configuration

### 中文
配置文件路径为：`config/data_agent-common.toml`（亦可在游戏内 Mods 列表 -> Data Agent -> Config 菜单中直接修改）：
- `enableApi` (Boolean): 是否开启 API 服务器，默认 `true`。
- `apiPort` (Int): API 服务器监听的端口，默认 `24482`。修改后会自动重启服务器。

### English
The config file is located at `config/data_agent-common.toml` (or configurable via in-game Mods List -> Data Agent -> Config):
- `enableApi` (Boolean): Enables/disables the API server. Defaults to `true`.
- `apiPort` (Int): The port for the API server. Defaults to `24482`. Editing it will automatically restart the server on the new port.

---

## 📡 API 接口速览 / API Quick Reference

### 中文
默认基础 URL 为 `http://localhost:24482`。
- `GET /api/v1/status`：获取 Mod 在线状态及加载的 Mod 列表。
- `POST /api/v1/reload`：异步触发游戏内资源重载（等同于执行 `/reload`）。
- `GET /api/v1/registry/<type>`：查询注册表数据（支持 `item`, `block`, `fluid`, `entity_type`, `enchantment`, `biome`, `dimension`）。
- `GET /api/v1/recipes`：查询已加载的合成配方，支持输入/输出筛选。
- 更多详细 API 说明请参考：[API Specification (中文)](docs/08_api_specification.md)。

### English
The base URL is `http://localhost:24482` by default.
- `GET /api/v1/status`: Retrieve API status and loaded mods.
- `POST /api/v1/reload`: Asynchronously trigger in-game reload (same as `/reload`).
- `GET /api/v1/registry/<type>`: Query registries (supports `item`, `block`, `fluid`, `entity_type`, `enchantment`, `biome`, `dimension`).
- `GET /api/v1/recipes`: Query active crafting recipes with input/output filters.
- For complete details, see [API Specification (中文)](docs/08_api_specification.md).

---

## 📄 文档指南 / Documentation Guide

- [01. Architecture Overview / 架构概述](docs/01_architecture_overview.md)
- [02. Agent Mod Specification / 智能体 Mod 规范](docs/02_agent_mod_spec.md)
- [03. MCP Integration / MCP 协议集成](docs/03_mcp_integration.md)
- [04. Local Generation Guide / 本地生成指南](docs/04_local_generation_guide.md)
- [05. NeoForge Runtime Query / NeoForge 运行时查询](docs/05_neoforge_runtime_query.md)
- [06. Development Roadmap / 开发路线图](docs/06_development_roadmap.md)
- [07. Development Specifications / 开发技术指标说明](docs/07_development_specifications.md)
- [08. API Specification / API 接口规范说明书](docs/08_api_specification.md)

---

## ⚖️ 开源协议 / License

本项目采用 [MIT License](LICENSE) 授权。

This project is licensed under the [MIT License](LICENSE).
