package com.mrshellad.dataagent.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mrshellad.dataagent.core.ThreadScheduler;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class AdvancementQueryService {

    /**
     * Queries active achievements/advancements from the ServerAdvancementManager.
     */
    public JsonArray getAdvancements(String idFilter) {
        return ThreadScheduler.call(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("Minecraft server is not running.");
            }

            var advancementManager = server.getAdvancements();
            JsonArray results = new JsonArray();

            for (AdvancementHolder holder : advancementManager.getAllAdvancements()) {
                Identifier id = holder.id();

                if (idFilter != null && !idFilter.isEmpty() && !id.toString().equalsIgnoreCase(idFilter)) {
                    continue;
                }

                JsonObject advJson = new JsonObject();
                advJson.addProperty("id", id.toString());

                // Parent ID
                if (holder.value().parent().isPresent()) {
                    advJson.addProperty("parent", holder.value().parent().get().toString());
                } else {
                    advJson.add("parent", null);
                }

                // Display info
                holder.value().display().ifPresent(display -> {
                    JsonObject displayJson = new JsonObject();
                    displayJson.addProperty("title", display.getTitle().getString());
                    displayJson.addProperty("description", display.getDescription().getString());
                    advJson.add("display", displayJson);
                });

                // Criteria
                JsonObject criteriaJson = new JsonObject();
                holder.value().criteria().forEach((name, criterion) -> {
                    JsonObject critDetails = new JsonObject();
                    critDetails.addProperty("trigger", criterion.trigger().toString());
                    criteriaJson.add(name, critDetails);
                });
                advJson.add("criteria", criteriaJson);

                results.add(advJson);
            }

            return results;
        });
    }
}
