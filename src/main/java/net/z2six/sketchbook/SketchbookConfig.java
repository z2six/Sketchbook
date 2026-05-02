package net.z2six.sketchbook;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SketchbookConfig {
    private static final int DEFAULT_MEMORY_RETENTION_DAYS = 7;
    private static final int DEFAULT_MAX_MEMORIES_PER_PLAYER = 20;
    private static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    private static volatile int memoryRetentionDays = DEFAULT_MEMORY_RETENTION_DAYS;
    private static volatile int maxMemoriesPerPlayer = DEFAULT_MAX_MEMORIES_PER_PLAYER;

    static {
        var pair = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = pair.getLeft();
        SERVER_SPEC = pair.getRight();
    }

    private SketchbookConfig() {
    }

    public static int memoryRetentionDays() {
        return memoryRetentionDays;
    }

    public static long memoryRetentionTicks() {
        return (long)memoryRetentionDays * 24_000L;
    }

    public static int maxMemoriesPerPlayer() {
        return maxMemoriesPerPlayer;
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            bake();
        }
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            bake();
        }
    }

    private static void bake() {
        memoryRetentionDays = SERVER.memoryRetentionDays.get();
        maxMemoriesPerPlayer = SERVER.maxMemoriesPerPlayer.get();
    }

    private static final class Server {
        private final ModConfigSpec.IntValue memoryRetentionDays;
        private final ModConfigSpec.IntValue maxMemoriesPerPlayer;

        private Server(ModConfigSpec.Builder builder) {
            builder.push("memories");
            this.memoryRetentionDays = builder
                .comment("How many in-game days memories are kept before they expire.")
                .defineInRange("memoryRetentionDays", DEFAULT_MEMORY_RETENTION_DAYS, 1, 365);
            this.maxMemoriesPerPlayer = builder
                .comment("Maximum number of remembered scenes stored per player.")
                .defineInRange("maxMemoriesPerPlayer", DEFAULT_MAX_MEMORIES_PER_PLAYER, 1, 200);
            builder.pop();
        }
    }
}
