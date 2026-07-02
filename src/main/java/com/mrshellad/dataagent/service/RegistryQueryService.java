package com.mrshellad.dataagent.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mrshellad.dataagent.core.ThreadScheduler;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.Optional;

public class RegistryQueryService {

    /**
     * Queries a specified registry type with filters and pagination.
     * Delegates registry fetching to the main server thread via ThreadScheduler.
     */
    public JsonObject queryRegistry(String type, String namespace, String tag, String search, int limit, int offset) {
        return ThreadScheduler.call(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("Minecraft server is not running.");
            }

            RegistryAccess access = server.registryAccess();
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, access);

            switch (type.toLowerCase()) {
                case "item":
                    return queryGenericRegistry(BuiltInRegistries.ITEM, Registries.ITEM, namespace, tag, search, limit, offset, ops);
                case "block":
                    return queryGenericRegistry(BuiltInRegistries.BLOCK, Registries.BLOCK, namespace, tag, search, limit, offset, ops);
                case "fluid":
                    return queryGenericRegistry(BuiltInRegistries.FLUID, Registries.FLUID, namespace, tag, search, limit, offset, ops);
                case "entity_type":
                    return queryGenericRegistry(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, namespace, tag, search, limit, offset, ops);
                case "enchantment":
                    Registry<net.minecraft.world.item.enchantment.Enchantment> enchantments = access.lookupOrThrow(Registries.ENCHANTMENT);
                    return queryGenericRegistry(enchantments, Registries.ENCHANTMENT, namespace, tag, search, limit, offset, ops);
                case "biome":
                    Registry<net.minecraft.world.level.biome.Biome> biomes = access.lookupOrThrow(Registries.BIOME);
                    return queryGenericRegistry(biomes, Registries.BIOME, namespace, tag, search, limit, offset, ops);
                case "dimension":
                    Registry<net.minecraft.world.level.dimension.DimensionType> dimensions = access.lookupOrThrow(Registries.DIMENSION_TYPE);
                    return queryGenericRegistry(dimensions, Registries.DIMENSION_TYPE, namespace, tag, search, limit, offset, ops);
                default:
                    throw new IllegalArgumentException("Unsupported registry type: '" + type + "'. Supported types: item, block, fluid, entity_type, enchantment, biome, dimension.");
            }
        });
    }

    private <T> JsonObject queryGenericRegistry(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey, String namespace, String tag, String search, int limit, int offset, net.minecraft.resources.RegistryOps<com.google.gson.JsonElement> ops) {
        JsonObject response = new JsonObject();
        JsonArray results = new JsonArray();

        // 1. Tag filtering setup
        TagKey<T> tagKey = null;
        if (tag != null && !tag.isEmpty()) {
            Identifier tagLoc = Identifier.tryParse(tag);
            if (tagLoc != null) {
                tagKey = TagKey.create(registryKey, tagLoc);
            }
        }

        int matchedCount = 0;
        int addedCount = 0;

        for (var entry : registry.entrySet()) {
            Identifier id = entry.getKey().identifier();
            T value = entry.getValue();

            // Filter by namespace
            if (namespace != null && !namespace.isEmpty() && !id.getNamespace().equalsIgnoreCase(namespace)) {
                continue;
            }

            // Filter by search keyword (case-insensitive)
            if (search != null && !search.isEmpty() && !id.getPath().toLowerCase().contains(search.toLowerCase())) {
                continue;
            }

            // Filter by tag
            if (tagKey != null) {
                var holder = registry.get(entry.getKey()).orElse(null);
                if (holder == null || !holder.is(tagKey)) {
                    continue;
                }
            }

            // Match found! Apply pagination
            if (matchedCount >= offset && addedCount < limit) {
                JsonObject itemJson = new JsonObject();
                itemJson.addProperty("id", id.toString());
                
                String translationKey = getTranslationKey(value, id);
                if (translationKey != null) {
                    itemJson.addProperty("translation_key", translationKey);
                }

                // Append tags list
                JsonArray tagsArray = new JsonArray();
                var holder = registry.get(entry.getKey()).orElse(null);
                if (holder != null) {
                    holder.tags().forEach(t -> tagsArray.add(t.location().toString()));
                }
                itemJson.add("tags", tagsArray);

                // Enrich item/block detailed properties
                if (value instanceof net.minecraft.world.item.Item item) {
                    enrichItemJson(itemJson, item, ops);
                } else if (value instanceof net.minecraft.world.level.block.Block block) {
                    enrichBlockJson(itemJson, block);
                }

                results.add(itemJson);
                addedCount++;
            }
            matchedCount++;
        }

        response.addProperty("total", matchedCount);
        response.addProperty("limit", limit);
        response.addProperty("offset", offset);
        response.add("results", results);

        return response;
    }

    private void enrichItemJson(JsonObject json, net.minecraft.world.item.Item item, net.minecraft.resources.RegistryOps<com.google.gson.JsonElement> ops) {
        net.minecraft.world.item.ItemStack stack = item.getDefaultInstance();
        json.addProperty("max_stack_size", stack.getMaxStackSize());
        json.addProperty("max_damage", stack.getMaxDamage());
        json.addProperty("is_damageable", stack.isDamageableItem());
        try {
            json.addProperty("rarity", stack.getRarity().name());
        } catch (Exception e) {}

        // Serialize all components
        try {
            var result = net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, stack);
            if (result.result().isPresent() && result.result().get().isJsonObject()) {
                JsonObject itemStackJson = result.result().get().getAsJsonObject();
                if (itemStackJson.has("components")) {
                    json.add("components", itemStackJson.getAsJsonObject("components"));
                }
            }
        } catch (Exception e) {
            // Ignore serialization errors
        }

        // Flat attributes extraction
        JsonObject attributesObj = new JsonObject();
        try {
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                JsonArray slotAttrs = new JsonArray();
                stack.forEachModifier(slot, (attributeHolder, modifier) -> {
                    JsonObject attrMod = new JsonObject();
                    String attrId = BuiltInRegistries.ATTRIBUTE.getKey(attributeHolder.value()).toString();
                    attrMod.addProperty("attribute", attrId);
                    attrMod.addProperty("name", modifier.id().toString());
                    attrMod.addProperty("amount", modifier.amount());
                    attrMod.addProperty("operation", modifier.operation().name());
                    slotAttrs.add(attrMod);
                });
                if (slotAttrs.size() > 0) {
                    attributesObj.add(slot.getName(), slotAttrs);
                }
            }
        } catch (Exception e) {
            // Ignore exceptions
        }
        if (attributesObj.size() > 0) {
            json.add("attributes", attributesObj);
        }
    }

    private void enrichBlockJson(JsonObject json, net.minecraft.world.level.block.Block block) {
        try {
            var state = block.defaultBlockState();
            json.addProperty("hardness", state.getDestroySpeed(null, null));
            json.addProperty("requires_tool", state.requiresCorrectToolForDrops());
            json.addProperty("light_emission", state.getLightEmission());
        } catch (Exception e) {
            // Ignore exceptions
        }
    }


    private String getTranslationKey(Object value, Identifier id) {
        if (value instanceof net.minecraft.world.item.Item item) {
            return item.getDescriptionId();
        } else if (value instanceof net.minecraft.world.level.block.Block block) {
            return block.getDescriptionId();
        } else if (value instanceof net.minecraft.world.level.material.Fluid fluid) {
            return fluid.getFluidType().getDescriptionId();
        } else if (value instanceof net.minecraft.world.entity.EntityType<?> entityType) {
            return entityType.getDescriptionId();
        } else if (value instanceof net.minecraft.world.item.enchantment.Enchantment) {
            return "enchantment." + id.getNamespace() + "." + id.getPath();
        } else if (value instanceof net.minecraft.world.level.biome.Biome) {
            return "biome." + id.getNamespace() + "." + id.getPath();
        } else if (value instanceof net.minecraft.world.level.dimension.DimensionType) {
            return "dimension." + id.getNamespace() + "." + id.getPath();
        }
        return null;
    }
}
