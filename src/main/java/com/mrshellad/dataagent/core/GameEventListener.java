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
