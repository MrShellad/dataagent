package com.mrshellad.dataagent.core;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class ThreadScheduler {

    /**
     * Executes a supplier on the Minecraft main server thread and returns the result.
     * Blocks the calling (HTTP) thread until execution is complete.
     */
    public static <T> T call(Supplier<T> supplier) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // Server is not running yet or shutting down, fall back to direct call or throw
            return supplier.get();
        }
        
        if (server.isSameThread()) {
            // Already on server thread
            return supplier.get();
        }

        try {
            // Submit to main server thread and wait for result
            return server.submit(supplier::get).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException("Failed to execute query on server thread", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Execution interrupted on server thread", e);
        }
    }

    /**
     * Executes a runnable on the Minecraft main server thread without returning a result.
     */
    public static void run(Runnable runnable) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            runnable.run();
            return;
        }

        if (server.isSameThread()) {
            runnable.run();
            return;
        }

        server.execute(runnable);
    }
}
