package net.z2six.sketchbook.client;

import net.z2six.sketchbook.book.PageSketch;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClientSketchCache {
    private static final Map<UUID, CachedSketch> CACHE = new HashMap<>();

    private ClientSketchCache() {
    }

    public static Optional<PageSketch> get(UUID referenceId) {
        return Optional.ofNullable(CACHE.get(referenceId)).map(CachedSketch::sketch);
    }

    public static void put(UUID referenceId, PageSketch sketch, boolean sourceAvailable, int colorMask) {
        CACHE.put(referenceId, new CachedSketch(sketch, sourceAvailable, colorMask));
    }

    public static boolean hasSource(UUID referenceId) {
        return Optional.ofNullable(CACHE.get(referenceId)).map(CachedSketch::sourceAvailable).orElse(false);
    }

    public static int getColorMask(UUID referenceId) {
        return Optional.ofNullable(CACHE.get(referenceId)).map(CachedSketch::colorMask).orElse(0);
    }

    public static void updateColorMask(UUID referenceId, int colorMask) {
        CachedSketch cachedSketch = CACHE.get(referenceId);
        if (cachedSketch != null) {
            CACHE.put(referenceId, new CachedSketch(cachedSketch.sketch(), cachedSketch.sourceAvailable(), colorMask));
        }
    }

    public static void remove(UUID referenceId) {
        CACHE.remove(referenceId);
    }

    private record CachedSketch(PageSketch sketch, boolean sourceAvailable, int colorMask) {
    }
}
