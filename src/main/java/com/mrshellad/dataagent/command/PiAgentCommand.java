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
                .then(Commands.literal("mcp")
                    .executes(context -> {
                        String projectPath = new java.io.File(".").getAbsolutePath().replace("\\", "/");
                        if (projectPath.endsWith("/.")) {
                            projectPath = projectPath.substring(0, projectPath.length() - 2);
                        }
                        String absoluteBridgePath = projectPath + "/.pi-agent/mcp-bridge.js";
                        int port = com.mrshellad.dataagent.Config.API_PORT.get();

                        String msg = "\n§6=================== [ Pi Agent MCP Setup ] ===================§r\n" +
                                "§b[Option A] For Cursor / VS Code (SSE Mode)§r\n" +
                                "  Add a new MCP server in Cursor settings:\n" +
                                "  - Name: §aPiAgent§r\n" +
                                "  - Type: §aSSE§r\n" +
                                "  - URL: §ahttp://localhost:" + port + "/mcp§r\n" +
                                "  (No bridge script or Node.js required!)\n\n" +
                                "§b[Option B] For Claude Desktop / Roo Code (Stdio Mode)§r\n" +
                                "  Add the following block to your mcp_config.json:\n" +
                                "§e{\n" +
                                "  \"mcpServers\": {\n" +
                                "    \"pi-agent-bridge\": {\n" +
                                "      \"command\": \"node\",\n" +
                                "      \"args\": [\n" +
                                "        \"" + absoluteBridgePath + "\"\n" +
                                "      ]\n" +
                                "    }\n" +
                                "  }\n" +
                                "}§r\n" +
                                "  (Requires Node.js installed locally)\n" +
                                "§6=============================================================§r";

                        context.getSource().sendSuccess(() -> Component.literal(msg), false);
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
