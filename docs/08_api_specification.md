# 08. Pi Agent HTTP API 接口规范说明书

本篇文档详细列出了 **Pi Agent Mod** 导出的 HTTP API 接口设计。这些接口由内置的高性能 `HttpServer` 服务器托管，默认运行在端口 `24482`，并支持利用 Java 25 虚拟线程进行非阻塞高并发处理。

---

## 1. 基础信息与配置项

* **配置菜单**: 
  * 客户端内可通过选择 **Mods** 列表 -> 点击本 Mod (**Data Agent**) -> 点击 **Config** 按钮，直接打开可视化的参数配置页面。
* **物理配置文件**:
  * 对应磁盘上的路径为：本地游戏目录下的 `config/data_agent-common.toml`。
* **可配置参数**:
  * `enableApi` (Boolean): 是否开启 HTTP API 服务器访问。默认值为 `true`。若设为 `false`，则不会启动 API 监听；如果在游戏运行中将其关闭，服务器将自动下线。
  * `apiPort` (Int): API 服务器监听的端口。默认值为 `24482`，有效取值范围为 `1024` 至 `65535`。如果在运行中修改此端口，HTTP 服务器将自动在新的端口上重启。
* **响应格式**: `application/json; charset=utf-8`
* **异常返回**: 出错时统一返回标准的错误 JSON 结构，包含 `error`、`status` 及具体的 `message` 信息。

---

## 2. 接口列表

### 2.1. 查询 Mod 运行状态 (`/api/v1/status`)
* **方法**: `GET`
* **说明**: 查询当前 Agent 服务的在线状态、NeoForge 版本以及当前服务器加载的所有 Mod 列表及版本号。
* **参数**: 无
* **响应示例**:
```json
{
  "status": "ok",
  "message": "Pi Agent API is online.",
  "neoforge_version": "26.2",
  "loaded_mods": [
    {
      "mod_id": "minecraft",
      "display_name": "Minecraft",
      "version": "26.2"
    },
    {
      "mod_id": "data_agent",
      "display_name": "Data Agent",
      "version": "1.0.0"
    }
  ],
  "total_mods": 2
}
```

---

### 2.2. 触发资源重载 (`/api/v1/reload`)
* **方法**: `POST`
* **说明**: 异步通知 Minecraft 服务器主线程重新加载数据包、配方、战利品表和标签（等同于在控制台执行 `/reload`）。
* **参数**: 无
* **响应示例**:
```json
{
  "status": "success",
  "message": "Reload command successfully scheduled on Minecraft server thread."
}
```

---

### 2.3. 查询游戏注册表数据 (`/api/v1/registry/<type>`)
* **方法**: `GET`
* **说明**: 分页、筛选并查询 Minecraft 各种内置及动态数据注册表信息。
* **支持类型 (`<type>`)**:
  * `item` (物品)
  * `block` (方块)
  * `fluid` (流体)
  * `entity_type` (实体类型)
  * `enchantment` (魔咒)
  * `biome` (生物群系)
  * `dimension` (维度类型)
* **查询参数**:
  * `namespace` (String): 空间筛选，如 `minecraft` 或 `data_agent`。
  * `tag` (String): 按注册表标签筛选，例如魔咒标签或物品标签。
  * `search` (String): 模糊搜索匹配物品或方块的 ID 路径。
  * `limit` (Int): 限制返回条数，默认为 `50`，最大限制 `100`。
  * `offset` (Int): 分页偏移量，默认为 `0`。
* **响应示例**:
如果查询类型为 `item`（物品），接口还会富集返回物品的堆叠、耐久、稀有度，以及其在 Mojang 底层注册的所有组件（`components`）和扁平化的插槽属性（`attributes`）：
`/api/v1/registry/item?search=sword&limit=1`
```json
{
  "total": 5,
  "limit": 1,
  "offset": 0,
  "results": [
    {
      "id": "minecraft:iron_sword",
      "translation_key": "item.minecraft.iron_sword",
      "tags": [
        "minecraft:swords",
        "minecraft:sharp_weapon"
      ],
      "max_stack_size": 1,
      "max_damage": 250,
      "is_damageable": true,
      "rarity": "COMMON",
      "components": {
        "minecraft:max_damage": 250,
        "minecraft:max_stack_size": 1,
        "minecraft:attribute_modifiers": {
          "modifiers": [
            {
              "type": "minecraft:generic.attack_damage",
              "amount": 6.0,
              "operation": "add_value"
            }
          ]
        }
      },
      "attributes": {
        "mainhand": [
          {
            "attribute": "minecraft:generic.attack_damage",
            "name": "Weapon modifier",
            "amount": 6.0,
            "operation": "ADD_VALUE"
          }
        ]
      }
    }
  ]
}
```

如果查询类型为 `block`（方块），接口会富集返回该方块的挖掘硬度、抗暴、光照等属性：
`/api/v1/registry/block?search=obsidian&limit=1`
```json
{
  "total": 1,
  "limit": 1,
  "offset": 0,
  "results": [
    {
      "id": "minecraft:obsidian",
      "translation_key": "block.minecraft.obsidian",
      "tags": [
        "minecraft:mineable/pickaxe",
        "minecraft:needs_diamond_tool"
      ],
      "hardness": 50.0,
      "requires_tool": true,
      "light_emission": 0
    }
  ]
}
```

---

### 2.4. 查询合成配方 (`/api/v1/recipes`)
* **方法**: `GET`
* **说明**: 查询当前服务器已加载的所有合成配方。
* **查询参数**:
  * `inputItem` (String): 配方的输入材料（支持物品 ID 匹配或材料标签匹配）。
  * `outputItem` (String): 配方的输出物品 ID。
  * `typeFilter` (String): 过滤配方类型（例如 `minecraft:crafting_shaped` 或 `minecraft:smelting`）。
  * `limit` (Int): 返回的最大数量，默认 `50`，上限 `100`。
  * `offset` (Int): 分页偏移量，默认为 `0`。
* **响应示例**:
```json
{
  "total": 1585,
  "limit": 1,
  "offset": 0,
  "recipes": [
    {
      "id": "minecraft:stone_axe",
      "type": "minecraft:crafting_shaped",
      "detail": {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "key": {
          "#": {
            "item": "minecraft:stick"
          },
          "X": {
            "tag": "minecraft:stone_tool_materials"
          }
        },
        "pattern": [
          "XX",
          "X#",
          " #"
        ],
        "result": {
          "count": 1,
          "id": "minecraft:stone_axe"
        },
        "show_notification": true
      }
    }
  ]
}
```

---

### 2.5. 查询战利品表 (`/api/v1/loot-tables`)
* **方法**: `GET`
* **说明**: 通过战利品表 ID 获取其底层的详细 JSON 配置结构。数据通过 Mojang 的 Direct Codec 系统直接序列化。
* **查询参数**:
  * `id` (String, 必填): 战利品表的 Resource Location，例如 `minecraft:chests/simple_dungeon`。
* **响应示例**: `/api/v1/loot-tables?id=minecraft:blocks/coal_ore`
```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "bonus_rolls": 0.0,
      "entries": [
        {
          "type": "minecraft:alternatives",
          "children": [
            {
              "type": "minecraft:item",
              "conditions": [
                {
                  "condition": "minecraft:match_tool",
                  "predicate": {
                    "predicates": {
                      "minecraft:enchantments": [
                        {
                          "enchantment": "minecraft:silk_touch",
                          "levels": {
                            "min": 1
                          }
                        }
                      ]
                    }
                  }
                }
              ],
              "name": "minecraft:coal_ore"
            },
            {
              "type": "minecraft:item",
              "functions": [
                {
                  "count": {
                    "type": "minecraft:ore_drops"
                  },
                  "enchantment": "minecraft:fortune",
                  "function": "minecraft:apply_bonus"
                },
                {
                  "function": "minecraft:explosion_decay"
                }
              ],
              "name": "minecraft:coal"
            }
          ]
        }
      ],
      "rolls": 1.0
    }
  ]
}
```

---

### 2.6. 查询进度/成就数据 (`/api/v1/advancements`)
* **方法**: `GET`
* **说明**: 查询游戏内的进度配置及其达成条件（Criteria）。
* **查询参数**:
  * `idFilter` (String, 可选): 指定需要查询的进度 ID（如 `minecraft:story/mine_stone`）。
* **响应示例**:
```json
[
  {
    "id": "minecraft:story/mine_stone",
    "parent": "minecraft:story/root",
    "display": {
      "title": "石器时代",
      "description": "用你的新镐子开采石头"
    },
    "criteria": {
      "get_stone": {
        "trigger": "minecraft:inventory_changed"
      }
    }
  }
]
```

---

### 2.7. 动态村民交易表查询 (`/api/v1/villager-trades`)
* **方法**: `GET`
* **说明**: 查询特定村民职业在特定等级（1~5级）下的所有可能交易组合。在 Minecraft 1.21.4 中，此数据由服务器底层的 `TRADE_SET` 动态注册表驱动。
* **查询参数**:
  * `profession` (String, 必填): 村民的职业，例如 `minecraft:armorer`、`minecraft:farmer`、`minecraft:librarian` 等。
  * `level` (Int, 可选): 村民的职业等级（1 到 5），默认值为 `1`。
* **响应示例**: `/api/v1/villager-trades?profession=minecraft:armorer&level=1`
```json
[
  {
    "wants": {
      "item": "minecraft:coal",
      "count": 15
    },
    "gives": {
      "item": "minecraft:emerald",
      "count": 1
    },
    "max_uses": "16",
    "xp": "2",
    "reputation_discount": "0"
  },
  {
    "wants": {
      "item": "minecraft:emerald",
      "count": 4
    },
    "gives": {
      "item": "minecraft:iron_shield",
      "count": 1
    },
    "max_uses": "12",
    "xp": "1",
    "reputation_discount": "0"
  }
]
```

---

### 2.8. 批量导出静态 JSON 副本 (`/api/v1/export`)
* **方法**: `POST`
* **说明**: 批量将注册表、合成表和战利品表序列化并持久化导出到本地磁盘。
* **请求体或查询参数**:
  * `targetDir` (String, 必填): 导出的本地目标绝对路径，例如 `C:/exported_data`。
  * `types` (String, 必填): 用逗号分隔的导出数据类型（例如 `item,block,recipe,loot_table`）。
* **响应示例**:
```json
{
  "status": "success",
  "message": "Bulk data successfully exported to disk.",
  "exported_files": {
    "item": "C:\\exported_data\\items.json",
    "block": "C:\\exported_data\\blocks.json",
    "recipe": "C:\\exported_data\\recipes.json",
    "loot_table": "C:\\exported_data\\loot_tables.json"
  }
}
```

---

### 2.9. 实时游戏事件流订阅 (`/api/v1/events/subscribe`)
* **方法**: `GET`
* **协议**: Server-Sent Events (SSE)
* **说明**: 订阅一个持久的实时数据推送通道，支持推送游戏中触发的瞬时事件（如玩家连入、玩家断开连接、命令触发等）。连接每 15 秒发送一个 Keep-Alive Ping。
* **推送事件类型**:
  * `connected`: 订阅建立成功的欢迎事件。
  * 其他自定义游戏内事件（当事件触发时推送）。
* **响应流结构**:
```text
event: connected
data: {"status":"ok"}

event: player_join
data: {"player":"Mrshellad","uuid":"dd12be42-52a9-4a91-a8a1-11c01849e498"}

: ping
```

---

### 2.10. 查询实体基础信息及默认属性 (`/api/v1/entity-types`)
* **方法**: `GET`
* **说明**: 分页、筛选并查询游戏内所有实体类型（EntityType）的基础元数据、尺寸大小、免疫属性、关联的默认战利品表，以及该实体类型所注册的全部默认属性（Attributes，如最大生命值、移动速度、攻击伤害、随从范围等）。
* **查询参数**:
  * `namespace` (String, 可选): 按实体命名空间筛选（如 `minecraft`）。
  * `category` (String, 可选): 按生物分类筛选，常见分类包括 `monster` (怪物), `creature` (友好生物), `ambient` (环境生物), `water_creature` (水生生物), `misc` (杂项) 等。
  * `search` (String, 可选): 模糊匹配实体 ID 的路径（例如 `zombie`）。
  * `limit` (Int): 限制返回条数，默认为 `50`，最大限制 `100`。
  * `offset` (Int): 分页偏移量，默认为 `0`。
* **响应示例**: `/api/v1/entity-types?search=zombie&limit=1`
```json
{
  "total": 5,
  "limit": 1,
  "offset": 0,
  "results": [
    {
      "id": "minecraft:zombie",
      "translation_key": "entity.minecraft.zombie",
      "category": "monster",
      "summonable": true,
      "fire_immune": false,
      "width": 0.6,
      "height": 1.95,
      "loot_table": "minecraft:entities/zombie",
      "attributes": {
        "minecraft:generic.max_health": 20.0,
        "minecraft:generic.knockback_resistance": 0.0,
        "minecraft:generic.movement_speed": 0.23000000417232513,
        "minecraft:generic.attack_damage": 3.0,
        "minecraft:generic.follow_range": 35.0,
        "minecraft:generic.zombie_spawn_reinforcements": 0.0
      }
    }
  ]
}
```

---

### 2.11. 特权指令执行接口 (`/api/v1/command`)
* **方法**: `POST`
* **说明**: 在 Minecraft 服务器线程上以最高管理员级别（OP Level 4）运行任意指令，并捕获指令执行产生的全部系统反馈文本。该接口仅在整合包制作阶段的开发环境使用，无安全过滤。
* **请求体 (JSON)**:
  * `command` (String, 必填): 要执行的游戏内指令，可带或不带开头的 `/`。
* **请求示例**: `POST /api/v1/command`
```json
{
  "command": "/locate structure minecraft:village_plains"
}
```
* **响应示例**:
```json
{
  "status": "success",
  "output": "最靠近的 minecraft:village_plains 在 [-120, ~, 240] (300 方块远)"
}
```

---

### 2.12. 开发配置文件与脚本管理接口 (`/api/v1/files/*`)
* **说明**: 读写游戏文件系统（如 `config/` 或 `kubejs/` 中的文件和脚本）。本接口仅供整合包制作环境下的 Agent 部署和更新配置文件，无路径或权限安全隔离。

#### 2.12.1. 列出目录内容 (`/api/v1/files/list`)
* **方法**: `GET`
* **参数**:
  * `dir` (String, 可选): 目标目录路径（相对于游戏根目录，默认为 `.`）。
* **响应示例**: `GET /api/v1/files/list?dir=config`
```json
{
  "directory": "config",
  "files": [
    {
      "name": "data_agent-common.toml",
      "path": "config/data_agent-common.toml",
      "is_directory": false,
      "size": 1553
    }
  ]
}
```

#### 2.12.2. 读取文件内容 (`/api/v1/files/read`)
* **方法**: `GET`
* **参数**:
  * `path` (String, 必填): 文件相对或绝对路径。
* **响应示例**: `GET /api/v1/files/read?path=config/data_agent-common.toml`
```json
{
  "path": "config/data_agent-common.toml",
  "content": "# Port for the Pi Agent HTTP/MCP server\napiPort = 24482\n..."
}
```

#### 2.12.3. 写入/覆写文件内容 (`/api/v1/files/write`)
* **方法**: `POST`
* **请求体 (JSON)**:
  * `path` (String, 必填): 文件写入路径（若父目录不存在会自动创建）。
  * `content` (String, 必填): 写入的文件原始内容。
* **响应示例**: `POST /api/v1/files/write`
```json
{
  "status": "success",
  "message": "File written successfully.",
  "path": "config/data_agent-common.toml"
}
```

---

### 2.13. 游戏日志实时获取接口 (`/api/v1/logs`)
* **方法**: `GET`
* **说明**: 获取当前游戏运行时产生的控制台日志（读取 `logs/latest.log`），方便 Agent 进行配方或配置异常分析与自愈。
* **参数**:
  * `lines` (Int, 可选): 返回的最新日志行数，默认为 `100`，最大允许 `1000`。
* **响应示例**: `GET /api/v1/logs?lines=3`
```json
{
  "total_lines": 3,
  "lines": [
    "[01:10:20] [Server thread/INFO] [neoforge]: Reloading ResourceManager...",
    "[01:10:21] [Server thread/INFO] [PiAgent]: DataAgent HTTP server is reloading...",
    "[01:10:22] [Server thread/INFO] [KubeJS]: Reload completed successfully."
  ]
}
```

---

### 2.14. 查询玩家状态及背包数据 (`/api/v1/player`)
* **方法**: `GET`
* **说明**: 查询指定玩家（在测试环境下若未指定玩家，默认取当前在线的第一位玩家）所处的游戏结构（如村庄、要塞等）、当前世界坐标、所处维度、角色背包物品和末影箱数据。
* **参数**:
  * `name` (String, 可选): 指定查询的玩家游戏角色名。
* **响应示例**: `GET /api/v1/player`
```json
{
  "username": "PlayerName",
  "uuid": "43e25b16-538a-4932-a589-cf913506c9a7",
  "dimension": "minecraft:overworld",
  "position": {
    "x": -12.5,
    "y": 64.0,
    "z": 256.3
  },
  "structures": [
    "minecraft:village_plains"
  ],
  "inventory": [
    {
      "slot": 0,
      "id": "minecraft:iron_sword",
      "count": 1,
      "detail": {
        "id": "minecraft:iron_sword",
        "count": 1,
        "components": {
          "minecraft:damage": 0
        }
      }
    }
  ],
  "ender_chest": [
    {
      "slot": 0,
      "id": "minecraft:diamond",
      "count": 64,
      "detail": {
        "id": "minecraft:diamond",
        "count": 64
      }
    }
  ]
}
```


