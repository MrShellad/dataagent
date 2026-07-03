package com.mrshellad.dataagent.core;

import com.mrshellad.dataagent.DataAgent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class McpExtractor {

    public static void extract() {
        Path targetDir = Paths.get("./.pi-agent");
        try {
            Files.createDirectories(targetDir);
            extractFile("/assets/data_agent/mcp-bridge.js", targetDir.resolve("mcp-bridge.js"));
            extractFile("/assets/data_agent/package.json", targetDir.resolve("package.json"));
            DataAgent.LOGGER.info("MCP Bridge files successfully extracted/verified in .pi-agent/ directory.");
        } catch (Exception e) {
            DataAgent.LOGGER.error("Failed to extract MCP Bridge files.", e);
        }
    }

    private static void extractFile(String resourcePath, Path targetFile) throws IOException {
        if (Files.exists(targetFile)) {
            return;
        }
        try (InputStream is = McpExtractor.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found in classpath: " + resourcePath);
            }
            Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
