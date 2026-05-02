package net.z2six.sketchbook.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.book.SceneMemorySummary;
import net.z2six.sketchbook.network.SceneMemoryListRequestPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ClientSceneMemoryCache {
    private static List<SceneMemorySummary> memories = List.of();
    private static boolean requested;

    private ClientSceneMemoryCache() {
    }

    public static List<SceneMemorySummary> getMemories() {
        return memories;
    }

    public static Optional<SceneMemorySummary> getMemory(UUID memoryId) {
        return memories.stream().filter(memory -> memory.memoryId().equals(memoryId)).findFirst();
    }

    public static void setMemories(List<SceneMemorySummary> memories) {
        ClientSceneMemoryCache.memories = List.copyOf(memories);
        requested = true;
    }

    public static void ensureRequested() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            memories = List.of();
            requested = false;
            return;
        }

        if (!requested) {
            requested = true;
            PacketDistributor.sendToServer(new SceneMemoryListRequestPayload());
        }
    }
}
