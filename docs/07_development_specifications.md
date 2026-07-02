# 07. Pi Agent Mod - 开发规范与目录结构

为了保证 **Pi Agent Mod** 代码的优雅性、安全性与高维护度，本规范制定了详细的 Java 代码目录结构、开发规范、线程安全规范及 JSON 命名标准。

---

## 1. Java 项目目录与包结构

整个 Java 代码应当组织在 `com.mrshellad.dataagent` 包下，其模块结构组织如下：

```
src/main/java/com/mrshellad/dataagent/
├── DataAgent.java                    // Mod 入口类，注册事件总线，管理生命周期
├── Config.java                       // NeoForge Mod 配置文件定义
│
├── core/                             // agent-core 模块
│   ├── ThreadScheduler.java          // 提供 Minecraft 主线程安全调度的工具类
│   └── ApiConfig.java                // 端口与 API 参数模型
│
├── service/                          // 业务查询服务层
│   ├── RegistryQueryService.java     // 注册表静态/动态查询业务
│   ├── RecipeQueryService.java       // 配方解析与 Codec 转换业务
│   ├── LootQueryService.java         // 战利品表序列化业务
│   ├── VillagerTradeService.java     // 村民交易表实例化提取业务
│   └── ExportService.java            // 批量 JSON 数据导出器
│
├── api/                              // http-api 模块 (Web 服务)
│   ├── HttpServerManager.java        // HttpServer 开启/关闭逻辑
│   ├── HttpHandlerBase.java          // HTTP 请求处理基类，封装通用 JSON 响应与异常捕获
│   └── handlers/                     // 各路由处理器
│       ├── RegistryHandler.java      // 处理 /registry/*
│       ├── RecipeHandler.java        // 处理 /recipes
│       ├── LootTableHandler.java     // 处理 /loot-tables
│       ├── VillagerTradeHandler.java // 处理 /villager-trades
│       └── ReloadHandler.java        // 处理 /reload
│
├── event/                            // event-service 模块
│   └── GameEventListener.java        // 监听配方更新与游戏生命周期事件
│
└── command/                          // debug-tools 模块
    └── PiAgentCommand.java           // 游戏内 /pi-agent 指令注册与逻辑
```

---

## 2. Java 编码规约

### 2.1. 线程安全准则 (Thread Safety)
由于 Web 服务处理器（HttpHandler）运行在专门的网络子线程中，**切记严禁在此子线程直接读取或修改任何不稳定的游戏运行时数据**（如世界方块、实体列表、生物群系放置等）。
* **静态只读数据**：读取 `BuiltInRegistries.ITEM` 是线程安全的，可以直接在 HTTP 线程中执行。
* **动态/交互数据**：获取 `RecipeManager` 中的内容，或者执行数据包热重载时，**必须**将任务推送到服务器的主线程队列中同步执行，并阻塞等待其返回：
  ```java
  // 线程安全调度示例
  public static <T> T callOnServerThread(Supplier<T> supplier) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server == null) return supplier.get();
      try {
          return server.submit(supplier::get).get(); // 阻塞子线程，等待主线程处理完毕
      } catch (Exception e) {
          throw new RuntimeException("主线程执行任务失败", e);
      }
  }
  ```

### 2.2. 异常防护 (Exception Management)
作为开发辅助 Mod，**不允许由于 Web 接口中的异常引发客户端或服务端崩溃**。
* 每一个 HttpHandler 的 `handle()` 方法都必须被巨大的 `try-catch` 块包裹。
* 一旦发生异常，应当打印 Error 日志（利用 `DataAgent.LOGGER`），并返回 HTTP `500 Internal Server Error`，同时附带详细的 JSON 错误信息。

### 2.3. 代码抽象与版本兼容 (Version Friendliness)
* 避免直接引入底层特定的 NMS (Net.Minecraft.Server) 混淆字段，多采用 `neoforge` 封装的 API。
* 使用 `ResourceLocation` 对注册表中的 ID 进行统一解析，不要直接使用字符串拼接。

---

## 3. JSON 数据规范

所有的 API 接口响应与导出的 JSON 必须符合以下规范，以便于本地 AI 智能体能够实现极简的词法解析与匹配。

### 3.1. 命名规范
* 所有 JSON 的 Key 必须使用 **`snake_case`（下划线命名法）**，如 `translation_key`、`max_uses`。
* 不要使用 `camelCase`（驼峰命名法）。

### 3.2. 完整 ID 规范
* 所有物品、方块、属性等，输出时必须携带**命名空间前缀**（Namespace）。
  * 错误示例: `"id": "dirt"`
  * 正确示例: `"id": "minecraft:dirt"` 或 `"id": "kubejs:custom_item"`

### 3.3. 分页与过滤规范
对于结果可能超过 1000 条的大型列表（如 `GET /recipes` 或 `GET /registry/item`），接口响应必须返回标准分页元数据：
```json
{
  "total": 5240,       // 总匹配数
  "limit": 50,         // 单次返回数限制
  "offset": 100,       // 偏移量
  "results": [ ... ]   // 实际数据列表
}
```

---

## 4. 导出文件标准规范 (`export-service`)

导出服务生成的快照文件默认放置在游戏根目录下的隐藏文件夹 `.pi-agent/dumps/` 中。

### 4.1. 文件组织规范
* `items.json`：全量物品 ID 及其中文/英文翻译键映射。
* `blocks.json`：全量方块 ID 及方块状态属性列表。
* `recipes.json`：全量合成配方数据。
* `villager_trades.json`：各职业村民交易表。

### 4.2. 版本标识
每次导出时，均应在导出路径下生成一个 `metadata.json`，其中记录当前的 Minecraft 版本、NeoForge 版本、已加载 Mod 列表及其版本哈希，以便 AI 判断是否需要重新读取缓存。
```json
{
  "minecraft_version": "1.21.4",
  "neoforge_version": "26.2.0",
  "timestamp": 1719888900000,
  "mod_count": 248
}
```
通过遵守这套开发规范与目录结构，团队协作和 AI 工具集成的效率将得到极大的保障。
