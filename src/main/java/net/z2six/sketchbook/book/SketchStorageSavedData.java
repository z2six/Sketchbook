package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SketchStorageSavedData extends SavedData {
    private static final Codec<Map<UUID, StoredSketchData>> SKETCHES_CODEC = ExtraCodecs.sizeLimitedMap(
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, StoredSketchData.CODEC),
        100_000
    );
    private static final Codec<Packed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        SKETCHES_CODEC.optionalFieldOf("sketches", Map.of()).forGetter(Packed::sketches)
    ).apply(instance, Packed::new));

    private static final String NAME = "sketchbook_sketch_storage";

    private final Map<UUID, StoredSketchData> sketches;

    public SketchStorageSavedData() {
        this(Map.of());
    }

    private SketchStorageSavedData(Packed packed) {
        this(packed.sketches());
    }

    public SketchStorageSavedData(Map<UUID, StoredSketchData> sketches) {
        this.sketches = new LinkedHashMap<>(sketches);
    }

    public static SketchStorageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SketchStorageSavedData::new, (tag, provider) -> load(tag)), NAME);
    }

    public Optional<PageSketch> get(UUID referenceId) {
        return this.getData(referenceId).map(StoredSketchData::sketch);
    }

    public Optional<StoredSketchData> getData(UUID referenceId) {
        return Optional.ofNullable(this.sketches.get(referenceId));
    }

    public void put(UUID referenceId, PageSketch sketch) {
        this.put(referenceId, StoredSketchData.legacy(sketch));
    }

    public void put(UUID referenceId, StoredSketchData sketchData) {
        StoredSketchData previous = this.sketches.put(referenceId, sketchData);
        if (!sketchData.equals(previous)) {
            this.setDirty();
        }
    }

    public void updateSketch(UUID referenceId, PageSketch sketch) {
        StoredSketchData current = this.sketches.get(referenceId);
        if (current == null) {
            this.put(referenceId, sketch);
            return;
        }

        StoredSketchData updated = current.withSketch(sketch, current.colorMask());
        if (!updated.equals(current)) {
            this.sketches.put(referenceId, updated);
            this.setDirty();
        }
    }

    public boolean contains(UUID referenceId) {
        return this.sketches.containsKey(referenceId);
    }

    private Packed pack() {
        return new Packed(Map.copyOf(this.sketches));
    }

    private static SketchStorageSavedData load(CompoundTag tag) {
        Packed packed = CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).getOrThrow(IllegalStateException::new);
        return new SketchStorageSavedData(packed);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return (CompoundTag)CODEC.encodeStart(NbtOps.INSTANCE, this.pack()).getOrThrow(IllegalStateException::new);
    }

    private record Packed(Map<UUID, StoredSketchData> sketches) {
    }
}
