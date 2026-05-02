package net.z2six.sketchbook.client;

import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.network.BookSketchRequestPayload;

import java.util.HashMap;
import java.util.Map;

public final class ClientSketchRequestManager {
    private static final long REQUEST_COOLDOWN_MILLIS = 1_000L;
    private static final Map<RequestKey, Long> LAST_REQUESTS = new HashMap<>();

    private ClientSketchRequestManager() {
    }

    public static void request(BookSketchTarget target, int pageIndex) {
        RequestKey key = new RequestKey(target, pageIndex);
        long now = System.currentTimeMillis();
        Long lastRequest = LAST_REQUESTS.get(key);
        if (lastRequest != null && now - lastRequest < REQUEST_COOLDOWN_MILLIS) {
            return;
        }

        LAST_REQUESTS.put(key, now);
        PacketDistributor.sendToServer(new BookSketchRequestPayload(target, pageIndex));
    }

    public static void clear(BookSketchTarget target, int pageIndex) {
        LAST_REQUESTS.remove(new RequestKey(target, pageIndex));
    }

    private record RequestKey(BookSketchTarget target, int pageIndex) {
    }
}
