package com.mrshellad.dataagent.api;

import com.sun.net.httpserver.HttpServer;
import com.mrshellad.dataagent.Config;
import com.mrshellad.dataagent.DataAgent;
import com.mrshellad.dataagent.api.handlers.*;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class HttpServerManager {

    private static HttpServer server;
    private static boolean isRunning = false;

    public static synchronized void start() {
        if (isRunning) {
            return;
        }

        if (!Config.ENABLE_API.get()) {
            return;
        }

        try {
            Class<?> vt = Class.forName("net.minecraft.world.item.trading.VillagerTrades");
            for (java.lang.reflect.Field f : vt.getDeclaredFields()) {
                if (f.getType().getName().contains("Map") || !f.getType().getName().contains("ResourceKey")) {
                    DataAgent.LOGGER.info("VILLAGER_TRADES_NON_KEY_FIELD: " + f.getName() + " of type " + f.getType().getName());
                }
            }
        } catch (Exception e) {
            DataAgent.LOGGER.error("VT Error", e);
        }

        try {
            Class<?> regs = Class.forName("net.minecraft.core.registries.Registries");
            for (java.lang.reflect.Field f : regs.getDeclaredFields()) {
                if (f.getName().contains("TRADE") || f.getName().contains("VILLAGER")) {
                    DataAgent.LOGGER.info("REGISTRY_KEY: " + f.getName() + " of type " + f.getType().getName());
                }
            }
        } catch (Exception e) {
            DataAgent.LOGGER.error("Regs Error", e);
        }

        try {
            Class<?> et = Class.forName("net.minecraft.world.entity.EntityType");
            for (java.lang.reflect.Field f : et.getDeclaredFields()) {
                if (f.getName().contains("VILLAGER")) {
                    DataAgent.LOGGER.info("VILLAGER_ENTITY_TYPE_FIELD: " + f.getName() + " of type " + f.getType().getName());
                }
            }
        } catch (Exception e) {
            DataAgent.LOGGER.error("ET Error", e);
        }

        int port = Config.API_PORT.get();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Execute handlers on Java 25 virtual threads
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            // Register routes
            server.createContext("/api/v1/status", new StatusHandler());
            server.createContext("/api/v1/reload", new ReloadHandler());
            server.createContext("/api/v1/registry", new RegistryHandler());
            server.createContext("/api/v1/recipes", new RecipeHandler());
            server.createContext("/api/v1/loot-tables", new LootTableHandler());
            server.createContext("/api/v1/advancements", new AdvancementHandler());
            server.createContext("/api/v1/export", new ExportHandler());
            server.createContext("/api/v1/villager-trades", new VillagerTradesHandler());
            server.createContext("/api/v1/events/subscribe", new EventSubscriptionHandler());
            server.createContext("/api/v1/entity-types", new EntityTypeHandler());
            server.createContext("/api/v1/command", new CommandHandler());
            server.createContext("/api/v1/files", new FilesHandler());
            server.createContext("/api/v1/logs", new LogsHandler());
            server.createContext("/api/v1/player", new PlayerHandler());

            server.start();
            isRunning = true;
            DataAgent.LOGGER.info("Pi Agent Mod HTTP API Server successfully started on port " + port);
        } catch (IOException e) {
            DataAgent.LOGGER.error("Failed to start Pi Agent Mod HTTP Server on port " + port + ". Is it already in use?", e);
        }
    }

    public static synchronized void stop() {
        if (!isRunning || server == null) {
            return;
        }

        try {
            server.stop(1); // Stop server with 1 second delay
            isRunning = false;
            DataAgent.LOGGER.info("Pi Agent Mod HTTP API Server stopped.");
        } catch (Exception e) {
            DataAgent.LOGGER.error("Error stopping HTTP API Server", e);
        }
    }

    public static synchronized void applyConfigChange() {
        boolean shouldBeRunning = Config.ENABLE_API.get() && ServerLifecycleHooks.getCurrentServer() != null;
        if (shouldBeRunning) {
            if (isRunning) {
                if (server != null && server.getAddress().getPort() != Config.API_PORT.get()) {
                    stop();
                    start();
                }
            } else {
                start();
            }
        } else {
            if (isRunning) {
                stop();
            }
        }
    }

    public static boolean isRunning() {
        return isRunning;
    }
}
