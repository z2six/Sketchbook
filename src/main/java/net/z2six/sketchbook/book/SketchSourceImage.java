package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public record SketchSourceImage(int width, int height, byte[] pngBytes) {
    private static final Codec<byte[]> BYTES_CODEC = Codec.BYTE_BUFFER.xmap(SketchSourceImage::toByteArray, ByteBuffer::wrap);
    public static final Codec<SketchSourceImage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(1, PageSketch.MAX_WIDTH).fieldOf("width").forGetter(SketchSourceImage::width),
        Codec.intRange(1, PageSketch.MAX_HEIGHT).fieldOf("height").forGetter(SketchSourceImage::height),
        BYTES_CODEC.fieldOf("png").forGetter(SketchSourceImage::pngBytes)
    ).apply(instance, SketchSourceImage::new));

    public SketchSourceImage {
        pngBytes = pngBytes.clone();
    }

    @Override
    public byte[] pngBytes() {
        return this.pngBytes.clone();
    }

    public static SketchSourceImage fromArgb(int width, int height, int[] argbPixels) {
        if (argbPixels.length != width * height) {
            throw new IllegalArgumentException("Expected " + (width * height) + " source pixels but got " + argbPixels.length);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argbPixels, 0, width);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return new SketchSourceImage(width, height, output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode sketch source image", exception);
        }
    }

    public int[] readArgb() {
        try (ByteArrayInputStream input = new ByteArrayInputStream(this.pngBytes)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalStateException("Failed to decode sketch source image");
            }

            if (image.getWidth() != this.width || image.getHeight() != this.height) {
                throw new IllegalStateException("Sketch source image dimensions do not match stored metadata");
            }
            return image.getRGB(0, 0, this.width, this.height, null, 0, this.width);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decode sketch source image", exception);
        }
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
