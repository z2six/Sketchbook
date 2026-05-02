package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record CapturedSketch(PageSketch sketch, SketchSourceImage sourceImage) {
    public static final Codec<CapturedSketch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        PageSketch.NETWORK_CODEC.fieldOf("sketch").forGetter(CapturedSketch::sketch),
        SketchSourceImage.CODEC.fieldOf("source").forGetter(CapturedSketch::sourceImage)
    ).apply(instance, CapturedSketch::new));
}
