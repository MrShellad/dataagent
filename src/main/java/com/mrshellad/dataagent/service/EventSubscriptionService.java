package com.mrshellad.dataagent.service;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.mrshellad.dataagent.DataAgent;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EventSubscriptionService {

    private static final List<HttpExchange> clients = new CopyOnWriteArrayList<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PiAgent-SSE-KeepAlive");
        t.setDaemon(true);
        return t;
    });

    static {
        // Send a ping comment every 15 seconds to keep connections alive and prune dead clients
        scheduler.scheduleAtFixedRate(EventSubscriptionService::sendKeepAlive, 15, 15, TimeUnit.SECONDS);
    }

    /**
     * Registers an HTTP client connection for Server-Sent Events (SSE).
     */
    public static void subscribe(HttpExchange exchange) {
        try {
            // Set headers for Server-Sent Events
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            // 0 indicates chunked transfer encoding (dynamic length)
            exchange.sendResponseHeaders(200, 0);

            // Send initial connection OK event
            OutputStream os = exchange.getResponseBody();
            os.write("event: connected\ndata: {\"status\":\"ok\"}\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();

            clients.add(exchange);
            DataAgent.LOGGER.info("New SSE client subscribed. Total active clients: " + clients.size());
        } catch (IOException e) {
            DataAgent.LOGGER.error("Failed to establish SSE client subscription", e);
            try {
                exchange.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Broadcasts a JSON event to all registered SSE clients.
     */
    public static void broadcast(String eventType, JsonObject eventData) {
        String message = String.format("event: %s\ndata: %s\n\n", eventType, eventData.toString());
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);

        for (HttpExchange client : clients) {
            try {
                OutputStream os = client.getResponseBody();
                os.write(bytes);
                os.flush();
            } catch (IOException e) {
                // Client disconnected, prune it
                clients.remove(client);
                try {
                    client.close();
                } catch (Exception ignored) {}
                DataAgent.LOGGER.info("Pruned disconnected SSE client. Active clients: " + clients.size());
            }
        }
    }

    private static void sendKeepAlive() {
        byte[] pingBytes = ": ping\n\n".getBytes(StandardCharsets.UTF_8);
        for (HttpExchange client : clients) {
            try {
                OutputStream os = client.getResponseBody();
                os.write(pingBytes);
                os.flush();
            } catch (IOException e) {
                clients.remove(client);
                try {
                    client.close();
                } catch (Exception ignored) {}
                DataAgent.LOGGER.info("Pruned disconnected SSE client during keep-alive. Active clients: " + clients.size());
            }
        }
    }

    /**
     * Clears all active connections on server shutdown.
     */
    public static void shutdown() {
        scheduler.shutdown();
        for (HttpExchange client : clients) {
            try {
                client.close();
            } catch (Exception ignored) {}
        }
        clients.clear();
    }
}
