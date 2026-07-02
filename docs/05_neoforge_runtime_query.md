# 05. NeoForge 运行时查询开发手册

本手册为 **Pi Agent Mod** 的 Java 侧开发人员提供 NeoForge 26.2 (Minecraft 1.21.4) 运行时的具体接口调用参考，指导如何提取并序列化最底层、最真实的动态数据。

---

## 1. Web 服务器线程安全与主线程通信 (`agent-core`)

因为 Web 网络请求与 Minecraft 主线程异步，直接访问非线程安全的数据（如玩家实体、世界维度）会导致并发修改异常。使用 `ServerLifecycleHooks.getCurrentServer().execute(...)` 或是静态只读访问即可保证安全。

---

## 2. 动态注册表查询 (Biomes & Dimensions)

在 Minecraft 1.21.x 中，生物群系（Biomes）和维度（Dimensions）不再是静态的 `BuiltInRegistries` 项，而是通过数据包动态加载的**动态注册表（RegistryAccess）**。

### 2.1. 查询运行时所有生物群系
```java
package com.mrshellad.dataagent.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class BiomeQueryService {

    public JsonObject getBiomes() {
        JsonObject response = new JsonObject();
        JsonArray results = new JsonArray();

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // 从服务器的 RegistryAccess 中获取动态生物群系注册表
            Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);

            for (var entry : biomeRegistry.entrySet()) {
                var key = entry.getKey().location();
                Biome biome = entry.getValue();

                JsonObject biomeJson = new JsonObject();
                biomeJson.addProperty("id", key.toString());
                biomeJson.addProperty("has_precipitation", biome.hasPrecipitation());
                biomeJson.addProperty("temperature", biome.getBaseTemperature());
                
                results.add(biomeJson);
            }
        }

        response.add("results", results);
        return response;
    }
}
```

---

## 3. 村民交易查询 (`registry-service` / `debug-tools`)

村民交易表是由 `net.minecraft.world.entity.npc.VillagerTrades` 统一管理的静态数据。由于大多数 Mod 会动态往各个等级（1-5级）中插入 `ItemListing`（交易条目），我们需要反射或转换以提取真实可读的交易项目。

### 3.1. 遍历并获取特定职业交易
```java
package com.mrshellad.dataagent.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Random;

public class VillagerTradeService {

    public JsonObject getTradesForProfession(String professionId, int targetLevel) {
        JsonObject response = new JsonObject();
        JsonArray tradesArray = new JsonArray();

        // 1. 获取职业
        VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(new ResourceLocation(professionId));
        if (profession == null) return response;

        // 2. 从官方的 TRADES 容器中取得该职业的交易映射 (Key: 等级 1-5, Value: ItemListing[])
        Int2ObjectMap<ItemListing[]> tradesMap = VillagerTrades.TRADES.get(profession);
        if (tradesMap == null || !tradesMap.containsKey(targetLevel)) return response;

        ItemListing[] listings = tradesMap.get(targetLevel);
        
        // 模拟一个随机数种子用于获取 MerchantOffer（大部分 ItemListing 在转换为 Offer 时需要 Random）
        Random random = new Random();

        for (ItemListing listing : listings) {
            // ItemListing 并没有公开属性来获得 Cost/Result，需要调用其 toOffer() 方法实例化为 MerchantOffer
            // 传入一个虚拟的 NPC 实体或 null（注意：部分 Mod 的 listing 在入参为 null 时可能抛空指针，应做异常防护）
            try {
                MerchantOffer offer = listing.getOffer(null, random);
                if (offer != null) {
                    JsonObject tradeJson = new JsonObject();
                    
                    // 输入 A (如绿宝石)
                    JsonObject costA = new JsonObject();
                    costA.addProperty("id", BuiltInRegistries.ITEM.getKey(offer.getBaseCostA().getItem()).toString());
                    costA.addProperty("count", offer.getBaseCostA().getCount());
                    tradeJson.add("costA", costA);

                    // 输入 B (部分交易需要两件物品)
                    if (!offer.getCostB().isEmpty()) {
                        JsonObject costB = new JsonObject();
                        costB.addProperty("id", BuiltInRegistries.ITEM.getKey(offer.getCostB().getItem()).toString());
                        costB.addProperty("count", offer.getCostB().getCount());
                        tradeJson.add("costB", costB);
                    } else {
                        tradeJson.add("costB", null);
                    }

                    // 输出结果物品
                    JsonObject result = new JsonObject();
                    result.addProperty("id", BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).toString());
                    result.addProperty("count", offer.getResult().getCount());
                    tradeJson.add("result", result);

                    tradeJson.addProperty("max_uses", offer.getMaxUses());
                    tradeJson.addProperty("xp_reward", offer.getXp());
                    tradeJson.addProperty("price_multiplier", offer.getPriceMultiplier());

                    tradesArray.add(tradeJson);
                }
            } catch (Exception e) {
                // 忽略解析失败的非标自定义条目
            }
        }

        response.add("trades", tradesArray);
        return response;
    }
}
```

---

## 4. 配方与战利品表最终状态序列化

在 Minecraft 1.21.x 之后，数据序列化必须配合 Mojang 的 Codec 使用。以下展示如何快速从 `RecipeManager` 中序列化合并了 KubeJS / CraftTweaker 修改后的最新运行时配方：

```java
// 利用 Mojang 官方 Recipe CODEC 将动态配方对象序列化为 Gson Element
JsonObject recipeJson = new JsonObject();
var recipeCodec = (net.mojang.serialization.Codec<net.minecraft.world.item.crafting.Recipe<?>>) Recipe.CODEC;

com.google.gson.JsonElement detailElement = recipeCodec.encodeStart(JsonOps.INSTANCE, recipe)
        .result()
        .map(json -> (com.google.gson.JsonElement) json)
        .orElse(null);

if (detailElement != null) {
    recipeJson.add("detail", detailElement);
}
```

---

## 5. 执行指令触发数据包/脚本重载 (`agent-core`)

当 AI 在本地生成并保存完新的开发资源文件后，可以通过调用以下代码在主线程排队执行热重载，并触发 KubeJS 与 CraftTweaker 的数据更新：

```java
public void triggerServerReload() {
    var server = ServerLifecycleHooks.getCurrentServer();
    if (server != null) {
        server.execute(() -> {
            // 重载数据包和配方
            server.reloadResources(server.packRepository().getSelectedIds())
                  .exceptionally(throwable -> {
                      System.err.println("重载数据包时发生异常: " + throwable.getMessage());
                      return null;
                  });
        });
    }
}
```
通过这种方式，AI 客户端可以直接用代码热部署和验证修改后的整合包配置。
