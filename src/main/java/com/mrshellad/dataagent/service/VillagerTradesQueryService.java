package com.mrshellad.dataagent.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mrshellad.dataagent.DataAgent;
import com.mrshellad.dataagent.core.ThreadScheduler;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.lang.reflect.Field;
import java.util.Optional;

public class VillagerTradesQueryService {

    private static Field wantsField;
    private static Field additionalWantsField;
    private static Field givesField;
    private static Field maxUsesField;
    private static Field xpField;
    private static Field reputationDiscountField;

    static {
        try {
            Class<?> vtClass = Class.forName("net.minecraft.world.item.trading.VillagerTrade");
            wantsField = vtClass.getDeclaredField("wants");
            wantsField.setAccessible(true);
            
            additionalWantsField = vtClass.getDeclaredField("additionalWants");
            additionalWantsField.setAccessible(true);
            
            givesField = vtClass.getDeclaredField("gives");
            givesField.setAccessible(true);
            
            maxUsesField = vtClass.getDeclaredField("maxUses");
            maxUsesField.setAccessible(true);
            
            xpField = vtClass.getDeclaredField("xp");
            xpField.setAccessible(true);
            
            reputationDiscountField = vtClass.getDeclaredField("reputationDiscount");
            reputationDiscountField.setAccessible(true);
        } catch (Exception e) {
            DataAgent.LOGGER.error("Failed to initialize VillagerTrade reflection fields", e);
        }
    }

    public JsonArray getTrades(String professionId, int level) {
        return ThreadScheduler.call(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                throw new IllegalStateException("Minecraft server is not running.");
            }

            var access = server.registryAccess();
            Registry<TradeSet> tradeSets = access.lookupOrThrow(Registries.TRADE_SET);

            Identifier profLoc = Identifier.tryParse(professionId);
            if (profLoc == null) {
                throw new IllegalArgumentException("Invalid profession ID: " + professionId);
            }

            String tradeSetPath = profLoc.getPath() + "/level_" + level;
            Identifier tradeSetId = Identifier.fromNamespaceAndPath(profLoc.getNamespace(), tradeSetPath);

            TradeSet tradeSet = tradeSets.get(tradeSetId).map(Holder::value).orElse(null);
            if (tradeSet == null) {
                throw new IllegalArgumentException("No trade set found for profession '" + professionId + "' at level " + level);
            }

            JsonArray response = new JsonArray();

            for (Holder<VillagerTrade> holder : tradeSet.getTrades()) {
                VillagerTrade trade = holder.value();
                JsonObject tradeJson = new JsonObject();

                try {
                    // Read field values using reflection
                    TradeCost wants = (TradeCost) wantsField.get(trade);
                    @SuppressWarnings("unchecked")
                    Optional<TradeCost> additionalWants = (Optional<TradeCost>) additionalWantsField.get(trade);
                    ItemStackTemplate gives = (ItemStackTemplate) givesField.get(trade);
                    NumberProvider maxUses = (NumberProvider) maxUsesField.get(trade);
                    NumberProvider xp = (NumberProvider) xpField.get(trade);
                    NumberProvider reputationDiscount = (NumberProvider) reputationDiscountField.get(trade);

                    // Serialize "wants"
                    if (wants != null) {
                        tradeJson.add("wants", serializeTradeCost(wants));
                    }

                    // Serialize "additionalWants"
                    if (additionalWants != null && additionalWants.isPresent()) {
                        tradeJson.add("additionalWants", serializeTradeCost(additionalWants.get()));
                    }

                    // Serialize "gives"
                    if (gives != null) {
                        tradeJson.add("gives", serializeItemStackTemplate(gives));
                    }

                    // Serialize maxUses, xp, reputationDiscount
                    if (maxUses != null) {
                        tradeJson.addProperty("max_uses", getNumberProviderValue(maxUses));
                    }
                    if (xp != null) {
                        tradeJson.addProperty("xp", getNumberProviderValue(xp));
                    }
                    if (reputationDiscount != null) {
                        tradeJson.addProperty("reputation_discount", getNumberProviderValue(reputationDiscount));
                    }

                } catch (Exception e) {
                    DataAgent.LOGGER.error("Failed to read trade fields via reflection", e);
                }

                response.add(tradeJson);
            }

            return response;
        });
    }

    /**
     * Retrieves all trades for all registered professions and levels.
     */
    public JsonObject getAllTrades() {
        return ThreadScheduler.call(() -> {
            JsonObject allTrades = new JsonObject();
            for (var entry : BuiltInRegistries.VILLAGER_PROFESSION.entrySet()) {
                String profId = entry.getKey().identifier().toString();
                JsonObject profJson = new JsonObject();
                for (int level = 1; level <= 5; level++) {
                    try {
                        JsonArray trades = getTrades(profId, level);
                        if (trades.size() > 0) {
                            profJson.add(String.valueOf(level), trades);
                        }
                    } catch (Exception e) {
                        // Skip levels that don't have trades
                    }
                }
                if (profJson.size() > 0) {
                    allTrades.add(profId, profJson);
                }
            }
            return allTrades;
        });
    }

    private JsonObject serializeTradeCost(TradeCost cost) {
        JsonObject json = new JsonObject();
        if (cost != null && cost.item() != null) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(cost.item().value());
            json.addProperty("item", itemId.toString());
            
            if (cost.count() != null) {
                if (cost.count() instanceof ConstantValue constant) {
                    json.addProperty("count", (int) constant.value());
                } else {
                    json.addProperty("count", cost.count().toString());
                }
            }
        }
        return json;
    }

    private JsonObject serializeItemStackTemplate(ItemStackTemplate template) {
        JsonObject json = new JsonObject();
        if (template != null && template.item() != null) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(template.item().value());
            json.addProperty("item", itemId.toString());
            json.addProperty("count", template.count());
        }
        return json;
    }

    private String getNumberProviderValue(NumberProvider provider) {
        if (provider instanceof ConstantValue constant) {
            return String.valueOf((int) constant.value());
        }
        return provider.toString();
    }
}
