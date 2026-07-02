# 06. Pi Agent Mod - 开发顺序计划 (Roadmap)

本手册规划了 **Pi Agent Mod** 的分阶段开发路线图。考虑到游戏运行时的线程安全与数据序列化的复杂性，建议按照以下阶段推进开发，以确保每个阶段均可独立测试并交付。

---

## 阶段一：基础架构与网络桥梁 (基础搭建)
**目标**：完成 Mod 生命周期初始化与轻量级 Web 服务，打通外部 AI 客户端与游戏进程的连接。

* **开发内容**：
  * `agent-core`：初始化 Mod，读取端口等配置参数。实现主线程任务调度器（线程安全的 Executor 包装器）。
  * `http-api`：启动后台守护虚拟线程，搭建 Java `HttpServer`，提供最基础的 `/api/v1/reload` 接口。
  * `debug-tools`：在游戏内注册最简 `/pi-agent` 命令（如 `/pi-agent ping`）用于连通性校验。
* **验证标准**：
  * 游戏启动时控制台能正常监听 `24482` 端口。
  * 外部浏览器或 AI 命令行执行 `curl http://localhost:24482/api/v1/reload`，游戏控制台输出重载日志且网络请求成功返回。

---

## 阶段二：静态只读数据与一键导出 (数据同步)
**目标**：开发注册表与村民交易查询，并支持一键将数据导出至本地工作区以供 AI 静态分析。

* **开发内容**：
  * `registry-service`：实现基础物品、方块、流体、维度和生物群系的查询接口。
  * `export-service`：编写批量 JSON 序列化逻辑，能够将注册表快照与村民交易清单直接写入 `.minecraft/dumps/` 目录。
  * 完善 `debug-tools`，增加 `/pi-agent export` 游戏内指令。
* **验证标准**：
  * 能够通过接口 `GET /registry/item?search=wool` 准确查到所有羊毛物品。
  * 触发导出后，本地磁盘生成体积完整的 `items.json` 与 `biomes.json` 等快照文件。

---

## 阶段三：配方、战利品与成就接口 (核心逻辑)
**目标**：引入 Mojang Codec 系统，解析经过各 Mod 最终计算后的运行时配方与掉落表。

* **开发内容**：
  * `recipe-service`：接入 `RecipeManager`，支持 `RecipeHolder` 遍历，并利用官方 Codec 将各种非标配方（包括 KubeJS/CraftTweaker 动态加入的配方）转换为标准的 JSON 返回。
  * `loot-service`：接入 `LootDataManager`，序列化物品与方块的掉落规则。
  * `advancement-service`：解析服务器当前的进度树。
* **验证标准**：
  * AI 能够通过 `GET /recipes?output=minecraft:diamond` 检索出钻石的合成路径，且内容字段与官方 datapack 规范完全吻合。

---

## 阶段四：MCP 协议栈与 Stdio 桥接 (AI 适配)
**目标**：封装 Model Context Protocol (MCP) 接口，开发本地 Stdio 桥接脚本，使 Mod 完美融入 AI 生态。

* **开发内容**：
  * 在 `http-api` 中提供符合 MCP 规范的 SSE (Server Sent Events) 接口。
  * 在本地工作区 `.pi-agent/` 中编写 Node.js 桥接脚本 `mcp-bridge.js`。
  * 配置 Cursor 与 Claude Desktop，导入桥接器。
* **验证标准**：
  * 在 Claude Desktop 或 Cursor 聊天框中，AI 能够通过“调用工具”直接查询当前游戏内的物品或配方，无需人工复制粘贴。

---

## 阶段五：游戏事件订阅与主动推送 (动态交互)
**目标**：支持双向异步交互，当游戏内发生热重载或核心状态改变时，主动通知 AI 智能体。

* **开发内容**：
  * `event-service`：监听 NeoForge 端的 `RecipesUpdatedEvent`（配方重载完成）、`PlayerEvent`（玩家登录/聊天）等。
  * 在 `http-api` 中利用 SSE 或 WebSocket 建立事件推送管道。
* **验证标准**：
  * 开发者在游戏内手动输入 `/reload` 成功后，AI 智能体终端立刻收到包含 `resource_reload` 的推送消息。

---

## 阶段六：闭环自动化测试与自愈 (功能完备)
**目标**：联调所有模块，实现 AI 编写代码 -> 触发重载 -> 接口验证 -> 修复纠错的闭环开发体验。

* **开发内容**：
  * 联合测试本地 Datapack / KubeJS / CraftTweaker 自动生成器。
  * 优化错误捕获，在重载失败时提供详尽的堆栈或解析错误报告给 AI。
* **验证标准**：
  * AI 智能体在面对“修改配方”指令时，能够独立完成代码编写、重载调用、接口比对确认的全套流程，且遇到语法错误时能根据返回的报错日志自动修复。
