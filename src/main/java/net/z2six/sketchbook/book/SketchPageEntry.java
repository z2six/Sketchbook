package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;

public record SketchPageEntry(Optional<UUID> referenceId, Optional<PageSketch> inlineSketch) {
    private static final Codec<StoredSketchPageEntry> STORED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.optionalFieldOf("ref").forGetter(StoredSketchPageEntry::referenceId),
        PageSketch.CODEC.optionalFieldOf("legacy_sketch").forGetter(StoredSketchPageEntry::inlineSketch)
    ).apply(instance, StoredSketchPageEntry::new));
    private static final Codec<NetworkSketchPageEntry> NETWORK_ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("ref").forGetter(NetworkSketchPageEntry::referenceId)
    ).apply(instance, NetworkSketchPageEntry::new));

    public static final Codec<SketchPageEntry> CODEC = STORED_CODEC.xmap(SketchPageEntry::fromStored, SketchPageEntry::toStored);
    public static final Codec<SketchPageEntry> NETWORK_CODEC = NETWORK_ENTRY_CODEC.xmap(
        entry -> new SketchPageEntry(Optional.of(entry.referenceId()), Optional.empty()),
        SketchPageEntry::toNetworkEntry
    );

    public SketchPageEntry {
        referenceId = referenceId.or(() -> inlineSketch.map(SketchPageEntry::derivedReferenceId));
    }

    public static SketchPageEntry reference(UUID referenceId) {
        return new SketchPageEntry(Optional.of(referenceId), Optional.empty());
    }

    public static SketchPageEntry legacy(PageSketch sketch) {
        return new SketchPageEntry(Optional.empty(), Optional.of(sketch));
    }

    public boolean hasSketch() {
        return this.referenceId.isPresent() || this.inlineSketch.isPresent();
    }

    public SketchPageEntry withReference(UUID referenceId) {
        return reference(referenceId);
    }

    public PageSketch requireInlineSketch() {
        return this.inlineSketch.orElseThrow(() -> new IllegalStateException("Sketch page entry does not carry inline sketch data"));
    }

    private static UUID derivedReferenceId(PageSketch sketch) {
        byte[] pixels = sketch.pixels();
        ByteBuffer buffer = ByteBuffer.allocate(12 + pixels.length);
        buffer.putInt(sketch.width());
        buffer.putInt(sketch.height());
        buffer.putInt(sketch.format().ordinal());
        buffer.put(pixels);
        return UUID.nameUUIDFromBytes(buffer.array());
    }

    private static SketchPageEntry fromStored(StoredSketchPageEntry entry) {
        return new SketchPageEntry(entry.referenceId(), entry.inlineSketch());
    }

    private StoredSketchPageEntry toStored() {
        return new StoredSketchPageEntry(this.referenceId, this.inlineSketch);
    }

    private NetworkSketchPageEntry toNetworkEntry() {
        return new NetworkSketchPageEntry(this.referenceId.orElseGet(() -> derivedReferenceId(this.requireInlineSketch())));
    }

    private record StoredSketchPageEntry(Optional<UUID> referenceId, Optional<PageSketch> inlineSketch) {
    }

    private record NetworkSketchPageEntry(UUID referenceId) {
    }
}
