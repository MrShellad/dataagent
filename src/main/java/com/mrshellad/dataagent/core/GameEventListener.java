package com.mrshellad.dataagent.core;

import com.google.gson.JsonObject;
import com.mrshellad.dataagent.service.EventSubscriptionService;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public class GameEventListener {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide()) {
            JsonObject data = new JsonObject();
            data.addProperty("player", player.getName().getString());
            data.addProperty("uuid", player.getUUID().toString());
            EventSubscriptionService.broadcast("player_join", data);

            // Send notification to player chat
            int port = com.mrshellad.dataagent.Config.API_PORT.get();
            String welcomeMsg = "§6[Data Agent]§r Mod 正在后台运行中！\n" +
                                "👉 已开放 API / MCP 端口: §a" + port + "§r\n" +
                                "👉 请在游戏内输入指令 §e/pi-agent mcp§r 查看详细的 AI 连接指南。";
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(welcomeMsg));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide()) {
            JsonObject data = new JsonObject();
            data.addProperty("player", player.getName().getString());
            data.addProperty("uuid", player.getUUID().toString());
            EventSubscriptionService.broadcast("player_leave", data);
        }
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        JsonObject data = new JsonObject();
        data.addProperty("status", "synced");
        EventSubscriptionService.broadcast("recipes_updated", data);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        JsonObject data = new JsonObject();
        data.addProperty("status", "stopping");
        EventSubscriptionService.broadcast("server_stopping", data);
        EventSubscriptionService.shutdown();
    }
}
