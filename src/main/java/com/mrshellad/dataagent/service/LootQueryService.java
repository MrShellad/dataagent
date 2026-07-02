package com.mrshellad.dataagent.service;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mrshellad.dataagent.core.ThreadScheduler;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class LootQueryService {

    /**
     * Retrieves the JSON representation of an active loot table by its ID.
     */
    public JsonElement getLootTable(String lootTableId) {
        return ThreadScheduler.call(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("Minecraft server is not running.");
            }

            Identifier loc = Identifier.tryParse(lootTableId);
            if (loc == null) {
                throw new IllegalArgumentException("Invalid loot table ID: '" + lootTableId + "'");
            }

            // Get lookup provider from reloadable server registries
            HolderLookup.Provider lookup = server.reloadableRegistries().lookup();
            HolderLookup.RegistryLookup<LootTable> lootTables = lookup.lookupOrThrow(Registries.LOOT_TABLE);
            
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, loc);
            var holderOptional = lootTables.get(key);
            if (holderOptional.isEmpty()) {
                throw new IllegalArgumentException("Loot table not found: '" + lootTableId + "'");
            }

            var holder = holderOptional.get();
            LootTable lootTable = holder.value();

            // Create RegistryOps to provide the necessary registry context for serialization
            var ops = RegistryOps.create(JsonOps.INSTANCE, lookup);

            // Serialize using Mojang Direct Codec
            var result = LootTable.DIRECT_CODEC.encodeStart(ops, lootTable);
            if (result.result().isPresent()) {
                return (JsonElement) result.result().get();
            } else {
                throw new RuntimeException("Failed to serialize loot table using Mojang Codec.");
            }
        });
    }
}
