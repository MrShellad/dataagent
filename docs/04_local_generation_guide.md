# 04. 本地生成指南: Datapack, KubeJS & CraftTweaker

在通过 **Pi Agent Mod** 获得游戏内当前的真实注册表和运行数据之后，本地 AI 智能体的工作是生成对应的**数据包（Datapack）**、**KubeJS 脚本**或 **CraftTweaker 脚本**。

---

## 1. 资源生成环境与目录分配

根据开发需求，AI 智能体应在本地工作区生成并组织以下资源：

```
<game_root>/
├── datapacks/pi_generated_pack/        // 原版数据包 (Datapack)
│   ├── pack.mcmeta
│   └── data/pi_agent/
│       ├── recipes/                    // 原版配方 JSON
│       └── loot_tables/                // 掉落物战利品表 JSON
├── kubejs/                             // KubeJS 脚本与注册
│   ├── startup_scripts/                // 启动注册 (JavaScript)
│   └── server_scripts/                 // 运行时逻辑/配方修改 (JavaScript)
└── scripts/                            // CraftTweaker 脚本目录
    └── pi_recipes.zs                   // 配方修改 (ZenScript)
```

---

## 2. CraftTweaker 脚本生成规范 (ZenScript)

**CraftTweaker** 是另一种广泛使用的配方与机制修改工具。它使用 **ZenScript** 语言，脚本放置在 `scripts/` 文件夹下。

### 2.1. ZenScript 核心语法示例 (Minecraft 1.21.x+)

#### 配方移除与添加
```zs
// scripts/pi_recipes.zs

// 1. 移除指定合成表 (以木剑为例)
craftingTable.remove(<item:minecraft:wooden_sword>);

// 2. 添加 3x3 有序合成表 (煤炭 + 黑曜石 = 钻石)
craftingTable.addShaped("pi_agent_coal_to_diamond", <item:minecraft:diamond>, [
    [<item:minecraft:coal>, <item:minecraft:coal>, <item:minecraft:coal>],
    [<item:minecraft:coal>, <item:minecraft:obsidian>, <item:minecraft:coal>],
    [<item:minecraft:coal>, <item:minecraft:coal>, <item:minecraft:coal>]
]);

// 3. 添加无序合成 (泥土 + 沙子 = 粘土)
craftingTable.addShapeless("pi_agent_clay_dirt_sand", <item:minecraft:clay>, [
    <item:minecraft:dirt>, <item:minecraft:sand>
]);
```

#### 修改熔炼配方
```zs
// 移除原版铁矿石熔炼
furnace.remove(<item:minecraft:iron_ingot>);

// 添加新的高炉熔炼配方 (输入粗铁，输出 2 个铁锭)
blastFurnace.addRecipe("pi_agent_double_iron", <item:minecraft:iron_ingot> * 2, <item:minecraft:raw_iron>, 1.0, 100);
```

---

## 3. KubeJS 脚本与 Datapack 模板

### 3.1. KubeJS (JavaScript) 快速参考
* **修改配方 (`kubejs/server_scripts/recipes.js`)**:
  ```javascript
  ServerEvents.recipes(event => {
      event.remove({ output: 'minecraft:wooden_sword' });
      event.shapeless('minecraft:clay', ['minecraft:dirt', 'minecraft:sand']);
  });
  ```
* **注册新项目 (`kubejs/startup_scripts/register.js`)**:
  ```javascript
  StartupEvents.registry('item', event => {
      event.create('pi_agent:raw_silicon').displayName('Raw Silicon');
  });
  ```

### 3.2. Datapack (JSON) 快速参考
* **配方 JSON (`datapacks/my_pack/data/pi_agent/recipes/diamond.json`)**:
  对于原版 1.21.x 配方，直接输出指定结构的 JSON。可以使用 Mojang Codec 进行结构检验。

---

## 4. 完整的闭环自动化验证流程

基于设计原则中的 **Read First**，AI 在生成本地文件后，需进行自我验证：

1. **AI 动作**：将生成或修改后的脚本写入 `kubejs/server_scripts/`、`scripts/` 或 `datapacks/`。
2. **AI 发送重载命令**：调用 Pi Agent Mod 的 `/reload` 接口。对于 CraftTweaker，游戏在接收到 `/reload` 后也会连带重新加载脚本（或者通过执行指令 `/ct reload`）。
3. **AI 发送查询验证**：AI 再次调用 `GET /recipes?output=...`，比对返回的 JSON 结构与目标合成是否完全契合。

### 接口校验成功/失败判定基准：
* **生效验证**：查询返回中应包含带有对应命名空间的配方项（如 `pi_agent:coal_to_diamond`）。
* **冲突校验**：若旧配方仍旧存在于 `total` 列表中且不符合预期，说明移除配方的操作（`remove`）失效或命名冲突，AI 需要根据返回的其余配方 ID，回滚脚本并重新调整规则。
