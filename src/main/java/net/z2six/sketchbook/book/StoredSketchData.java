package net.z2six.sketchbook.book;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record StoredSketchData(PageSketch sketch, Optional<SketchSourceImage> sourceImage, int colorMask) {
    private static final Codec<StoredSketchData> STRUCTURED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        PageSketch.CODEC.fieldOf("sketch").forGetter(StoredSketchData::sketch),
        SketchSourceImage.CODEC.optionalFieldOf("source").forGetter(StoredSketchData::sourceImage),
        SketchColorMask.CODEC.optionalFieldOf("color_mask", SketchColorMask.NONE).forGetter(StoredSketchData::colorMask)
    ).apply(instance, StoredSketchData::new));

    public static final Codec<StoredSketchData> CODEC = Codec.either(PageSketch.CODEC, STRUCTURED_CODEC)
        .xmap(either -> either.map(StoredSketchData::legacy, data -> data), data -> data.sourceImage.isPresent() || data.colorMask != SketchColorMask.NONE ? Either.right(data) : Either.left(data.sketch));

    public StoredSketchData {
        sourceImage = sourceImage == null ? Optional.empty() : sourceImage;
        colorMask = SketchColorMask.normalize(colorMask);
    }

    public static StoredSketchData legacy(PageSketch sketch) {
        return new StoredSketchData(sketch, Optional.empty(), SketchColorMask.NONE);
    }

    public static StoredSketchData captured(CapturedSketch capture) {
        return new StoredSketchData(capture.sketch(), Optional.of(capture.sourceImage()), SketchColorMask.NONE);
    }

    public boolean hasSourceImage() {
        return this.sourceImage.isPresent();
    }

    public StoredSketchData withSketch(PageSketch updatedSketch, int updatedColorMask) {
        return new StoredSketchData(updatedSketch, this.sourceImage, updatedColorMask);
    }
}
