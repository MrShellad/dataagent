# 02. Pi Agent Mod - 接口规范与 API 设计

本模块定义了 **Pi Agent Mod** 暴露给外部 AI 智能体的 API 规范。所有接口均遵循 **Runtime First**（运行时优先）与 **AI Friendly**（对 AI 友好）的原则，确保返回的数据代表游戏当前的最终应用状态。

网络端口默认设定为 `24482`。

---

## 1. 基础配置与通用规范

* **Base URL**: `http://localhost:24482/api/v1`
* **Content-Type**: `application/json; charset=utf-8`
* **交互协议**: HTTP REST API / MCP (Model Context Protocol) / WebSocket

---

## 2. API 接口详解

### 2.1. Registry 查询接口 (`registry-service`)
查询游戏运行时的所有注册表数据。

* **接口路径**: `GET /registry/{registry_type}`
  * `{registry_type}` 支持包括：
    * `item` (物品), `block` (方块), `fluid` (流体), `entity_type` (实体)
    * `potion` (药水效果), `enchantment` (附魔), `attribute` (属性)
    * `dimension` (维度), `biome` (生物群系), `damage_type` (伤害类型)
* **查询参数 (Query Parameters)**:
  * `namespace` (String, 可选): 过滤指定 Mod。如 `namespace=minecraft`。
  * `tag` (String, 可选): 过滤包含特定标签的项目。如 `tag=minecraft:logs`。
  * `search` (String, 可选): 关键词模糊匹配。
* **响应示例** (`GET /registry/enchantment?search=fortune`):
  ```json
  {
    "registry": "minecraft:enchantment",
    "total": 1,
    "results": [
      {
        "id": "minecraft:fortune",
        "translation_key": "enchantment.minecraft.fortune",
        "description": "Fortune",
        "max_level": 3,
        "is_curse": false
      }
    ]
  }
  ```

---

### 2.2. 配方查询接口 (`recipe-service`)
查询 `RecipeManager` 中当前的配方，包括经 KubeJS, CraftTweaker 或 Datapacks 修改/注入后的**最终重载结果**。

* **接口路径**: `GET /recipes`
* **查询参数 (Query Parameters)**:
  * `input` (String, 可选): 输入材料物品 ID（如 `minecraft:coal`）。
  * `output` (String, 可选): 输出产物物品 ID。
  * `type` (String, 可选): 配方类型（如 `minecraft:crafting_shaped`）。
  * `limit` (Int, 默认 50)
  * `offset` (Int, 默认 0)
* **响应示例** (`GET /recipes?output=minecraft:diamond`):
  ```json
  {
    "total": 1,
    "recipes": [
      {
        "id": "pi_agent:coal_to_diamond",
        "type": "minecraft:crafting_shaped",
        "ingredients": {
          "keys": {
            "C": "minecraft:coal",
            "#": "minecraft:obsidian"
          },
          "pattern": [
            "CCC",
            "C#C",
            "CCC"
          ]
        },
        "result": {
          "id": "minecraft:diamond",
          "count": 1
        }
      }
    ]
  }
  ```

---

### 2.3. Loot Table 查询接口 (`loot-service`)
查询当前激活的方块掉落、生物掉落或箱子战利品表。

* **接口路径**: `GET /loot-tables`
* **查询参数 (Query Parameters)**:
  * `id` (String, 必填): 战利品表 ID。例如 `minecraft:blocks/dirt`。
* **响应示例**:
  ```json
  {
    "type": "minecraft:block",
    "pools": [
      {
        "rolls": 1.0,
        "entries": [
          {
            "type": "minecraft:item",
            "name": "minecraft:dirt"
          }
        ]
      }
    ]
  }
  ```

---

### 2.4. Advancement 查询接口 (`advancement-service`)
查询游戏内激活的成就进度树。

* **接口路径**: `GET /advancements`
* **查询参数**:
  * `id` (String, 可选): 进度 ID。
* **响应示例**:
  ```json
  [
    {
      "id": "minecraft:story/mine_stone",
      "parent": "minecraft:story/root",
      "display": {
        "title": "Stone Age",
        "description": "Mine stone with your new pickaxe"
      },
      "criteria": {
        "get_stone": {
          "trigger": "minecraft:inventory_changed",
          "conditions": {
            "items": [{ "items": "#minecraft:stone_crafting_materials" }]
          }
        }
      }
    ]
  ]
  ```

---

### 2.5. Villager Trades 查询接口 (`registry-service` / `debug-tools`)
查询各职业村民在不同等级下的交易项目，方便 AI 重新平衡生存经济或设定自定义交易。

* **接口路径**: `GET /villager-trades`
* **查询参数 (Query Parameters)**:
  * `profession` (String, 可选): 职业 ID。例如 `minecraft:armorer`（盔甲匠）。
  * `level` (Int, 可选): 职业等级 (1-5)。
* **响应示例** (`GET /villager-trades?profession=minecraft:armorer&level=1`):
  ```json
  {
    "profession": "minecraft:armorer",
    "level": 1,
    "trades": [
      {
        "costA": { "id": "minecraft:coal", "count": 15 },
        "costB": null,
        "result": { "id": "minecraft:emerald", "count": 1 },
        "max_uses": 16,
        "xp_reward": 2,
        "price_multiplier": 0.05
      },
      {
        "costA": { "id": "minecraft:emerald", "count": 5 },
        "costB": null,
        "result": { "id": "minecraft:iron_helmet", "count": 1 },
        "max_uses": 12,
        "xp_reward": 1,
        "price_multiplier": 0.2
      }
    ]
  }
  ```

---

### 2.6. 数据一键导出接口 (`export-service`)
将当前游戏内的运行状态批量导出到工作区，便于本地进行高性能索引和校验。

* **接口路径**: `POST /export`
* **请求体 (Payload)**:
  ```json
  {
    "target_dir": "H:/VSCodeWork/MCDev/dataagent-26.2/.pi-agent/dumps",
    "export_types": ["item", "block", "recipe", "loot_table", "tag", "villager_trade"]
  }
  ```
* **响应示例**:
  ```json
  {
    "status": "success",
    "exported_files": {
      "item": ".../items.json",
      "recipe": ".../recipes.json",
      "villager_trade": ".../villager_trades.json"
    }
  }
  ```

---

### 2.7. 状态订阅与控制接口
* **SSE 事件流**: `GET /events/subscribe`
  * 订阅配方重载、玩家加入或世界状态变化事件。
* **游戏重载**: `POST /reload`
  * 触发类似原版 `/reload` 的操作。

---

## 3. MCP 工具映射

MCP 协议客户端（如 Cursor、Claude Desktop）连接后可直接调用以下工具：

| MCP Tool 名称 | 对应的 HTTP 接口 | 说明 |
| :--- | :--- | :--- |
| `mc_list_registry` | `GET /registry/{type}` | 查询方块、物品、附魔、维度等注册表内容 |
| `mc_query_recipes` | `GET /recipes` | 按输入/输出材料查询最终计算配方 |
| `mc_get_loot_table` | `GET /loot-tables` | 获取特定战利品表详情 |
| `mc_list_trades` | `GET /villager-trades` | 查询指定职业和等级村民的交易列表 |
| `mc_export_dumps` | `POST /export` | 一键打包运行时数据快照到本地 |
| `mc_reload_game` | `POST /reload` | 执行热重载以重新解析本地生成文件 |
