package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.service.ExportService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExportHandler extends HttpHandlerBase {

    private final ExportService exportService = new ExportService();

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only POST requests are supported on this endpoint.");
            return;
        }

        // Read and parse JSON request body
        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        String targetDir = params.get("targetDir");
        if (targetDir == null) targetDir = params.get("target_dir");

        String typesStr = params.get("types");
        if (typesStr == null) typesStr = params.get("export_types");
        
        List<String> exportTypes = new ArrayList<>();
        if (typesStr != null && !typesStr.isEmpty()) {
            for (String t : typesStr.split(",")) {
                exportTypes.add(t.trim());
            }
        }

        if (body != null && !body.trim().isEmpty()) {
            try {
                JsonObject json = GSON.fromJson(body, JsonObject.class);
                if (json.has("targetDir")) {
                    targetDir = json.get("targetDir").getAsString();
                } else if (json.has("target_dir")) {
                    targetDir = json.get("target_dir").getAsString();
                }
                
                if (json.has("export_types")) {
                    JsonArray arr = json.getAsJsonArray("export_types");
                    exportTypes.clear();
                    arr.forEach(element -> exportTypes.add(element.getAsString()));
                } else if (json.has("types")) {
                    JsonArray arr = json.getAsJsonArray("types");
                    exportTypes.clear();
                    arr.forEach(element -> exportTypes.add(element.getAsString()));
                }
            } catch (Exception e) {
                // Non-JSON body, fallback to query parameters
            }
        }

        if (targetDir == null || targetDir.isEmpty()) {
            targetDir = "./.pi-agent/dumps";
        }

        // Default to exporting all types if not specified
        if (exportTypes.isEmpty()) {
            exportTypes.add("item");
            exportTypes.add("block");
            exportTypes.add("recipe");
            exportTypes.add("loot_table");
            exportTypes.add("villager_trade");
            exportTypes.add("tag");
        }

        try {
            JsonObject response = exportService.exportData(targetDir, exportTypes);
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "Internal Server Error", "Export failed: " + e.getMessage());
        }
    }
}
