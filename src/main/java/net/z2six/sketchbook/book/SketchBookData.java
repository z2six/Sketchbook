package net.z2six.sketchbook.book;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.util.LinkedHashMap;
import java.util.Map;

public record SketchBookData(Map<Integer, SketchPageEntry> pages) {
    private static final Codec<Integer> PAGE_KEY_CODEC = Codec.STRING.comapFlatMap(SketchBookData::parsePageIndex, Object::toString);
    private static final Codec<Map<Integer, SketchPageEntry>> PAGES_CODEC = ExtraCodecs.sizeLimitedMap(Codec.unboundedMap(PAGE_KEY_CODEC, SketchPageEntry.CODEC), 100);
    private static final Codec<Map<Integer, SketchPageEntry>> LEGACY_PAGES_CODEC = ExtraCodecs.sizeLimitedMap(
        Codec.unboundedMap(PAGE_KEY_CODEC, PageSketch.CODEC.xmap(SketchPageEntry::legacy, SketchPageEntry::requireInlineSketch)),
        100
    );
    private static final Codec<Map<Integer, SketchPageEntry>> PAGES_PERSISTENT_CODEC = Codec.either(PAGES_CODEC, LEGACY_PAGES_CODEC)
        .xmap(either -> either.map(map -> map, map -> map), Either::left);
    private static final Codec<Map<Integer, SketchPageEntry>> PAGES_NETWORK_CODEC = ExtraCodecs.sizeLimitedMap(Codec.unboundedMap(PAGE_KEY_CODEC, SketchPageEntry.NETWORK_CODEC), 100);

    public static final SketchBookData EMPTY = new SketchBookData(Map.of());
    public static final Codec<SketchBookData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        PAGES_PERSISTENT_CODEC.optionalFieldOf("pages", Map.of()).forGetter(SketchBookData::pages)
    ).apply(instance, SketchBookData::new));
    public static final Codec<SketchBookData> NETWORK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        PAGES_NETWORK_CODEC.optionalFieldOf("pages", Map.of()).forGetter(SketchBookData::pages)
    ).apply(instance, SketchBookData::new));

    public SketchBookData {
        if (pages.size() > 100) {
            throw new IllegalArgumentException("Too many sketch pages: " + pages.size());
        }

        LinkedHashMap<Integer, SketchPageEntry> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, SketchPageEntry> entry : pages.entrySet()) {
            int pageIndex = entry.getKey();
            if (pageIndex < 0 || pageIndex >= 100) {
                throw new IllegalArgumentException("Invalid page index " + pageIndex);
            }
            SketchPageEntry sketchEntry = entry.getValue();
            if (sketchEntry != null && sketchEntry.hasSketch()) {
                copy.put(pageIndex, sketchEntry);
            }
        }

        pages = Map.copyOf(copy);
    }

    public SketchPageEntry get(int pageIndex) {
        return this.pages.get(pageIndex);
    }

    public boolean hasSketch(int pageIndex) {
        return this.pages.containsKey(pageIndex);
    }

    public boolean isEmpty() {
        return this.pages.isEmpty();
    }

    public SketchBookData withEntry(int pageIndex, SketchPageEntry entry) {
        LinkedHashMap<Integer, SketchPageEntry> copy = new LinkedHashMap<>(this.pages);
        copy.put(pageIndex, entry);
        return new SketchBookData(copy);
    }

    public SketchBookData withoutSketch(int pageIndex) {
        if (!this.pages.containsKey(pageIndex)) {
            return this;
        }

        LinkedHashMap<Integer, SketchPageEntry> copy = new LinkedHashMap<>(this.pages);
        copy.remove(pageIndex);
        return copy.isEmpty() ? EMPTY : new SketchBookData(copy);
    }

    private static DataResult<Integer> parsePageIndex(String value) {
        try {
            int pageIndex = Integer.parseInt(value);
            if (pageIndex < 0 || pageIndex >= 100) {
                return DataResult.error(() -> "Page index out of range: " + value);
            }
            return DataResult.success(pageIndex);
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "Invalid page index: " + value);
        }
    }
}
