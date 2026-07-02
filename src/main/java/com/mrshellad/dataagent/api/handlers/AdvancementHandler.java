package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonArray;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.service.AdvancementQueryService;

import java.util.Map;

public class AdvancementHandler extends HttpHandlerBase {

    private final AdvancementQueryService queryService = new AdvancementQueryService();

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET requests are supported on this endpoint.");
            return;
        }

        String id = params.get("idFilter");
        if (id == null) id = params.get("id");
        JsonArray response = queryService.getAdvancements(id);
        sendJson(exchange, 200, response);
    }
}
