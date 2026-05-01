package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Arrays;
import java.nio.ByteBuffer;

public record PageSketch(int width, int height, byte[] pixels) {
    public static final int MAX_WIDTH = 450;
    public static final int MAX_HEIGHT = 510;
    private static final Codec<byte[]> PIXELS_CODEC = Codec.BYTE_BUFFER.xmap(PageSketch::toByteArray, ByteBuffer::wrap);
    public static final Codec<PageSketch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(1, MAX_WIDTH).fieldOf("width").forGetter(PageSketch::width),
        Codec.intRange(1, MAX_HEIGHT).fieldOf("height").forGetter(PageSketch::height),
        PIXELS_CODEC.fieldOf("pixels").forGetter(PageSketch::pixels)
    ).apply(instance, PageSketch::new));

    public PageSketch {
        pixels = pixels.clone();
        if (pixels.length == 0) {
            throw new IllegalArgumentException("Sketch image bytes cannot be empty");
        }
    }

    @Override
    public byte[] pixels() {
        return this.pixels.clone();
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PageSketch pageSketch)) {
            return false;
        }
        return this.width == pageSketch.width && this.height == pageSketch.height && Arrays.equals(this.pixels, pageSketch.pixels);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(this.width);
        result = 31 * result + Integer.hashCode(this.height);
        result = 31 * result + Arrays.hashCode(this.pixels);
        return result;
    }
}
