package net.z2six.sketchbook.book;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public record PageSketch(int width, int height, StorageFormat format, byte[] pixels) {
    public static final int MAX_WIDTH = 450;
    public static final int MAX_HEIGHT = 510;
    public static final int INK_RED = 58;
    public static final int INK_GREEN = 52;
    public static final int INK_BLUE = 46;
    public static final int PAPER_RED = 243;
    public static final int PAPER_GREEN = 238;
    public static final int PAPER_BLUE = 229;

    private static final Codec<StorageFormat> FORMAT_CODEC = Codec.STRING.comapFlatMap(StorageFormat::byId, StorageFormat::id);
    private static final Codec<byte[]> PIXELS_CODEC = Codec.BYTE_BUFFER.xmap(PageSketch::toByteArray, ByteBuffer::wrap);

    private static final Codec<StoredPageSketch> STORED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(1, MAX_WIDTH).fieldOf("width").forGetter(StoredPageSketch::width),
        Codec.intRange(1, MAX_HEIGHT).fieldOf("height").forGetter(StoredPageSketch::height),
        FORMAT_CODEC.optionalFieldOf("format", StorageFormat.LEGACY_PNG).forGetter(StoredPageSketch::format),
        PIXELS_CODEC.fieldOf("pixels").forGetter(StoredPageSketch::pixels)
    ).apply(instance, StoredPageSketch::new));

    public static final Codec<PageSketch> CODEC = STORED_CODEC.xmap(PageSketch::fromStored, PageSketch::toStored);
    public static final Codec<PageSketch> NETWORK_CODEC = STORED_CODEC.xmap(PageSketch::fromStored, PageSketch::toNetworkStored);

    public PageSketch(int width, int height, byte[] pixels) {
        this(width, height, StorageFormat.LEGACY_PNG, pixels);
    }

    public PageSketch {
        pixels = pixels.clone();
        if (pixels.length == 0) {
            throw new IllegalArgumentException("Sketch image bytes cannot be empty");
        }
    }

    public static PageSketch fromShades(int width, int height, byte[] shades) {
        if (shades.length != width * height) {
            throw new IllegalArgumentException("Expected " + (width * height) + " sketch shades but got " + shades.length);
        }

        return new PageSketch(width, height, StorageFormat.PACKED_GRAYSCALE, compress(packShades(shades)));
    }

    public static PageSketch fromColorPng(int width, int height, byte[] pngBytes) {
        return new PageSketch(width, height, StorageFormat.COLOR_PNG, pngBytes);
    }

    @Override
    public byte[] pixels() {
        return this.pixels.clone();
    }

    public NativeImage toImage() {
        return switch (this.format) {
            case LEGACY_PNG -> readLegacyImage();
            case COLOR_PNG -> readLegacyImage();
            case PACKED_GRAYSCALE -> createPackedGrayscaleImage();
        };
    }

    public PageSketch compact() {
        if (this.format == StorageFormat.PACKED_GRAYSCALE || this.format == StorageFormat.COLOR_PNG) {
            return this;
        }

        return fromShades(this.width, this.height, extractShadesFromLegacyImage());
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    public static int colorFromShade(int shade) {
        int outRed = lerpChannel(INK_RED, PAPER_RED, shade);
        int outGreen = lerpChannel(INK_GREEN, PAPER_GREEN, shade);
        int outBlue = lerpChannel(INK_BLUE, PAPER_BLUE, shade);
        return 0xFF000000 | outBlue << 16 | outGreen << 8 | outRed;
    }

    private NativeImage readLegacyImage() {
        try (ByteArrayInputStream input = new ByteArrayInputStream(this.pixels)) {
            return NativeImage.read(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decode stored sketch PNG", exception);
        }
    }

    private NativeImage createPackedGrayscaleImage() {
        byte[] shades = unpackShades(decompress(this.pixels, packedShadeByteLength(this.width, this.height)), this.width * this.height);
        NativeImage image = new NativeImage(this.width, this.height, false);
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                int shade = shades[y * this.width + x] & 0xFF;
                image.setPixelRGBA(x, y, colorFromShade(shade));
            }
        }
        return image;
    }

    private byte[] extractShadesFromLegacyImage() {
        try (ByteArrayInputStream input = new ByteArrayInputStream(this.pixels)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalStateException("Failed to decode stored sketch PNG");
            }

            byte[] shades = new byte[this.width * this.height];
            for (int y = 0; y < this.height; y++) {
                for (int x = 0; x < this.width; x++) {
                    int argb = image.getRGB(x, y);
                    int red = argb >> 16 & 0xFF;
                    int green = argb >> 8 & 0xFF;
                    int blue = argb & 0xFF;
                    int color = 0xFF000000 | blue << 16 | green << 8 | red;
                    shades[y * this.width + x] = (byte)shadeFromTintedColor(color);
                }
            }
            return shades;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decode stored sketch PNG", exception);
        }
    }

    private static byte[] packShades(byte[] shades) {
        byte[] packed = new byte[(shades.length + 1) / 2];
        for (int index = 0; index < shades.length; index++) {
            int quantized = Math.round((shades[index] & 0xFF) / 17.0F);
            quantized = Math.max(0, Math.min(15, quantized));
            if ((index & 1) == 0) {
                packed[index / 2] = (byte)(quantized << 4);
            } else {
                packed[index / 2] |= (byte)quantized;
            }
        }
        return packed;
    }

    private static byte[] unpackShades(byte[] packed, int pixelCount) {
        byte[] shades = new byte[pixelCount];
        for (int index = 0; index < pixelCount; index++) {
            int packedValue = packed[index / 2] & 0xFF;
            int nibble = (index & 1) == 0 ? packedValue >> 4 : packedValue & 0x0F;
            shades[index] = (byte)(nibble * 17);
        }
        return shades;
    }

    private static byte[] compress(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(input);
        deflater.finish();

        byte[] buffer = new byte[8192];
        byte[] output = new byte[input.length + 64];
        int length = 0;
        try {
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                if (count <= 0) {
                    break;
                }
                if (length + count > output.length) {
                    output = Arrays.copyOf(output, Math.max(output.length * 2, length + count));
                }
                System.arraycopy(buffer, 0, output, length, count);
                length += count;
            }
        } finally {
            deflater.end();
        }
        return Arrays.copyOf(output, length);
    }

    private static byte[] decompress(byte[] input, int expectedLength) {
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        byte[] output = new byte[expectedLength];
        int offset = 0;

        try {
            while (!inflater.finished() && offset < expectedLength) {
                int count = inflater.inflate(output, offset, expectedLength - offset);
                if (count == 0) {
                    if (inflater.needsInput()) {
                        break;
                    }
                    throw new IllegalStateException("Sketch decompression stalled");
                }
                offset += count;
            }
        } catch (DataFormatException exception) {
            throw new IllegalStateException("Stored sketch data is corrupt", exception);
        } finally {
            inflater.end();
        }

        if (!inflater.finished() || offset != expectedLength) {
            throw new IllegalStateException("Expected " + expectedLength + " bytes from sketch decompression but got " + offset);
        }

        return output;
    }

    private static int packedShadeByteLength(int width, int height) {
        return (width * height + 1) / 2;
    }

    private static int shadeFromTintedColor(int color) {
        int red = color & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color >> 16 & 0xFF;
        int redShade = inverseLerpChannel(INK_RED, PAPER_RED, red);
        int greenShade = inverseLerpChannel(INK_GREEN, PAPER_GREEN, green);
        int blueShade = inverseLerpChannel(INK_BLUE, PAPER_BLUE, blue);
        return Math.max(0, Math.min(255, Math.round((redShade + greenShade + blueShade) / 3.0F)));
    }

    private static int lerpChannel(int dark, int light, int shade) {
        return dark + Math.round((light - dark) * (shade / 255.0F));
    }

    private static int inverseLerpChannel(int dark, int light, int value) {
        return Math.round((value - dark) * 255.0F / Math.max(1, light - dark));
    }

    private static PageSketch fromStored(StoredPageSketch stored) {
        return new PageSketch(stored.width(), stored.height(), stored.format(), stored.pixels());
    }

    private StoredPageSketch toStored() {
        return new StoredPageSketch(this.width, this.height, this.format, this.pixels);
    }

    private StoredPageSketch toNetworkStored() {
        PageSketch compact = this.compact();
        return new StoredPageSketch(compact.width(), compact.height(), compact.format(), compact.pixels());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PageSketch pageSketch)) {
            return false;
        }
        return this.width == pageSketch.width
            && this.height == pageSketch.height
            && this.format == pageSketch.format
            && Arrays.equals(this.pixels, pageSketch.pixels);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(this.width);
        result = 31 * result + Integer.hashCode(this.height);
        result = 31 * result + this.format.hashCode();
        result = 31 * result + Arrays.hashCode(this.pixels);
        return result;
    }

    private record StoredPageSketch(int width, int height, StorageFormat format, byte[] pixels) {
    }

    public enum StorageFormat {
        LEGACY_PNG("png"),
        COLOR_PNG("color_png"),
        PACKED_GRAYSCALE("packed_grayscale");

        private final String id;

        StorageFormat(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        private static DataResult<StorageFormat> byId(String id) {
            for (StorageFormat format : values()) {
                if (format.id.equals(id)) {
                    return DataResult.success(format);
                }
            }
            return DataResult.error(() -> "Unknown sketch storage format: " + id);
        }
    }
}
