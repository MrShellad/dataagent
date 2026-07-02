package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonArray;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.service.VillagerTradesQueryService;

import java.util.Map;

public class VillagerTradesHandler extends HttpHandlerBase {

    private final VillagerTradesQueryService queryService = new VillagerTradesQueryService();

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET requests are supported on this endpoint.");
            return;
        }

        String profession = params.get("profession");
        if (profession == null || profession.isEmpty()) {
            sendError(exchange, 400, "Bad Request", "Missing required parameter: 'profession'. Example: /api/v1/villager-trades?profession=minecraft:armorer");
            return;
        }

        int level = getIntParam(params, "level", 1);
        if (level < 1 || level > 5) {
            sendError(exchange, 400, "Bad Request", "Parameter 'level' must be between 1 and 5 (inclusive).");
            return;
        }

        try {
            JsonArray response = queryService.getTrades(profession, level);
            sendJson(exchange, 200, response);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 404, "Not Found", e.getMessage());
        }
    }
}
