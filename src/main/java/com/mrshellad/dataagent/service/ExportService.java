package com.mrshellad.dataagent.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mrshellad.dataagent.core.ThreadScheduler;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ExportService {

    private final RegistryQueryService registryQueryService = new RegistryQueryService();
    private final RecipeQueryService recipeQueryService = new RecipeQueryService();
    private final LootQueryService lootQueryService = new LootQueryService();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Exports bulk game data to JSON files on the local disk.
     */
    public JsonObject exportData(String targetDir, List<String> exportTypes) throws IOException {
        Path path = Paths.get(targetDir);
        Files.createDirectories(path); // Ensure target directory exists (boundary condition)

        JsonObject fileRegistry = new JsonObject();

        for (String type : exportTypes) {
            String cleanType = type.trim().toLowerCase();
            switch (cleanType) {
                case "item":
                    JsonObject items = registryQueryService.queryRegistry("item", null, null, null, 1000000, 0);
                    Path itemPath = path.resolve("items.json");
                    Files.writeString(itemPath, GSON.toJson(items));
                    fileRegistry.addProperty("item", itemPath.toAbsolutePath().toString());
                    break;
                case "block":
                    JsonObject blocks = registryQueryService.queryRegistry("block", null, null, null, 1000000, 0);
                    Path blockPath = path.resolve("blocks.json");
                    Files.writeString(blockPath, GSON.toJson(blocks));
                    fileRegistry.addProperty("block", blockPath.toAbsolutePath().toString());
                    break;
                case "recipe":
                    JsonObject recipes = recipeQueryService.queryRecipes(null, null, null, 1000000, 0);
                    Path recipePath = path.resolve("recipes.json");
                    Files.writeString(recipePath, GSON.toJson(recipes));
                    fileRegistry.addProperty("recipe", recipePath.toAbsolutePath().toString());
                    break;
                case "loot_table":
                    JsonObject lootTablesJson = ThreadScheduler.call(() -> {
                        var server = ServerLifecycleHooks.getCurrentServer();
                        if (server == null) return new JsonObject();
                        
                        HolderLookup.Provider lookup = server.reloadableRegistries().lookup();
                        HolderLookup.RegistryLookup<LootTable> registry = lookup.lookupOrThrow(Registries.LOOT_TABLE);
                        JsonObject lootObj = new JsonObject();
                        
                        // Query all dynamic registry loot table keys
                        registry.listElements().forEach(holder -> {
                            Identifier id = holder.key().identifier();
                            try {
                                lootObj.add(id.toString(), lootQueryService.getLootTable(id.toString()));
                            } catch (Exception e) {
                                // Ignore non-serializable tables
                            }
                        });
                        return lootObj;
                    });
                    Path lootPath = path.resolve("loot_tables.json");
                    Files.writeString(lootPath, GSON.toJson(lootTablesJson));
                    fileRegistry.addProperty("loot_table", lootPath.toAbsolutePath().toString());
                    break;
                case "villager_trade":
                    var vtService = new VillagerTradesQueryService();
                    JsonObject trades = vtService.getAllTrades();
                    Path tradePath = path.resolve("villager_trades.json");
                    Files.writeString(tradePath, GSON.toJson(trades));
                    fileRegistry.addProperty("villager_trade", tradePath.toAbsolutePath().toString());
                    break;
                case "tag":
                    JsonObject tagsJson = ThreadScheduler.call(() -> {
                        var server = ServerLifecycleHooks.getCurrentServer();
                        if (server == null) return new JsonObject();
                        
                        JsonObject allTags = new JsonObject();
                        
                        // Collect item tags
                        JsonObject itemTags = new JsonObject();
                        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
                            Identifier itemId = entry.getKey().identifier();
                            var holder = BuiltInRegistries.ITEM.get(entry.getKey()).orElse(null);
                            if (holder != null) {
                                holder.tags().forEach(tagKey -> {
                                    String tagIdStr = tagKey.location().toString();
                                    JsonArray array = itemTags.getAsJsonArray(tagIdStr);
                                    if (array == null) {
                                        array = new JsonArray();
                                        itemTags.add(tagIdStr, array);
                                    }
                                    array.add(itemId.toString());
                                });
                            }
                        }
                        allTags.add("item", itemTags);

                        // Collect block tags
                        JsonObject blockTags = new JsonObject();
                        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
                            Identifier blockId = entry.getKey().identifier();
                            var holder = BuiltInRegistries.BLOCK.get(entry.getKey()).orElse(null);
                            if (holder != null) {
                                holder.tags().forEach(tagKey -> {
                                    String tagIdStr = tagKey.location().toString();
                                    JsonArray array = blockTags.getAsJsonArray(tagIdStr);
                                    if (array == null) {
                                        array = new JsonArray();
                                        blockTags.add(tagIdStr, array);
                                    }
                                    array.add(blockId.toString());
                                });
                            }
                        }
                        allTags.add("block", blockTags);

                        return allTags;
                    });
                    Path tagPath = path.resolve("tags.json");
                    Files.writeString(tagPath, GSON.toJson(tagsJson));
                    fileRegistry.addProperty("tag", tagPath.toAbsolutePath().toString());
                    break;
            }
        }

        // Generate metadata.json
        JsonObject metadata = new JsonObject();
        metadata.addProperty("minecraft_version", "1.21.4");
        metadata.addProperty("neoforge_version", "26.2");
        metadata.addProperty("timestamp", System.currentTimeMillis());
        metadata.addProperty("mod_count", net.neoforged.fml.ModList.get().getMods().size());
        
        Path metadataPath = path.resolve("metadata.json");
        Files.writeString(metadataPath, GSON.toJson(metadata));
        fileRegistry.addProperty("metadata", metadataPath.toAbsolutePath().toString());

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("message", "Bulk data successfully exported to disk.");
        response.add("exported_files", fileRegistry);

        return response;
    }
}
