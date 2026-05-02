package net.z2six.sketchbook.book;

import net.minecraft.network.chat.Component;

public final class SceneMemoryTitles {
    private SceneMemoryTitles() {
    }

    public static Component component(long createdGameTime) {
        long day = createdGameTime / 24_000L + 1L;
        long timeOfDay = createdGameTime % 24_000L;
        int hour = (int)((timeOfDay / 1000L + 6L) % 24L);
        int minute = (int)((timeOfDay % 1000L) * 60L / 1000L);
        return Component.translatable("menu.sketchbook.memory_title", day, hour, minute);
    }
}
