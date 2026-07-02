package com.mrshellad.dataagent.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.mrshellad.dataagent.DataAgent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public abstract class HttpHandlerBase implements HttpHandler {

    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers for local client integrations
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        // Handle preflight OPTIONS request
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        // Verify if Minecraft Server is running and available
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            sendError(exchange, 503, "Service Unavailable", "Minecraft server is currently starting up or shutting down.");
            return;
        }

        try {
            Map<String, String> queryParams = parseQueryParams(exchange);
            handleRequest(exchange, queryParams);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Bad Request", e.getMessage());
        } catch (Exception e) {
            DataAgent.LOGGER.error("Error handling HTTP request for " + exchange.getRequestURI(), e);
            sendError(exchange, 500, "Internal Server Error", e.getMessage() != null ? e.getMessage() : "An unexpected error occurred.");
        } finally {
            if (!isPersistentConnection()) {
                exchange.close();
            }
        }
    }

    /**
     * Subclasses can override this to return true if the connection needs to remain open (e.g. for SSE).
     */
    protected boolean isPersistentConnection() {
        return false;
    }

    /**
     * Subclasses must implement this to handle requests.
     */
    protected abstract void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception;

    /**
     * Send a JSON response.
     */
    protected void sendJson(HttpExchange exchange, int status, Object responseObj) throws IOException {
        String json = GSON.toJson(responseObj);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Send a structured JSON error response.
     */
    protected void sendError(HttpExchange exchange, int status, String error, String message) throws IOException {
        JsonObject errorJson = new JsonObject();
        errorJson.addProperty("code", status);
        errorJson.addProperty("error", error);
        errorJson.addProperty("message", message);
        sendJson(exchange, status, errorJson);
    }

    /**
     * Parse URL query parameters.
     */
    protected Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> queryParams = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return queryParams;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx > 0 && idx < pair.length() - 1) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    queryParams.put(key, val);
                } else if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    queryParams.put(key, "");
                }
            } catch (Exception e) {
                // Ignore malformed query parameters
            }
        }
        return queryParams;
    }

    /**
     * Retrieve parameter with a default value.
     */
    protected String getParam(Map<String, String> params, String key, String defaultValue) {
        String val = params.get(key);
        return val != null ? val : defaultValue;
    }

    /**
     * Retrieve integer parameter safely. Clamps values to valid bounds if necessary.
     */
    protected int getIntParam(Map<String, String> params, String key, int defaultValue) {
        String val = params.get(key);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
