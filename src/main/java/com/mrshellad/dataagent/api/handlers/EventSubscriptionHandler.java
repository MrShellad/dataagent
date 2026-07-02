package com.mrshellad.dataagent.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.service.EventSubscriptionService;

import java.util.Map;

public class EventSubscriptionHandler extends HttpHandlerBase {

    @Override
    protected boolean isPersistentConnection() {
        return true;
    }

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET requests are supported on this endpoint.");
            // Manual close since we returned early before subscribing
            exchange.close();
            return;
        }

        EventSubscriptionService.subscribe(exchange);
    }
}
