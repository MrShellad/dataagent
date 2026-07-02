package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.service.LootQueryService;

import java.util.Map;

public class LootTableHandler extends HttpHandlerBase {

    private final LootQueryService queryService = new LootQueryService();

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET requests are supported on this endpoint.");
            return;
        }

        String id = params.get("id");
        if (id == null || id.isEmpty()) {
            sendError(exchange, 400, "Bad Request", "Missing required parameter: 'id' (loot table ID). Example: /api/v1/loot-tables?id=minecraft:blocks/dirt");
            return;
        }

        try {
            JsonElement response = queryService.getLootTable(id);
            sendJson(exchange, 200, response);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 404, "Not Found", e.getMessage());
        }
    }
}
