package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record SceneMemorySummary(UUID memoryId, long createdGameTime, PageSketch previewSketch) {
    public static final Codec<SceneMemorySummary> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(SceneMemorySummary::memoryId),
        Codec.LONG.fieldOf("created_game_time").forGetter(SceneMemorySummary::createdGameTime),
        PageSketch.NETWORK_CODEC.fieldOf("preview").forGetter(SceneMemorySummary::previewSketch)
    ).apply(instance, SceneMemorySummary::new));
}
