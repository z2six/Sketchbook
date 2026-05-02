package net.z2six.sketchbook.client;

import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.network.SketchReferenceRequestPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientSketchReferenceRequestManager {
    private static final long REQUEST_COOLDOWN_MILLIS = 1_000L;
    private static final Map<UUID, Long> LAST_REQUESTS = new HashMap<>();

    private ClientSketchReferenceRequestManager() {
    }

    public static void request(UUID referenceId) {
        long now = System.currentTimeMillis();
        Long lastRequest = LAST_REQUESTS.get(referenceId);
        if (lastRequest != null && now - lastRequest < REQUEST_COOLDOWN_MILLIS) {
            return;
        }

        LAST_REQUESTS.put(referenceId, now);
        PacketDistributor.sendToServer(new SketchReferenceRequestPayload(referenceId));
    }

    public static void clear(UUID referenceId) {
        LAST_REQUESTS.remove(referenceId);
    }
}
