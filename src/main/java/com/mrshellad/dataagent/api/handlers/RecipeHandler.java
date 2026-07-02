package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.service.RecipeQueryService;

import java.util.Map;

public class RecipeHandler extends HttpHandlerBase {

    private final RecipeQueryService queryService = new RecipeQueryService();

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET requests are supported on this endpoint.");
            return;
        }

        String inputItem = params.get("inputItem");
        if (inputItem == null) inputItem = params.get("input");

        String outputItem = params.get("outputItem");
        if (outputItem == null) outputItem = params.get("output");

        String typeFilter = params.get("typeFilter");
        if (typeFilter == null) typeFilter = params.get("type");

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

        JsonObject response = queryService.queryRecipes(inputItem, outputItem, typeFilter, limit, offset);
        sendJson(exchange, 200, response);
    }
}
