package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class FilesHandler extends HttpHandlerBase {

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        String pathInfo = exchange.getRequestURI().getPath();

        if (pathInfo.startsWith("/api/v1/files/list")) {
            handleList(exchange, params);
        } else if (pathInfo.startsWith("/api/v1/files/read")) {
            handleRead(exchange, params);
        } else if (pathInfo.startsWith("/api/v1/files/write")) {
            handleWrite(exchange, params);
        } else {
            sendError(exchange, 404, "Not Found", "Sub-endpoint not found. Supported: /list, /read, /write");
        }
    }

    private void handleList(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET is supported.");
            return;
        }

        String dirParam = getParam(params, "dir", ".");
        Path targetDir = resolvePath(dirParam);

        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            sendError(exchange, 400, "Bad Request", "Directory does not exist: " + dirParam);
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("directory", dirParam);
        JsonArray filesArray = new JsonArray();

        try (var stream = Files.list(targetDir)) {
            stream.forEach(p -> {
                JsonObject fileObj = new JsonObject();
                fileObj.addProperty("name", p.getFileName().toString());
                fileObj.addProperty("path", Paths.get(".").toAbsolutePath().relativize(p.toAbsolutePath()).toString().replace("\\", "/"));
                fileObj.addProperty("is_directory", Files.isDirectory(p));
                try {
                    fileObj.addProperty("size", Files.size(p));
                } catch (Exception ignored) {
                    fileObj.addProperty("size", 0);
                }
                filesArray.add(fileObj);
            });
        }

        response.add("files", filesArray);
        sendJson(exchange, 200, response);
    }

    private void handleRead(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET is supported.");
            return;
        }

        String pathParam = params.get("path");
        if (pathParam == null || pathParam.isEmpty()) {
            sendError(exchange, 400, "Bad Request", "Missing 'path' parameter.");
            return;
        }

        Path targetFile = resolvePath(pathParam);

        if (!Files.exists(targetFile) || Files.isDirectory(targetFile)) {
            sendError(exchange, 400, "Bad Request", "File does not exist: " + pathParam);
            return;
        }

        String content = Files.readString(targetFile, StandardCharsets.UTF_8);

        JsonObject response = new JsonObject();
        response.addProperty("path", pathParam);
        response.addProperty("content", content);

        sendJson(exchange, 200, response);
    }

    private void handleWrite(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only POST is supported.");
            return;
        }

        String pathParam = params.get("path");
        String contentParam = null;

        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (body != null && !body.trim().isEmpty()) {
            try {
                JsonObject json = GSON.fromJson(body, JsonObject.class);
                if (json.has("path")) {
                    pathParam = json.get("path").getAsString();
                }
                if (json.has("content")) {
                    contentParam = json.get("content").getAsString();
                }
            } catch (Exception e) {
                // Ignore JSON parse errors
            }
        }

        if (pathParam == null || pathParam.isEmpty()) {
            sendError(exchange, 400, "Bad Request", "Missing 'path' parameter.");
            return;
        }

        if (contentParam == null) {
            contentParam = "";
        }

        Path targetFile = resolvePath(pathParam);

        // Ensure parent directories exist
        Path parent = targetFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.writeString(targetFile, contentParam, StandardCharsets.UTF_8);

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("message", "File written successfully.");
        response.addProperty("path", pathParam);

        sendJson(exchange, 200, response);
    }

    private Path resolvePath(String pathStr) {
        Path p = Paths.get(pathStr);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        // Resolve relative to current working directory (Minecraft root)
        return Paths.get(".").toAbsolutePath().resolve(p).normalize();
    }
}
