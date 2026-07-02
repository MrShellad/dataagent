package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.service.EntityQueryService;

import java.util.Map;

public class EntityTypeHandler extends HttpHandlerBase {

    private final EntityQueryService queryService = new EntityQueryService();

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET requests are supported on this endpoint.");
            return;
        }

        String namespace = params.get("namespace");
        String category = params.get("category");
        String search = params.get("search");

        // Clamp limit to 1-100
        int limit = getIntParam(params, "limit", 50);
        if (limit < 1) {
            limit = 50;
        } else if (limit > 100) {
            limit = 100;
        }

        // Clamp offset to non-negative
        int offset = getIntParam(params, "offset", 0);
        if (offset < 0) {
            offset = 0;
        }

        JsonObject response = queryService.queryEntityTypes(namespace, category, search, limit, offset);
        sendJson(exchange, 200, response);
    }
}
