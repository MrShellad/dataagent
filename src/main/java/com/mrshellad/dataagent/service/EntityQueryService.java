package com.mrshellad.dataagent.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mrshellad.dataagent.core.ThreadScheduler;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class EntityQueryService {

    /**
     * Queries all registered entity types with filters, default attributes, size dimensions, and pagination.
     */
    public JsonObject queryEntityTypes(String namespace, String category, String search, int limit, int offset) {
        return ThreadScheduler.call(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("Minecraft server is not running.");
            }

            JsonObject response = new JsonObject();
            JsonArray results = new JsonArray();

            int matchedCount = 0;
            int addedCount = 0;

            for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                if (id == null) continue;

                // 1. Namespace filter
                if (namespace != null && !namespace.isEmpty() && !id.getNamespace().equalsIgnoreCase(namespace)) {
                    continue;
                }

                // 2. Category filter
                String catName = entityType.getCategory().getName();
                if (category != null && !category.isEmpty() && !catName.equalsIgnoreCase(category)) {
                    continue;
                }

                // 3. Search query filter (fuzzy search match on path)
                if (search != null && !search.isEmpty() && !id.getPath().toLowerCase().contains(search.toLowerCase())) {
                    continue;
                }

                if (matchedCount >= offset && addedCount < limit) {
                    JsonObject entityJson = new JsonObject();
                    entityJson.addProperty("id", id.toString());
                    entityJson.addProperty("translation_key", entityType.getDescriptionId());
                    entityJson.addProperty("category", catName);
                    entityJson.addProperty("summonable", entityType.canSummon());
                    entityJson.addProperty("fire_immune", entityType.fireImmune());
                    
                    var dims = entityType.getDimensions();
                    entityJson.addProperty("width", dims.width());
                    entityJson.addProperty("height", dims.height());

                    // Default Loot Table ID
                    entityType.getDefaultLootTable().ifPresent(key -> {
                        entityJson.addProperty("loot_table", key.identifier().toString());
                    });

                    // Attributes
                    JsonObject attrsJson = new JsonObject();
                    if (DefaultAttributes.hasSupplier(entityType)) {
                        @SuppressWarnings("unchecked")
                        EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) entityType;
                        AttributeSupplier supplier = DefaultAttributes.getSupplier(livingType);

                        for (var entry : BuiltInRegistries.ATTRIBUTE.entrySet()) {
                            var attrKey = entry.getKey();
                            Attribute attribute = entry.getValue();
                            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute);
                            if (holder != null && supplier.hasAttribute(holder)) {
                                attrsJson.addProperty(attrKey.identifier().toString(), supplier.getValue(holder));
                            }
                        }
                    }
                    entityJson.add("attributes", attrsJson);

                    results.add(entityJson);
                    addedCount++;
                }
                matchedCount++;
            }

            response.addProperty("total", matchedCount);
            response.addProperty("limit", limit);
            response.addProperty("offset", offset);
            response.add("results", results);

            return response;
        });
    }
}
