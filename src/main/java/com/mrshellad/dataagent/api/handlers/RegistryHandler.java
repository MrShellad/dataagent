package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.service.RegistryQueryService;

import java.util.Map;

public class RegistryHandler extends HttpHandlerBase {

    private final RegistryQueryService queryService = new RegistryQueryService();

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET requests are supported on this endpoint.");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String prefix = "/api/v1/registry";
        
        // Extract type from path (e.g. /api/v1/registry/item)
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            sendError(exchange, 400, "Bad Request", "Missing registry type in path. Example: /api/v1/registry/item");
            return;
        }

        String type = path.substring(prefix.length());
        if (type.startsWith("/")) {
            type = type.substring(1);
        }

        if (type.isEmpty()) {
            sendError(exchange, 400, "Bad Request", "Missing registry type in path. Supported types: item, block, fluid, entity_type, enchantment, biome, dimension");
            return;
        }

        String lowerType = type.toLowerCase();
        if (!lowerType.equals("item") && !lowerType.equals("block") && !lowerType.equals("fluid") &&
            !lowerType.equals("entity_type") && !lowerType.equals("enchantment") &&
            !lowerType.equals("biome") && !lowerType.equals("dimension")) {
            sendError(exchange, 404, "Not Found", "Unsupported registry type: '" + type + "'. Supported types: item, block, fluid, entity_type, enchantment, biome, dimension.");
            return;
        }

        // Parse and sanitize query parameters
        String namespace = params.get("namespace");
        String tag = params.get("tag");
        String search = params.get("search");

        // Clamp limit to 1-100 to prevent DOS / memory overflow
        int limit = getIntParam(params, "limit", 50);
        if (limit < 1) {
            limit = 50;
        } else if (limit > 100) {
            limit = 100;
        }

        // Ensure offset is non-negative
        int offset = getIntParam(params, "offset", 0);
        if (offset < 0) {
            offset = 0;
        }

        try {
            JsonObject response = queryService.queryRegistry(type, namespace, tag, search, limit, offset);
            sendJson(exchange, 200, response);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 404, "Not Found", e.getMessage());
        }
    }
}
