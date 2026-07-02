package com.mrshellad.dataagent.api.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mrshellad.dataagent.api.HttpHandlerBase;
import com.mrshellad.dataagent.core.ThreadScheduler;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Map;

public class PlayerHandler extends HttpHandlerBase {

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws Exception {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed", "Only GET requests are supported on this endpoint.");
            return;
        }

        String targetName = params.get("name");

        JsonObject response = ThreadScheduler.call(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("Minecraft server is not running.");
            }

            var playerList = server.getPlayerList();
            ServerPlayer player = null;

            if (targetName != null && !targetName.isEmpty()) {
                player = playerList.getPlayerByName(targetName);
            } else {
                List<ServerPlayer> players = playerList.getPlayers();
                if (!players.isEmpty()) {
                    player = players.get(0);
                }
            }

            if (player == null) {
                return null; // Return null so we can send a 404 in the handler thread
            }

            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("username", player.getName().getString());
            playerJson.addProperty("uuid", player.getUUID().toString());

            ServerLevel level = (ServerLevel) player.level();
            playerJson.addProperty("dimension", level.dimension().identifier().toString());

            // Position
            JsonObject posJson = new JsonObject();
            posJson.addProperty("x", player.getX());
            posJson.addProperty("y", player.getY());
            posJson.addProperty("z", player.getZ());
            playerJson.add("position", posJson);

            // Structures player is inside
            JsonArray structuresArray = new JsonArray();
            BlockPos pos = player.blockPosition();
            try {
                Registry<Structure> registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
                for (var entry : registry.entrySet()) {
                    Structure structure = entry.getValue();
                    StructureStart start = level.structureManager().getStructureAt(pos, structure);
                    if (start != null && start.isValid()) {
                        structuresArray.add(entry.getKey().identifier().toString());
                    }
                }
            } catch (Exception e) {
                // Ignore structure lookup errors
            }
            playerJson.add("structures", structuresArray);

            var lookupProvider = server.registryAccess();

            // Inventory
            JsonArray invArray = new JsonArray();
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    JsonObject itemObj = new JsonObject();
                    itemObj.addProperty("slot", i);
                    itemObj.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    itemObj.addProperty("count", stack.getCount());
                    itemObj.add("detail", serializeItemStack(stack, lookupProvider));
                    invArray.add(itemObj);
                }
            }
            playerJson.add("inventory", invArray);

            // Ender Chest
            JsonArray ecArray = new JsonArray();
            PlayerEnderChestContainer enderChest = player.getEnderChestInventory();
            for (int i = 0; i < enderChest.getContainerSize(); i++) {
                ItemStack stack = enderChest.getItem(i);
                if (!stack.isEmpty()) {
                    JsonObject itemObj = new JsonObject();
                    itemObj.addProperty("slot", i);
                    itemObj.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    itemObj.addProperty("count", stack.getCount());
                    itemObj.add("detail", serializeItemStack(stack, lookupProvider));
                    ecArray.add(itemObj);
                }
            }
            playerJson.add("ender_chest", ecArray);

            return playerJson;
        });

        if (response == null) {
            sendError(exchange, 404, "Not Found", "No online players found.");
        } else {
            sendJson(exchange, 200, response);
        }
    }

    private JsonElement serializeItemStack(ItemStack stack, net.minecraft.core.HolderLookup.Provider lookupProvider) {
        if (stack.isEmpty()) {
            return new JsonObject();
        }
        try {
            var ops = RegistryOps.create(JsonOps.INSTANCE, lookupProvider);
            var result = ItemStack.CODEC.encodeStart(ops, stack);
            if (result.result().isPresent()) {
                return (JsonElement) result.result().get();
            }
        } catch (Exception e) {
            // Log fallback
        }
        JsonObject fallback = new JsonObject();
        fallback.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        fallback.addProperty("count", stack.getCount());
        return fallback;
    }
}
