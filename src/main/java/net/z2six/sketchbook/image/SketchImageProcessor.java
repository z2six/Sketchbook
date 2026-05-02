package net.z2six.sketchbook.image;

import net.minecraft.util.Mth;
import net.z2six.sketchbook.book.CapturedSketch;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.book.SketchColorMask;
import net.z2six.sketchbook.book.SketchSourceImage;
import net.z2six.sketchbook.item.PencilColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class SketchImageProcessor {
    private SketchImageProcessor() {
    }

    public static CapturedSketch createCapturedSketch(int width, int height, int[] argbPixels, SketchStyle style) {
        return new CapturedSketch(render(width, height, argbPixels, SketchColorMask.NONE, style), SketchSourceImage.fromArgb(width, height, argbPixels));
    }

    public static PageSketch render(int width, int height, int[] argbPixels, int colorMask, SketchStyle style) {
        SketchToneData toneData = prepareToneData(width, height, argbPixels);
        byte[] shades = style == SketchStyle.V2 ? stylizeSketchV2(toneData) : stylizeSketchV1(toneData);
        int normalizedColorMask = SketchColorMask.normalize(colorMask);
        if (normalizedColorMask == SketchColorMask.NONE) {
            return PageSketch.fromShades(width, height, shades);
        }

        return PageSketch.fromColorPng(width, height, createColorImage(width, height, argbPixels, shades, normalizedColorMask));
    }

    private static byte[] createColorImage(int width, int height, int[] argbPixels, byte[] shades, int colorMask) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];

        for (int index = 0; index < pixels.length; index++) {
            int argb = argbPixels[index];
            int sourceRed = argb >> 16 & 0xFF;
            int sourceGreen = argb >> 8 & 0xFF;
            int sourceBlue = argb & 0xFF;
            float shade = (shades[index] & 0xFF) / 255.0F;

            int paperRed = PageSketch.PAPER_RED;
            int paperGreen = PageSketch.PAPER_GREEN;
            int paperBlue = PageSketch.PAPER_BLUE;
            int inkRed = PageSketch.INK_RED;
            int inkGreen = PageSketch.INK_GREEN;
            int inkBlue = PageSketch.INK_BLUE;

            if (colorMask == SketchColorMask.ALL) {
                float sourceSaturation = saturation(sourceRed, sourceGreen, sourceBlue);
                int paperTintRed = lerp(PageSketch.PAPER_RED, sourceRed, 0.74F);
                int paperTintGreen = lerp(PageSketch.PAPER_GREEN, sourceGreen, 0.74F);
                int paperTintBlue = lerp(PageSketch.PAPER_BLUE, sourceBlue, 0.74F);
                int inkTintRed = lerp(PageSketch.INK_RED, sourceRed, 0.24F + sourceSaturation * 0.14F);
                int inkTintGreen = lerp(PageSketch.INK_GREEN, sourceGreen, 0.24F + sourceSaturation * 0.14F);
                int inkTintBlue = lerp(PageSketch.INK_BLUE, sourceBlue, 0.24F + sourceSaturation * 0.14F);
                paperRed = lerp(PageSketch.PAPER_RED, paperTintRed, 0.56F + sourceSaturation * 0.10F);
                paperGreen = lerp(PageSketch.PAPER_GREEN, paperTintGreen, 0.56F + sourceSaturation * 0.10F);
                paperBlue = lerp(PageSketch.PAPER_BLUE, paperTintBlue, 0.56F + sourceSaturation * 0.10F);
                inkRed = lerp(PageSketch.INK_RED, inkTintRed, 0.70F);
                inkGreen = lerp(PageSketch.INK_GREEN, inkTintGreen, 0.70F);
                inkBlue = lerp(PageSketch.INK_BLUE, inkTintBlue, 0.70F);
                shade = clamp01((float)Math.pow(shade, 1.14F));
            } else {
                float totalChromaWeight = 0.0F;
                float strongestChromaWeight = 0.0F;
                float weightedRed = 0.0F;
                float weightedGreen = 0.0F;
                float weightedBlue = 0.0F;

                for (PencilColor color : PencilColor.values()) {
                    if (!SketchColorMask.isSelected(colorMask, color) || !isChromatic(color)) {
                        continue;
                    }

                    float chromaWeight = pigmentAmount(color, sourceRed, sourceGreen, sourceBlue);
                    if (chromaWeight <= 0.0F) {
                        continue;
                    }

                    totalChromaWeight += chromaWeight;
                    strongestChromaWeight = Math.max(strongestChromaWeight, chromaWeight);
                    weightedRed += color.red() * chromaWeight;
                    weightedGreen += color.green() * chromaWeight;
                    weightedBlue += color.blue() * chromaWeight;
                }

                if (totalChromaWeight > 0.0F) {
                    float chromaStrength = clamp01(strongestChromaWeight * 0.95F + totalChromaWeight * 0.30F);
                    int mixRed = Math.round(weightedRed / totalChromaWeight);
                    int mixGreen = Math.round(weightedGreen / totalChromaWeight);
                    int mixBlue = Math.round(weightedBlue / totalChromaWeight);
                    int sourceTintRed = lerp(mixRed, sourceRed, 0.45F);
                    int sourceTintGreen = lerp(mixGreen, sourceGreen, 0.45F);
                    int sourceTintBlue = lerp(mixBlue, sourceBlue, 0.45F);
                    paperRed = lerp(PageSketch.PAPER_RED, sourceTintRed, chromaStrength * 0.82F);
                    paperGreen = lerp(PageSketch.PAPER_GREEN, sourceTintGreen, chromaStrength * 0.82F);
                    paperBlue = lerp(PageSketch.PAPER_BLUE, sourceTintBlue, chromaStrength * 0.82F);
                    inkRed = lerp(PageSketch.INK_RED, sourceTintRed, chromaStrength * 0.40F);
                    inkGreen = lerp(PageSketch.INK_GREEN, sourceTintGreen, chromaStrength * 0.40F);
                    inkBlue = lerp(PageSketch.INK_BLUE, sourceTintBlue, chromaStrength * 0.40F);
                }

                float whiteAmount = SketchColorMask.isSelected(colorMask, PencilColor.WHITE) ? pigmentAmount(PencilColor.WHITE, sourceRed, sourceGreen, sourceBlue) : 0.0F;
                if (whiteAmount > 0.0F) {
                    paperRed = lerp(paperRed, PencilColor.WHITE.red(), whiteAmount * 0.92F);
                    paperGreen = lerp(paperGreen, PencilColor.WHITE.green(), whiteAmount * 0.92F);
                    paperBlue = lerp(paperBlue, PencilColor.WHITE.blue(), whiteAmount * 0.92F);
                    inkRed = lerp(inkRed, PencilColor.LIGHT_GRAY.red(), whiteAmount * 0.18F);
                    inkGreen = lerp(inkGreen, PencilColor.LIGHT_GRAY.green(), whiteAmount * 0.18F);
                    inkBlue = lerp(inkBlue, PencilColor.LIGHT_GRAY.blue(), whiteAmount * 0.18F);
                }

                float lightGrayAmount = SketchColorMask.isSelected(colorMask, PencilColor.LIGHT_GRAY) ? pigmentAmount(PencilColor.LIGHT_GRAY, sourceRed, sourceGreen, sourceBlue) : 0.0F;
                if (lightGrayAmount > 0.0F) {
                    paperRed = lerp(paperRed, PencilColor.LIGHT_GRAY.red(), lightGrayAmount * 0.48F);
                    paperGreen = lerp(paperGreen, PencilColor.LIGHT_GRAY.green(), lightGrayAmount * 0.48F);
                    paperBlue = lerp(paperBlue, PencilColor.LIGHT_GRAY.blue(), lightGrayAmount * 0.48F);
                    inkRed = lerp(inkRed, PencilColor.LIGHT_GRAY.red(), lightGrayAmount * 0.28F);
                    inkGreen = lerp(inkGreen, PencilColor.LIGHT_GRAY.green(), lightGrayAmount * 0.28F);
                    inkBlue = lerp(inkBlue, PencilColor.LIGHT_GRAY.blue(), lightGrayAmount * 0.28F);
                }

                float grayAmount = SketchColorMask.isSelected(colorMask, PencilColor.GRAY) ? pigmentAmount(PencilColor.GRAY, sourceRed, sourceGreen, sourceBlue) : 0.0F;
                if (grayAmount > 0.0F) {
                    paperRed = lerp(paperRed, PencilColor.GRAY.red(), grayAmount * 0.34F);
                    paperGreen = lerp(paperGreen, PencilColor.GRAY.green(), grayAmount * 0.34F);
                    paperBlue = lerp(paperBlue, PencilColor.GRAY.blue(), grayAmount * 0.34F);
                    inkRed = lerp(inkRed, PencilColor.GRAY.red(), grayAmount * 0.55F);
                    inkGreen = lerp(inkGreen, PencilColor.GRAY.green(), grayAmount * 0.55F);
                    inkBlue = lerp(inkBlue, PencilColor.GRAY.blue(), grayAmount * 0.55F);
                }

                float blackAmount = SketchColorMask.isSelected(colorMask, PencilColor.BLACK) ? pigmentAmount(PencilColor.BLACK, sourceRed, sourceGreen, sourceBlue) : 0.0F;
                if (blackAmount > 0.0F) {
                    paperRed = lerp(paperRed, PencilColor.BLACK.red(), blackAmount * 0.08F);
                    paperGreen = lerp(paperGreen, PencilColor.BLACK.green(), blackAmount * 0.08F);
                    paperBlue = lerp(paperBlue, PencilColor.BLACK.blue(), blackAmount * 0.08F);
                    inkRed = lerp(inkRed, PencilColor.BLACK.red(), blackAmount * 0.88F);
                    inkGreen = lerp(inkGreen, PencilColor.BLACK.green(), blackAmount * 0.88F);
                    inkBlue = lerp(inkBlue, PencilColor.BLACK.blue(), blackAmount * 0.88F);
                }
            }

            int red = lerp(inkRed, paperRed, shade);
            int green = lerp(inkGreen, paperGreen, shade);
            int blue = lerp(inkBlue, paperBlue, shade);
            pixels[index] = 0xFF000000 | Mth.clamp(red, 0, 255) << 16 | Mth.clamp(green, 0, 255) << 8 | Mth.clamp(blue, 0, 255);
        }

        image.setRGB(0, 0, width, height, pixels, 0, width);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode colored sketch image", exception);
        }
    }

    private static float pigmentAmount(PencilColor color, int sourceRed, int sourceGreen, int sourceBlue) {
        float brightness = (sourceRed + sourceGreen + sourceBlue) / (255.0F * 3.0F);
        float saturation = saturation(sourceRed, sourceGreen, sourceBlue);
        float similarity = colorSimilarity(sourceRed, sourceGreen, sourceBlue, color.red(), color.green(), color.blue());
        return switch (color) {
            case WHITE -> {
                float highlight = smoothstep(0.60F, 1.0F, brightness);
                float neutral = 1.0F - saturation;
                yield emphasize(Math.max(highlight, similarity * 0.45F) * (0.55F + neutral * 0.45F), 2.0F);
            }
            case LIGHT_GRAY -> {
                float neutral = 1.0F - saturation;
                float tone = 1.0F - Math.min(1.0F, Math.abs(brightness - 0.74F) / 0.24F);
                yield emphasize(Math.max(similarity * 0.55F, neutral * tone), 1.8F);
            }
            case GRAY -> {
                float neutral = 1.0F - saturation;
                float tone = 1.0F - Math.min(1.0F, Math.abs(brightness - 0.50F) / 0.28F);
                yield emphasize(Math.max(similarity * 0.55F, neutral * tone), 1.8F);
            }
            case BLACK -> {
                float darkness = smoothstep(0.18F, 0.78F, 1.0F - brightness);
                yield emphasize(Math.max(similarity * 0.60F, darkness), 1.6F);
            }
            case GRAPHITE -> 0.0F;
            default -> emphasize(similarity, 4.0F) * (0.25F + saturation * 0.75F);
        };
    }

    private static boolean isChromatic(PencilColor color) {
        return switch (color) {
            case GRAPHITE, WHITE, LIGHT_GRAY, GRAY, BLACK -> false;
            default -> true;
        };
    }

    private static float emphasize(float amount, float exponent) {
        return (float)Math.pow(clamp01(amount), exponent);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float normalized = clamp01((value - edge0) / Math.max(0.0001F, edge1 - edge0));
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private static float colorSimilarity(int sourceRed, int sourceGreen, int sourceBlue, int targetRed, int targetGreen, int targetBlue) {
        float redDelta = (sourceRed - targetRed) / 255.0F;
        float greenDelta = (sourceGreen - targetGreen) / 255.0F;
        float blueDelta = (sourceBlue - targetBlue) / 255.0F;
        float distance = Mth.sqrt(redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta) / Mth.sqrt(3.0F);
        return Math.max(0.0F, 1.0F - distance);
    }

    private static float saturation(int red, int green, int blue) {
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        if (max == 0) {
            return 0.0F;
        }
        return (max - min) / (float)max;
    }

    private static int lerp(int start, int end, float amount) {
        return Math.round(start + (end - start) * amount);
    }

    private static SketchToneData prepareToneData(int width, int height, int[] argbPixels) {
        int size = width * height;
        int[] grayscale = new int[size];
        int[] histogram = new int[256];

        for (int index = 0; index < size; index++) {
            int argb = argbPixels[index];
            int red = argb >> 16 & 0xFF;
            int green = argb >> 8 & 0xFF;
            int blue = argb & 0xFF;
            int shade = Math.round(red * 0.299F + green * 0.587F + blue * 0.114F);
            grayscale[index] = shade;
            histogram[shade]++;
        }

        int low = percentile(histogram, size, 0.02F);
        int high = percentile(histogram, size, 0.98F);
        if (high <= low) {
            low = 0;
            high = 255;
        }

        int[] contrast = new int[size];
        int[] inverted = new int[size];
        for (int index = 0; index < size; index++) {
            int shade = Mth.clamp((grayscale[index] - low) * 255 / Math.max(1, high - low), 0, 255);
            contrast[index] = shade;
            inverted[index] = 255 - shade;
        }

        return new SketchToneData(width, height, contrast, inverted);
    }

    private static byte[] stylizeSketchV1(SketchToneData toneData) {
        int width = toneData.width();
        int height = toneData.height();
        int[] contrast = toneData.contrast();
        int[] blurredInverted = boxBlur(boxBlur(toneData.inverted(), width, height, 2), width, height, 2);
        byte[] shades = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int pencil = Math.min(255, contrast[index] * 255 / Math.max(16, 255 - blurredInverted[index]));
                int shade = Math.round(contrast[index] * 0.35F + pencil * 0.65F);

                int gx = sample(contrast, width, height, x + 1, y - 1) + 2 * sample(contrast, width, height, x + 1, y) + sample(contrast, width, height, x + 1, y + 1)
                    - sample(contrast, width, height, x - 1, y - 1) - 2 * sample(contrast, width, height, x - 1, y) - sample(contrast, width, height, x - 1, y + 1);
                int gy = sample(contrast, width, height, x - 1, y + 1) + 2 * sample(contrast, width, height, x, y + 1) + sample(contrast, width, height, x + 1, y + 1)
                    - sample(contrast, width, height, x - 1, y - 1) - 2 * sample(contrast, width, height, x, y - 1) - sample(contrast, width, height, x + 1, y - 1);
                float edge = Math.min(1.0F, (float)Math.sqrt(gx * gx + gy * gy) / 220.0F);
                shade -= Math.round(edge * 105.0F);

                float darkness = 1.0F - shade / 255.0F;
                if (darkness > 0.22F && Math.floorMod(x + y, 12) == 0) {
                    shade -= Math.round((darkness - 0.22F) * 28.0F);
                }
                if (darkness > 0.40F && Math.floorMod(x - y, 14) == 0) {
                    shade -= Math.round((darkness - 0.40F) * 38.0F);
                }

                shade += ((x * 13 + y * 17) & 7) - 3;
                shades[index] = (byte)Mth.clamp(shade, 0, 255);
            }
        }
        return shades;
    }

    private static byte[] stylizeSketchV2(SketchToneData toneData) {
        int width = toneData.width();
        int height = toneData.height();
        int[] contrast = toneData.contrast();
        int[] blurredInverted = boxBlur(boxBlur(toneData.inverted(), width, height, 2), width, height, 2);
        int[] tonal = boxBlur(contrast, width, height, 1);
        int[] structure = boxBlur(boxBlur(contrast, width, height, 3), width, height, 3);

        byte[] shades = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int pencil = Math.min(255, contrast[index] * 255 / Math.max(16, 255 - blurredInverted[index]));
                int shade = Math.round(tonal[index] * 0.22F + pencil * 0.78F);

                int gx = sample(structure, width, height, x + 1, y - 1) + 2 * sample(structure, width, height, x + 1, y) + sample(structure, width, height, x + 1, y + 1)
                    - sample(structure, width, height, x - 1, y - 1) - 2 * sample(structure, width, height, x - 1, y) - sample(structure, width, height, x - 1, y + 1);
                int gy = sample(structure, width, height, x - 1, y + 1) + 2 * sample(structure, width, height, x, y + 1) + sample(structure, width, height, x + 1, y + 1)
                    - sample(structure, width, height, x - 1, y - 1) - 2 * sample(structure, width, height, x, y - 1) - sample(structure, width, height, x + 1, y - 1);
                float edge = Math.min(1.0F, (float)Math.sqrt(gx * gx + gy * gy) / 170.0F);
                shade -= Math.round(edge * 110.0F);

                float darkness = 1.0F - tonal[index] / 255.0F;
                if (darkness > 0.28F && Math.floorMod(x + y, 13) == 0) {
                    shade -= Math.round((darkness - 0.28F) * 20.0F);
                }
                if (darkness > 0.48F && Math.floorMod(x - y, 16) == 0) {
                    shade -= Math.round((darkness - 0.48F) * 26.0F);
                }

                shade += ((x * 13 + y * 17) & 7) - 3;
                shades[index] = (byte)Mth.clamp(shade, 0, 255);
            }
        }
        return shades;
    }

    private static int percentile(int[] histogram, int total, float fraction) {
        int threshold = Math.max(0, Math.min(total - 1, Math.round(total * fraction)));
        int running = 0;
        for (int value = 0; value < histogram.length; value++) {
            running += histogram[value];
            if (running > threshold) {
                return value;
            }
        }
        return histogram.length - 1;
    }

    private static int[] boxBlur(int[] values, int width, int height, int radius) {
        int[] horizontal = new int[values.length];
        int[] output = new int[values.length];

        for (int y = 0; y < height; y++) {
            int row = y * width;
            int total = 0;
            for (int x = -radius; x <= radius; x++) {
                total += values[row + Mth.clamp(x, 0, width - 1)];
            }
            for (int x = 0; x < width; x++) {
                horizontal[row + x] = total / (radius * 2 + 1);
                int removeIndex = row + Mth.clamp(x - radius, 0, width - 1);
                int addIndex = row + Mth.clamp(x + radius + 1, 0, width - 1);
                total += values[addIndex] - values[removeIndex];
            }
        }

        for (int x = 0; x < width; x++) {
            int total = 0;
            for (int y = -radius; y <= radius; y++) {
                total += horizontal[Mth.clamp(y, 0, height - 1) * width + x];
            }
            for (int y = 0; y < height; y++) {
                output[y * width + x] = total / (radius * 2 + 1);
                int removeIndex = Mth.clamp(y - radius, 0, height - 1) * width + x;
                int addIndex = Mth.clamp(y + radius + 1, 0, height - 1) * width + x;
                total += horizontal[addIndex] - horizontal[removeIndex];
            }
        }

        return output;
    }

    private static int sample(int[] values, int width, int height, int x, int y) {
        return values[Mth.clamp(y, 0, height - 1) * width + Mth.clamp(x, 0, width - 1)];
    }

    private record SketchToneData(int width, int height, int[] contrast, int[] inverted) {
    }

    public enum SketchStyle {
        V1,
        V2
    }
}
