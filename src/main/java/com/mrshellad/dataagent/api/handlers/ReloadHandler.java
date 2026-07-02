package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.core.ThreadScheduler;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;

public class ReloadHandler extends HttpHandlerBase {

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only POST requests are supported on this endpoint.");
            return;
        }

        // Schedule reload on server main thread asynchronously
        ThreadScheduler.run(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                // Trigger the reload
                server.reloadResources(server.getPackRepository().getSelectedIds()).exceptionally(ex -> {
                    System.err.println("Error reloading resources: " + ex.getMessage());
                    return null;
                });
            }
        });

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("message", "Reload command successfully scheduled on Minecraft server thread.");

        sendJson(exchange, 200, response);
    }
}
