package net.z2six.sketchbook.client.network;

import net.z2six.sketchbook.client.ClientSceneMemoryCache;
import net.z2six.sketchbook.network.SceneMemoryListSyncPayload;

public final class SceneMemoryListSyncClientHandler {
    private SceneMemoryListSyncClientHandler() {
    }

    public static void handle(SceneMemoryListSyncPayload payload) {
        ClientSceneMemoryCache.setMemories(payload.memories());
    }
}
