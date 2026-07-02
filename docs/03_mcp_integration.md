# 03. MCP (Model Context Protocol) 接入指南

**Model Context Protocol (MCP)** 是由 Anthropic 提出的一项开放协议，旨在标准化 AI 客户端（如 Claude Desktop、Cursor、Roo Code 等）与本地开发工具、数据源或服务之间的通信。

通过 MCP，我们可以让 AI 自动发现并调用 Pi Agent Mod 提供的诸多只读查询与验证工具。

---

## 1. 接入架构设计

由于 Minecraft 作为一个独立进程运行，AI 智能体通常使用 **Stdio (标准输入输出)** 来对接本地的 MCP 服务。我们可以部署一个极简的本地桥接脚本，将 Stdio 中的 JSON-RPC 消息转换成向 Pi Agent Mod 的网络调用。

```
┌─────────────────┐             ┌─────────────────┐             ┌─────────────────┐
│     Cursor      ├────────────►│  Pi Agent API   ├────────────►│    Minecraft    │
│ (HTTP/SSE Mode) │             │  (Port 24482)   │             │   (NeoForge)    │
└─────────────────┘             └─────────────────┘             └─────────────────┘
                                         ▲
┌─────────────────┐   Stdio     ┌────────┴────────┐             
│ Claude Desktop  ├────────────►│   mcp-bridge    │             
│  (Stdio Mode)   │             │ (Node.js/Python)│             
└─────────────────┘             └─────────────────┘             
```

---

## 2. 客户端配置指南

### 2.1. Cursor 接入配置
Cursor 支持直接通过 HTTP SSE 形式连接 MCP 服务。

1. 打开 Cursor，进入 `Settings` -> `Features` -> `MCP`。
2. 点击 `+ Add New MCP Server`：
   * **Name**: `PiAgent`
   * **Type**: `SSE`
   * **URL**: `http://localhost:24482/mcp`

### 2.2. Claude Desktop / Roo Code 配置
对于 Stdio 接口模式，修改配置文件（如 `%APPDATA%\Claude\claude_desktop_config.json` 或 Roo Code 中的 MCP 设置）：
```json
{
  "mcpServers": {
    "pi-agent-bridge": {
      "command": "node",
      "args": [
        "H:/VSCodeWork/MCDev/dataagent-26.2/.pi-agent/mcp-bridge.js"
      ]
    }
  }
}
```

---

## 3. Node.js 极简 Stdio 桥接器实现 (`mcp-bridge.js`)

在项目根目录下放置该桥接器，处理 AI 智能体的 Stdio 消息。

```javascript
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import fetch from "node-fetch";

const MC_API_URL = "http://localhost:24482/api/v1";

const server = new Server(
  {
    name: "pi-agent-bridge",
    version: "1.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// 1. 声明 Pi Agent 暴露给 AI 的 Tools
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: "mc_list_registry",
        description: "查询游戏内注册表数据（支持 item, block, fluid, enchantment, biome, dimension, damage_type, potion）",
        inputSchema: {
          type: "object",
          properties: {
            type: { type: "string", description: "注册表类型，例如 item, block, enchantment" },
            namespace: { type: "string", description: "命名空间，例如 minecraft, kubejs" },
            search: { type: "string", description: "模糊匹配关键词" }
          },
          required: ["type"]
        }
      },
      {
        name: "mc_query_recipes",
        description: "查询当前所有的已加载配方（包含 Mod、KubeJS、CraftTweaker 动态应用后的最终配方）",
        inputSchema: {
          type: "object",
          properties: {
            input: { type: "string", description: "输入材料物品 ID" },
            output: { type: "string", description: "合成产物物品 ID" }
          }
        }
      },
      {
        name: "mc_list_trades",
        description: "获取特定职业和等级村民的交易列表",
        inputSchema: {
          type: "object",
          properties: {
            profession: { type: "string", description: "村民职业 ID，例如 minecraft:armorer" },
            level: { type: "integer", description: "职业等级 1-5" }
          }
        }
      },
      {
        name: "mc_export_dumps",
        description: "一键导出当前所有游戏运行时数据快照到本地进行静态分析",
        inputSchema: {
          type: "object",
          properties: {
            target_dir: { type: "string", description: "导出路径" }
          }
        }
      },
      {
        name: "mc_reload_game",
        description: "触发游戏数据热重载（类似执行 /reload），使本地新生成的 Datapack/KubeJS/CraftTweaker 生效",
        inputSchema: { type: "object", properties: {} }
      }
    ],
  };
});

// 2. 将 Stdio 工具调用转发为 API 服务端调用
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;
  
  try {
    if (name === "mc_list_registry") {
      const type = args.type;
      const params = { ...args };
      delete params.type;
      const qs = new URLSearchParams(params).toString();
      const res = await fetch(`${MC_API_URL}/registry/${type}?${qs}`);
      const json = await res.json();
      return { content: [{ type: "text", text: JSON.stringify(json, null, 2) }] };
    }
    
    if (name === "mc_query_recipes") {
      const qs = new URLSearchParams(args).toString();
      const res = await fetch(`${MC_API_URL}/recipes?${qs}`);
      const json = await res.json();
      return { content: [{ type: "text", text: JSON.stringify(json, null, 2) }] };
    }

    if (name === "mc_list_trades") {
      const qs = new URLSearchParams(args).toString();
      const res = await fetch(`${MC_API_URL}/villager-trades?${qs}`);
      const json = await res.json();
      return { content: [{ type: "text", text: JSON.stringify(json, null, 2) }] };
    }

    if (name === "mc_export_dumps") {
      const res = await fetch(`${MC_API_URL}/export`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(args)
      });
      const json = await res.json();
      return { content: [{ type: "text", text: JSON.stringify(json, null, 2) }] };
    }

    if (name === "mc_reload_game") {
      const res = await fetch(`${MC_API_URL}/reload`, { method: "POST" });
      const json = await res.json();
      return { content: [{ type: "text", text: JSON.stringify(json, null, 2) }] };
    }

    throw new Error(`未知的工具: ${name}`);
  } catch (error) {
    return {
      isError: true,
      content: [{ type: "text", text: `调用 Mod 接口失败: ${error.message}` }]
    };
  }
});

// 3. 运行传输通道
const transport = new StdioServerTransport();
await server.connect(transport);
console.error("Pi Agent MCP Stdio Bridge 启动成功！");
```
