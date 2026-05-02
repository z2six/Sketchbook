package net.z2six.sketchbook.client.network;

import net.z2six.sketchbook.client.ClientSketchCache;
import net.z2six.sketchbook.client.ClientSketchReferenceRequestManager;
import net.z2six.sketchbook.network.SketchReferenceSyncPayload;

public final class SketchReferenceSyncClientHandler {
    private SketchReferenceSyncClientHandler() {
    }

    public static void handle(SketchReferenceSyncPayload payload) {
        ClientSketchCache.put(payload.referenceId(), payload.sketch(), payload.sourceAvailable(), payload.colorMask());
        ClientSketchReferenceRequestManager.clear(payload.referenceId());
    }
}
