package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import net.neoforged.fml.ModList;
import java.util.Map;

public class StatusHandler extends HttpHandlerBase {

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET requests are supported on this endpoint.");
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("message", "Pi Agent API is online.");
        response.addProperty("neoforge_version", "26.2"); // NeoForge version

        // Populate loaded mods
        JsonArray modsArray = new JsonArray();
        ModList.get().getMods().forEach(mod -> {
            JsonObject modJson = new JsonObject();
            modJson.addProperty("mod_id", mod.getModId());
            modJson.addProperty("display_name", mod.getDisplayName());
            modJson.addProperty("version", mod.getVersion().toString());
            modsArray.add(modJson);
        });

        response.add("loaded_mods", modsArray);
        response.addProperty("total_mods", modsArray.size());

        sendJson(exchange, 200, response);
    }
}
