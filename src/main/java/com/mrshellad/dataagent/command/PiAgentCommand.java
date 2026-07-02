package com.mrshellad.dataagent.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mrshellad.dataagent.api.HttpServerManager;
import com.mrshellad.dataagent.service.ExportService;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

public class PiAgentCommand {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("pi-agent")
                .then(Commands.literal("ping")
                    .executes(context -> {
                        boolean running = HttpServerManager.isRunning();
                        int port = com.mrshellad.dataagent.Config.API_PORT.get();
                        context.getSource().sendSuccess(() -> Component.literal(
                            "Pi Agent API is " + (running ? "ONLINE on port " + port : "OFFLINE")), false);
                        return 1;
                    })
                )
                .then(Commands.literal("export")
                    .executes(context -> {
                        try {
                            ExportService service = new ExportService();
                            service.exportData("./.pi-agent/dumps", List.of("item", "block", "recipe", "loot_table", "villager_trade", "tag"));
                            context.getSource().sendSuccess(() -> Component.literal("Data successfully exported to default directory (./.pi-agent/dumps)"), false);
                            return 1;
                        } catch (Exception e) {
                            context.getSource().sendFailure(Component.literal("Export failed: " + e.getMessage()));
                            return 0;
                        }
                    })
                    .then(Commands.argument("targetDir", StringArgumentType.greedyString())
                        .executes(context -> {
                            String targetDir = StringArgumentType.getString(context, "targetDir");
                            try {
                                ExportService service = new ExportService();
                                service.exportData(targetDir, List.of("item", "block", "recipe", "loot_table", "villager_trade", "tag"));
                                context.getSource().sendSuccess(() -> Component.literal("Data successfully exported to: " + targetDir), false);
                                return 1;
                            } catch (Exception e) {
                                context.getSource().sendFailure(Component.literal("Export failed: " + e.getMessage()));
                                return 0;
                            }
                        })
                    )
                )
        );
    }
}
