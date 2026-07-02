package com.mrshellad.dataagent;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static void onLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            com.mrshellad.dataagent.api.HttpServerManager.applyConfigChange();
        }
    }

    public static void onReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            com.mrshellad.dataagent.api.HttpServerManager.applyConfigChange();
        }
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue API_PORT = BUILDER
            .comment("Port for the Pi Agent HTTP/MCP server")
            .translation("data_agent.configuration.apiPort")
            .defineInRange("apiPort", 24482, 1024, 65535);

    public static final ModConfigSpec.BooleanValue ENABLE_API = BUILDER
            .comment("Whether to enable the Pi Agent HTTP API server")
            .translation("data_agent.configuration.enableApi")
            .define("enableApi", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}
