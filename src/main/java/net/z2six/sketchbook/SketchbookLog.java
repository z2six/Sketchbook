package net.z2six.sketchbook;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SketchbookLog {
    private static final int MAX_ONCE_KEYS = 2048;
    private static final Set<String> INFO_ONCE_KEYS = ConcurrentHashMap.newKeySet();

    public static final Logger LOGGER = LogUtils.getLogger();

    private SketchbookLog() {
    }

    public static void infoOnce(String key, String message, Object... args) {
        if (INFO_ONCE_KEYS.size() > MAX_ONCE_KEYS) {
            INFO_ONCE_KEYS.clear();
        }

        if (INFO_ONCE_KEYS.add(key)) {
            LOGGER.info(message, args);
        }
    }
}
