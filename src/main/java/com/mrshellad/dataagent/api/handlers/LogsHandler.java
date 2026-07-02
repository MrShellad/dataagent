package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LogsHandler extends HttpHandlerBase {

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET is supported on this endpoint.");
            return;
        }

        int linesToRead = getIntParam(params, "lines", 100);
        if (linesToRead < 1) {
            linesToRead = 100;
        } else if (linesToRead > 1000) {
            linesToRead = 1000;
        }

        Path logPath = Paths.get("logs/latest.log").toAbsolutePath().normalize();
        if (!Files.exists(logPath)) {
            // Check in standard relative path
            logPath = Paths.get(".").toAbsolutePath().resolve("logs/latest.log").normalize();
        }

        if (!Files.exists(logPath)) {
            sendError(exchange, 404, "Not Found", "Log file logs/latest.log not found.");
            return;
        }

        List<String> lines = new ArrayList<>();
        try (var reader = Files.newBufferedReader(logPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > linesToRead) {
                    lines.remove(0);
                }
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Internal Server Error", "Failed to read logs: " + e.getMessage());
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("total_lines", lines.size());
        
        JsonArray linesArray = new JsonArray();
        for (String l : lines) {
            linesArray.add(l);
        }
        response.add("lines", linesArray);

        sendJson(exchange, 200, response);
    }
}
