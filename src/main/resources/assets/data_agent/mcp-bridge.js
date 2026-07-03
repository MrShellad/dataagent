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
