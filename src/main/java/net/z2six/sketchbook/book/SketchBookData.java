package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.util.LinkedHashMap;
import java.util.Map;

public record SketchBookData(Map<Integer, PageSketch> pages) {
    private static final Codec<Integer> PAGE_KEY_CODEC = Codec.STRING.comapFlatMap(SketchBookData::parsePageIndex, Object::toString);
    private static final Codec<Map<Integer, PageSketch>> PAGES_CODEC = ExtraCodecs.sizeLimitedMap(Codec.unboundedMap(PAGE_KEY_CODEC, PageSketch.CODEC), 100);
    public static final SketchBookData EMPTY = new SketchBookData(Map.of());
    public static final Codec<SketchBookData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        PAGES_CODEC.optionalFieldOf("pages", Map.of()).forGetter(SketchBookData::pages)
    ).apply(instance, SketchBookData::new));

    public SketchBookData {
        if (pages.size() > 100) {
            throw new IllegalArgumentException("Too many sketch pages: " + pages.size());
        }

        LinkedHashMap<Integer, PageSketch> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, PageSketch> entry : pages.entrySet()) {
            int pageIndex = entry.getKey();
            if (pageIndex < 0 || pageIndex >= 100) {
                throw new IllegalArgumentException("Invalid page index " + pageIndex);
            }
            copy.put(pageIndex, entry.getValue());
        }

        pages = Map.copyOf(copy);
    }

    public PageSketch get(int pageIndex) {
        return this.pages.get(pageIndex);
    }

    public boolean hasSketch(int pageIndex) {
        return this.pages.containsKey(pageIndex);
    }

    public boolean isEmpty() {
        return this.pages.isEmpty();
    }

    public SketchBookData withSketch(int pageIndex, PageSketch sketch) {
        LinkedHashMap<Integer, PageSketch> copy = new LinkedHashMap<>(this.pages);
        copy.put(pageIndex, sketch);
        return new SketchBookData(copy);
    }

    public SketchBookData withoutSketch(int pageIndex) {
        if (!this.pages.containsKey(pageIndex)) {
            return this;
        }

        LinkedHashMap<Integer, PageSketch> copy = new LinkedHashMap<>(this.pages);
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
