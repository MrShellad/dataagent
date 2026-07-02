package com.mrshellad.dataagent.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mrshellad.dataagent.core.ThreadScheduler;
import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class RecipeQueryService {

    /**
     * Queries active recipes from the RecipeManager on the main server thread.
     */
    public JsonObject queryRecipes(String inputItem, String outputItem, String typeFilter, int limit, int offset) {
        return ThreadScheduler.call(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("Minecraft server is not running.");
            }

            RecipeManager recipeManager = server.getRecipeManager();
            JsonObject response = new JsonObject();
            JsonArray results = new JsonArray();

            int matchedCount = 0;
            int addedCount = 0;

            // Create RegistryOps with server registry context
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());

            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                Identifier id = holder.id().identifier();
                Recipe<?> recipe = holder.value();

                // 1. Filter by Recipe Type
                Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                if (typeFilter != null && !typeFilter.isEmpty() && !typeId.toString().equalsIgnoreCase(typeFilter)) {
                    continue;
                }

                // Serialize recipe using Mojang Codec with safe fallback
                JsonElement detailJson = null;
                String serializationError = null;
                try {
                    @SuppressWarnings("unchecked")
                    Codec<Recipe<?>> recipeCodec = (Codec<Recipe<?>>) Recipe.CODEC;
                    var result = recipeCodec.encodeStart(ops, recipe);
                    if (result.result().isPresent()) {
                        detailJson = (JsonElement) result.result().get();
                    } else {
                        serializationError = "Codec failed to encode recipe.";
                    }
                } catch (Exception e) {
                    serializationError = "Exception during encoding: " + e.getMessage();
                }

                // 2. Filter by Output Item
                String actualOutputId = getOutputItemId(detailJson);
                if (outputItem != null && !outputItem.isEmpty()) {
                    if (actualOutputId == null || !actualOutputId.equalsIgnoreCase(outputItem)) {
                        continue;
                    }
                }

                // 3. Filter by Input Item
                if (inputItem != null && !inputItem.isEmpty()) {
                    if (!matchesInputItem(detailJson, inputItem)) {
                        continue;
                    }
                }

                // Match found! Apply pagination
                if (matchedCount >= offset && addedCount < limit) {
                    JsonObject recipeJson = new JsonObject();
                    recipeJson.addProperty("id", id.toString());
                    recipeJson.addProperty("type", typeId.toString());

                    if (detailJson != null) {
                        recipeJson.add("detail", detailJson);
                    } else {
                        recipeJson.addProperty("serialization_error", serializationError);
                        JsonObject fallbackDetail = new JsonObject();
                        if (actualOutputId != null) {
                            fallbackDetail.addProperty("output", actualOutputId);
                        }
                        recipeJson.add("detail", fallbackDetail);
                    }

                    results.add(recipeJson);
                    addedCount++;
                }
                matchedCount++;
            }

            response.addProperty("total", matchedCount);
            response.addProperty("limit", limit);
            response.addProperty("offset", offset);
            response.add("recipes", results);

            return response;
        });
    }

    private boolean matchesInputItem(JsonElement detail, String inputItemFilter) {
        if (detail == null || !detail.isJsonObject()) return false;
        JsonObject obj = detail.deepCopy().getAsJsonObject();
        // Remove output result field to prevent matching output items
        obj.remove("result");
        String jsonString = obj.toString();

        // 1. Check direct item ID match
        if (jsonString.contains("\"" + inputItemFilter + "\"")) {
            return true;
        }

        // 2. Check tag match (if the item belongs to any tag referenced in the recipe)
        try {
            Identifier itemId = Identifier.tryParse(inputItemFilter);
            if (itemId != null) {
                var holderOpt = BuiltInRegistries.ITEM.get(itemId);
                if (holderOpt.isPresent()) {
                    var holder = holderOpt.get();
                    for (var tagKey : holder.tags().toList()) {
                        String tagId = tagKey.location().toString();
                        if (jsonString.contains("\"" + tagId + "\"")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore reflection/lookup issues
        }

        return false;
    }

    private String getOutputItemId(JsonElement detail) {
        if (detail == null || !detail.isJsonObject()) return null;
        JsonObject obj = detail.getAsJsonObject();
        if (obj.has("result")) {
            JsonElement resultVal = obj.get("result");
            if (resultVal.isJsonPrimitive()) {
                return resultVal.getAsString();
            } else if (resultVal.isJsonObject()) {
                JsonObject resObj = resultVal.getAsJsonObject();
                if (resObj.has("id")) {
                    return resObj.get("id").getAsString();
                }
            }
        }
        return null;
    }
}
