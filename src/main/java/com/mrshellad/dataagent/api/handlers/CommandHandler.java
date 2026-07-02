package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.core.ThreadScheduler;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class CommandHandler extends HttpHandlerBase {

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only POST requests are supported on this endpoint.");
            return;
        }

        // Read command from JSON body or query parameter
        String command = params.get("command");

        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (body != null && !body.trim().isEmpty()) {
            try {
                JsonObject json = GSON.fromJson(body, JsonObject.class);
                if (json.has("command")) {
                    command = json.get("command").getAsString();
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        if (command == null || command.trim().isEmpty()) {
            sendError(exchange, 400, "Bad Request", "Missing 'command' parameter in request body or query string.");
            return;
        }

        // Ensure commands start without / (performPrefixedCommand does not expect / prefix usually, but we can strip it)
        String finalCommand = command.trim();
        if (finalCommand.startsWith("/")) {
            finalCommand = finalCommand.substring(1);
        }

        final String cmdToRun = finalCommand;

        // Execute on server thread and capture output
        JsonObject response = ThreadScheduler.call(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("Minecraft server is not running.");
            }

            CommandOutputCapturer capturer = new CommandOutputCapturer();
            CommandSourceStack stack = server.createCommandSourceStack()
                    .withSource(capturer);

            try {
                server.getCommands().performPrefixedCommand(stack, cmdToRun);
            } catch (Exception e) {
                capturer.sendSystemMessage(Component.literal("Execution error: " + e.getMessage()));
            }

            JsonObject res = new JsonObject();
            res.addProperty("status", "success");
            res.addProperty("output", capturer.getOutput());
            return res;
        });

        sendJson(exchange, 200, response);
    }

    private static class CommandOutputCapturer implements CommandSource {
        private final StringBuilder output = new StringBuilder();

        @Override
        public void sendSystemMessage(Component message) {
            output.append(message.getString()).append("\n");
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        public String getOutput() {
            return output.toString().trim();
        }
    }
}
