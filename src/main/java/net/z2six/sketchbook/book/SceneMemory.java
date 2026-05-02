package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

public record SceneMemory(UUID memoryId, long createdGameTime, StoredSketchData storedSketch) {
    private static final Codec<StoredSceneMemory> STORED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(StoredSceneMemory::memoryId),
        Codec.LONG.fieldOf("created_game_time").forGetter(StoredSceneMemory::createdGameTime),
        Codec.STRING.optionalFieldOf("title").forGetter(StoredSceneMemory::legacyTitle),
        StoredSketchData.CODEC.fieldOf("sketch").forGetter(StoredSceneMemory::storedSketch)
    ).apply(instance, StoredSceneMemory::new));

    public static final Codec<SceneMemory> CODEC = STORED_CODEC.xmap(
        stored -> new SceneMemory(stored.memoryId(), stored.createdGameTime(), stored.storedSketch()),
        memory -> new StoredSceneMemory(memory.memoryId(), memory.createdGameTime(), Optional.empty(), memory.storedSketch())
    );

    private record StoredSceneMemory(UUID memoryId, long createdGameTime, Optional<String> legacyTitle, StoredSketchData storedSketch) {
    }
}
