package net.z2six.sketchbook.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.z2six.sketchbook.SketchbookItems;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.network.BookSketchPayload;

import java.io.IOException;
import java.io.UncheckedIOException;

@EventBusSubscriber(modid = Sketchbook.MODID, value = Dist.CLIENT)
public final class SketchCaptureController {
    // Flip this back to V1 if the new outline suppression pass is not desirable.
    private static final SketchStyle ACTIVE_SKETCH_STYLE = SketchStyle.V1;
    private static PendingCapture pendingCapture;

    private SketchCaptureController() {
    }

    public static void requestCapture(SketchBookScreenBridge bridge, int pageIndex) {
        if (pendingCapture != null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !SketchbookItems.hasPencil(minecraft.player)) {
            return;
        }

        pendingCapture = new PendingCapture(bridge.sketchbook$asScreen(), bridge.sketchbook$getTarget(), pageIndex, minecraft.options.hideGui);
    }

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        if (pendingCapture == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != pendingCapture.screen) {
            minecraft.options.hideGui = pendingCapture.wasHideGui;
            pendingCapture = null;
            return;
        }

        minecraft.options.hideGui = true;
        pendingCapture.hideGuiApplied = true;
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (pendingCapture != null && event.getScreen() == pendingCapture.screen) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        if (pendingCapture == null || !pendingCapture.hideGuiApplied) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!(pendingCapture.screen instanceof SketchBookScreenBridge bridge) || minecraft.screen != pendingCapture.screen) {
            minecraft.options.hideGui = pendingCapture.wasHideGui;
            pendingCapture = null;
            return;
        }

        NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
        PageSketch sketch;
        try {
            sketch = createSketch(image);
        } finally {
            image.close();
            minecraft.options.hideGui = pendingCapture.wasHideGui;
        }

        bridge.sketchbook$applySketch(pendingCapture.pageIndex, sketch);
        PacketDistributor.sendToServer(new BookSketchPayload(pendingCapture.target, pendingCapture.pageIndex, sketch));
        minecraft.player.displayClientMessage(Component.translatable("message.sketchbook.capture_success"), true);
        pendingCapture = null;
    }

    private static PageSketch createSketch(NativeImage image) {
        int width = PageSketch.MAX_WIDTH;
        int height = PageSketch.MAX_HEIGHT;
        float targetAspect = (float)width / height;
        float sourceAspect = (float)image.getWidth() / image.getHeight();

        int sampleWidth;
        int sampleHeight;
        int startX;
        int startY;
        if (sourceAspect > targetAspect) {
            sampleHeight = image.getHeight();
            sampleWidth = Math.max(1, Math.round(sampleHeight * targetAspect));
            startX = (image.getWidth() - sampleWidth) / 2;
            startY = 0;
        } else {
            sampleWidth = image.getWidth();
            sampleHeight = Math.max(1, Math.round(sampleWidth / targetAspect));
            startX = 0;
            startY = (image.getHeight() - sampleHeight) / 2;
        }

        NativeImage scaled = new NativeImage(width, height, false);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    float sampleX = startX + ((x + 0.5F) / width) * sampleWidth;
                    float sampleY = startY + ((y + 0.5F) / height) * sampleHeight;
                    int color = image.getPixelRGBA(Mth.clamp((int)sampleX, 0, image.getWidth() - 1), Mth.clamp((int)sampleY, 0, image.getHeight() - 1));
                    scaled.setPixelRGBA(x, y, color);
                }
            }

            NativeImage stylized = stylizeSketch(scaled);
            try {
                return new PageSketch(width, height, stylized.asByteArray());
            } finally {
                stylized.close();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to encode sketch image", exception);
        } finally {
            scaled.close();
        }
    }

    private static NativeImage stylizeSketch(NativeImage source) {
        SketchToneData toneData = prepareToneData(source);
        return switch (ACTIVE_SKETCH_STYLE) {
            case V1 -> stylizeSketchV1(toneData);
            case V2 -> stylizeSketchV2(toneData);
        };
    }

    private static SketchToneData prepareToneData(NativeImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int size = width * height;
        int[] grayscale = new int[size];
        int[] histogram = new int[256];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = source.getPixelRGBA(x, y);
                int red = color & 0xFF;
                int green = color >> 8 & 0xFF;
                int blue = color >> 16 & 0xFF;
                int shade = Math.round(red * 0.299F + green * 0.587F + blue * 0.114F);
                int index = y * width + x;
                grayscale[index] = shade;
                histogram[shade]++;
            }
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

    private static NativeImage stylizeSketchV1(SketchToneData toneData) {
        int width = toneData.width();
        int height = toneData.height();
        int[] contrast = toneData.contrast();
        int[] blurredInverted = boxBlur(boxBlur(toneData.inverted(), width, height, 2), width, height, 2);
        NativeImage stylized = new NativeImage(width, height, false);
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
                shade = Mth.clamp(shade, 0, 255);

                int inkRed = 58;
                int inkGreen = 52;
                int inkBlue = 46;
                int paperRed = 243;
                int paperGreen = 238;
                int paperBlue = 229;
                int outRed = lerpChannel(inkRed, paperRed, shade);
                int outGreen = lerpChannel(inkGreen, paperGreen, shade);
                int outBlue = lerpChannel(inkBlue, paperBlue, shade);
                int color = 0xFF000000 | outBlue << 16 | outGreen << 8 | outRed;
                stylized.setPixelRGBA(x, y, color);
            }
        }

        return stylized;
    }

    private static NativeImage stylizeSketchV2(SketchToneData toneData) {
        int width = toneData.width();
        int height = toneData.height();
        int[] contrast = toneData.contrast();
        int[] blurredInverted = boxBlur(boxBlur(toneData.inverted(), width, height, 2), width, height, 2);
        int[] tonal = boxBlur(contrast, width, height, 1);
        int[] structure = boxBlur(boxBlur(contrast, width, height, 3), width, height, 3);

        NativeImage stylized = new NativeImage(width, height, false);
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
                shade = Mth.clamp(shade, 0, 255);

                int inkRed = 58;
                int inkGreen = 52;
                int inkBlue = 46;
                int paperRed = 243;
                int paperGreen = 238;
                int paperBlue = 229;
                int outRed = lerpChannel(inkRed, paperRed, shade);
                int outGreen = lerpChannel(inkGreen, paperGreen, shade);
                int outBlue = lerpChannel(inkBlue, paperBlue, shade);
                int color = 0xFF000000 | outBlue << 16 | outGreen << 8 | outRed;
                stylized.setPixelRGBA(x, y, color);
            }
        }

        return stylized;
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

    private static int lerpChannel(int dark, int light, int shade) {
        return dark + Math.round((light - dark) * (shade / 255.0F));
    }

    private record SketchToneData(int width, int height, int[] contrast, int[] inverted) {
    }

    private enum SketchStyle {
        V1,
        V2
    }

    private static final class PendingCapture {
        private final Screen screen;
        private final BookSketchTarget target;
        private final int pageIndex;
        private final boolean wasHideGui;
        private boolean hideGuiApplied;

        private PendingCapture(Screen screen, BookSketchTarget target, int pageIndex, boolean wasHideGui) {
            this.screen = screen;
            this.target = target;
            this.pageIndex = pageIndex;
            this.wasHideGui = wasHideGui;
        }
    }
}
