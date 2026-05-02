package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.saveddata.SavedData;
import net.z2six.sketchbook.SketchbookConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SceneMemorySavedData extends SavedData {
    private static final Codec<List<SceneMemory>> MEMORY_LIST_CODEC = Codec.list(SceneMemory.CODEC);
    private static final Codec<Map<UUID, List<SceneMemory>>> MEMORIES_CODEC = ExtraCodecs.sizeLimitedMap(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, MEMORY_LIST_CODEC),
        10_000
    );
    private static final Codec<Packed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MEMORIES_CODEC.optionalFieldOf("memories", Map.of()).forGetter(Packed::memories)
    ).apply(instance, Packed::new));
    private static final String NAME = "sketchbook_scene_memories";

    private final Map<UUID, List<SceneMemory>> memoriesByPlayer;

    public SceneMemorySavedData() {
        this(Map.of());
    }

    private SceneMemorySavedData(Packed packed) {
        this(packed.memories());
    }

    public SceneMemorySavedData(Map<UUID, List<SceneMemory>> memoriesByPlayer) {
        this.memoriesByPlayer = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<SceneMemory>> entry : memoriesByPlayer.entrySet()) {
            this.memoriesByPlayer.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    public static SceneMemorySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SceneMemorySavedData::new, (tag, provider) -> load(tag)), NAME);
    }

    public SceneMemory addMemory(UUID playerId, long currentGameTime, CapturedSketch capture) {
        this.prune(playerId, currentGameTime);
        List<SceneMemory> memories = this.memoriesByPlayer.computeIfAbsent(playerId, ignored -> new ArrayList<>());

        SceneMemory memory = new SceneMemory(UUID.randomUUID(), currentGameTime, StoredSketchData.captured(capture));
        memories.add(memory);
        memories.sort(Comparator.comparingLong(SceneMemory::createdGameTime));

        int maxMemories = SketchbookConfig.maxMemoriesPerPlayer();
        while (memories.size() > maxMemories) {
            memories.remove(0);
        }

        this.setDirty();
        return memory;
    }

    public List<SceneMemory> getMemories(UUID playerId, long currentGameTime) {
        this.prune(playerId, currentGameTime);
        List<SceneMemory> memories = this.memoriesByPlayer.get(playerId);
        if (memories == null) {
            return List.of();
        }
        return List.copyOf(memories);
    }

    public Optional<SceneMemory> getMemory(UUID playerId, UUID memoryId, long currentGameTime) {
        this.prune(playerId, currentGameTime);
        return this.memoriesByPlayer.getOrDefault(playerId, List.of()).stream().filter(memory -> memory.memoryId().equals(memoryId)).findFirst();
    }

    public List<SceneMemorySummary> getSummaries(UUID playerId, long currentGameTime) {
        List<SceneMemory> memories = this.getMemories(playerId, currentGameTime);
        List<SceneMemorySummary> summaries = new ArrayList<>(memories.size());
        for (int index = memories.size() - 1; index >= 0; index--) {
            SceneMemory memory = memories.get(index);
            summaries.add(new SceneMemorySummary(memory.memoryId(), memory.createdGameTime(), memory.storedSketch().sketch()));
        }
        return summaries;
    }

    private void prune(UUID playerId, long currentGameTime) {
        List<SceneMemory> memories = this.memoriesByPlayer.get(playerId);
        if (memories == null || memories.isEmpty()) {
            return;
        }

        long minGameTime = currentGameTime - SketchbookConfig.memoryRetentionTicks();
        boolean changed = memories.removeIf(memory -> memory.createdGameTime() < minGameTime);

        int maxMemories = SketchbookConfig.maxMemoriesPerPlayer();
        while (memories.size() > maxMemories) {
            memories.remove(0);
            changed = true;
        }

        if (memories.isEmpty()) {
            this.memoriesByPlayer.remove(playerId);
            changed = true;
        }

        if (changed) {
            this.setDirty();
        }
    }

    private Packed pack() {
        Map<UUID, List<SceneMemory>> packed = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<SceneMemory>> entry : this.memoriesByPlayer.entrySet()) {
            packed.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new Packed(Map.copyOf(packed));
    }

    private static SceneMemorySavedData load(CompoundTag tag) {
        Packed packed = CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).getOrThrow(IllegalStateException::new);
        return new SceneMemorySavedData(packed);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return (CompoundTag)CODEC.encodeStart(NbtOps.INSTANCE, this.pack()).getOrThrow(IllegalStateException::new);
    }
    private record Packed(Map<UUID, List<SceneMemory>> memories) {
    }
}
