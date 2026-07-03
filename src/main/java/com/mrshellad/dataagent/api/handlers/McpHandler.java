package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.core.ThreadScheduler;
import com.mrshellad.dataagent.service.ExportService;
import com.mrshellad.dataagent.service.RecipeQueryService;
import com.mrshellad.dataagent.service.RegistryQueryService;
import com.mrshellad.dataagent.service.VillagerTradesQueryService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class McpHandler extends HttpHandlerBase {

    private static final JsonArray TOOLS_LIST;

    static {
        String json = "[" +
                "  {" +
                "    \"name\": \"mc_list_registry\"," +
                "    \"description\": \"查询游戏内注册表数据（支持 item, block, fluid, enchantment, biome, dimension, damage_type, potion）\"," +
                "    \"inputSchema\": {" +
                "      \"type\": \"object\"," +
                "      \"properties\": {" +
                "        \"type\": {\"type\": \"string\", \"description\": \"注册表类型，例如 item, block, enchantment\"}," +
                "        \"namespace\": {\"type\": \"string\", \"description\": \"命名空间，例如 minecraft, kubejs\"}," +
                "        \"search\": {\"type\": \"string\", \"description\": \"模糊匹配关键词\"}" +
                "      }," +
                "      \"required\": [\"type\"]" +
                "    }" +
                "  }," +
                "  {" +
                "    \"name\": \"mc_query_recipes\"," +
                "    \"description\": \"查询当前所有的已加载配方（包含 Mod、KubeJS、CraftTweaker 动态应用后的最终配方）\"," +
                "    \"inputSchema\": {" +
                "      \"type\": \"object\"," +
                "      \"properties\": {" +
                "        \"input\": {\"type\": \"string\", \"description\": \"输入材料物品 ID\"}," +
                "        \"output\": {\"type\": \"string\", \"description\": \"合成产物物品 ID\"}" +
                "      }" +
                "    }" +
                "  }," +
                "  {" +
                "    \"name\": \"mc_list_trades\"," +
                "    \"description\": \"获取特定职业和等级村民的交易列表\"," +
                "    \"inputSchema\": {" +
                "      \"type\": \"object\"," +
                "      \"properties\": {" +
                "        \"profession\": {\"type\": \"string\", \"description\": \"村民职业 ID，例如 minecraft:armorer\"}," +
                "        \"level\": {\"type\": \"integer\", \"description\": \"职业等级 1-5\"}" +
                "      }" +
                "    }" +
                "  }," +
                "  {" +
                "    \"name\": \"mc_export_dumps\"," +
                "    \"description\": \"一键导出当前所有游戏运行时数据快照到本地进行静态分析\"," +
                "    \"inputSchema\": {" +
                "      \"type\": \"object\"," +
                "      \"properties\": {" +
                "        \"target_dir\": {\"type\": \"string\", \"description\": \"导出路径\"}" +
                "      }" +
                "    }" +
                "  }," +
                "  {" +
                "    \"name\": \"mc_reload_game\"," +
                "    \"description\": \"触发游戏数据热重载（类似执行 /reload），使本地新生成的 Datapack/KubeJS/CraftTweaker 生效\"," +
                "    \"inputSchema\": {\"type\": \"object\", \"properties\": {}}" +
                "  }" +
                "]";
        TOOLS_LIST = GSON.fromJson(json, JsonArray.class);
    }

    private boolean isSseConnection = false;

    @Override
    protected boolean isPersistentConnection() {
        return isSseConnection;
    }

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // 1. Handle GET /mcp (SSE setup)
        if ("GET".equalsIgnoreCase(method) && path.endsWith("/mcp")) {
            isSseConnection = true;
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            // Set CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(200, 0); // Chunked output

            try (OutputStream os = exchange.getResponseBody()) {
                // Send the endpoint event informing client where to send POST calls
                os.write("event: endpoint\n".getBytes(StandardCharsets.UTF_8));
                os.write("data: /api/v1/mcp\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();

                // Keep SSE connection alive with a heartbeat comment
                while (true) {
                    Thread.sleep(15000);
                    os.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            } catch (Exception e) {
                // Connection closed or thread interrupted, exit quietly
            } finally {
                exchange.close();
            }
            return;
        }

        // 2. Handle POST /api/v1/mcp (JSON-RPC)
        if ("POST".equalsIgnoreCase(method) && path.endsWith("/api/v1/mcp")) {
            isSseConnection = false;
            JsonObject requestJson;
            try (var reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
                requestJson = JsonParser.parseReader(reader).getAsJsonObject();
            }

            String jsonrpc = requestJson.has("jsonrpc") ? requestJson.get("jsonrpc").getAsString() : "2.0";
            String rpcMethod = requestJson.get("method").getAsString();
            JsonElement rpcId = requestJson.get("id");

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("jsonrpc", jsonrpc);
            responseJson.add("id", rpcId);

            if ("tools/list".equals(rpcMethod)) {
                JsonObject result = new JsonObject();
                result.add("tools", TOOLS_LIST);
                responseJson.add("result", result);
                sendJson(exchange, 200, responseJson);
                return;
            }

            if ("tools/call".equals(rpcMethod)) {
                JsonObject rpcParams = requestJson.getAsJsonObject("params");
                String toolName = rpcParams.get("name").getAsString();
                JsonObject toolArgs = rpcParams.has("arguments") ? rpcParams.getAsJsonObject("arguments") : new JsonObject();

                try {
                    JsonElement toolResult = executeTool(toolName, toolArgs);
                    JsonObject result = new JsonObject();
                    JsonArray contentArray = new JsonArray();
                    JsonObject contentObj = new JsonObject();
                    contentObj.addProperty("type", "text");
                    contentObj.addProperty("text", GSON.toJson(toolResult));
                    contentArray.add(contentObj);
                    result.add("content", contentArray);

                    responseJson.add("result", result);
                    sendJson(exchange, 200, responseJson);
                } catch (IllegalArgumentException e) {
                    JsonObject error = new JsonObject();
                    error.addProperty("code", -32602);
                    error.addProperty("message", e.getMessage());
                    responseJson.add("error", error);
                    sendJson(exchange, 200, responseJson);
                } catch (Exception e) {
                    JsonObject error = new JsonObject();
                    error.addProperty("code", -32603);
                    error.addProperty("message", "Internal error: " + e.getMessage());
                    responseJson.add("error", error);
                    sendJson(exchange, 200, responseJson);
                }
                return;
            }

            // Unknown method
            JsonObject error = new JsonObject();
            error.addProperty("code", -32601);
            error.addProperty("message", "Method not found");
            responseJson.add("error", error);
            sendJson(exchange, 200, responseJson);
            return;
        }

        // Method not allowed
        sendError(exchange, 405, "Method Not Allowed", "Method not supported on this path.");
    }

    private JsonElement executeTool(String name, JsonObject args) throws Exception {
        switch (name) {
            case "mc_list_registry": {
                String type = args.get("type").getAsString();
                String namespace = args.has("namespace") ? args.get("namespace").getAsString() : null;
                String search = args.has("search") ? args.get("search").getAsString() : null;
                RegistryQueryService service = new RegistryQueryService();
                return service.queryRegistry(type, namespace, null, search, 100, 0);
            }
            case "mc_query_recipes": {
                String input = args.has("input") ? args.get("input").getAsString() : null;
                String output = args.has("output") ? args.get("output").getAsString() : null;
                RecipeQueryService service = new RecipeQueryService();
                return service.queryRecipes(input, output, null, 100, 0);
            }
            case "mc_list_trades": {
                String profession = args.has("profession") ? args.get("profession").getAsString() : null;
                int level = args.has("level") ? args.get("level").getAsInt() : 1;
                VillagerTradesQueryService service = new VillagerTradesQueryService();
                return service.getTrades(profession, level);
            }
            case "mc_export_dumps": {
                String targetDir = args.has("target_dir") ? args.get("target_dir").getAsString() : "./.pi-agent/dumps";
                ExportService service = new ExportService();
                return service.exportData(targetDir, List.of("item", "block", "recipe", "loot_table", "villager_trade", "tag"));
            }
            case "mc_reload_game": {
                ThreadScheduler.run(() -> {
                    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        server.reloadResources(server.getPackRepository().getSelectedIds()).exceptionally(ex -> {
                            System.err.println("Error reloading resources: " + ex.getMessage());
                            return null;
                        });
                    }
                });
                JsonObject result = new JsonObject();
                result.addProperty("status", "success");
                result.addProperty("message", "Reload command successfully scheduled on Minecraft server thread.");
                return result;
            }
            default:
                throw new IllegalArgumentException("Unknown tool: " + name);
        }
    }
}
