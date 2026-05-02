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
import net.z2six.sketchbook.book.CapturedSketch;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.image.SketchImageProcessor;
import net.z2six.sketchbook.network.BookSketchPayload;

@EventBusSubscriber(modid = Sketchbook.MODID, value = Dist.CLIENT)
public final class SketchCaptureController {
    // Flip this back to V1 if the new outline suppression pass is not desirable.
    private static final SketchImageProcessor.SketchStyle ACTIVE_SKETCH_STYLE = SketchImageProcessor.SketchStyle.V1;
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
        CapturedSketch sketch;
        try {
            sketch = createSketch(image);
        } finally {
            image.close();
            minecraft.options.hideGui = pendingCapture.wasHideGui;
        }

        bridge.sketchbook$applySketch(pendingCapture.pageIndex, sketch.sketch());
        PacketDistributor.sendToServer(new BookSketchPayload(pendingCapture.target, pendingCapture.pageIndex, sketch));
        minecraft.player.displayClientMessage(Component.translatable("message.sketchbook.capture_success"), true);
        pendingCapture = null;
    }

    static CapturedSketch createSketch(NativeImage image) {
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

        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float sampleX = startX + ((x + 0.5F) / width) * sampleWidth;
                float sampleY = startY + ((y + 0.5F) / height) * sampleHeight;
                int nativeColor = image.getPixelRGBA(Mth.clamp((int)sampleX, 0, image.getWidth() - 1), Mth.clamp((int)sampleY, 0, image.getHeight() - 1));
                pixels[y * width + x] = nativeToArgb(nativeColor);
            }
        }
        return SketchImageProcessor.createCapturedSketch(width, height, pixels, ACTIVE_SKETCH_STYLE);
    }

    private static int nativeToArgb(int nativeColor) {
        int alpha = nativeColor >> 24 & 0xFF;
        int red = nativeColor & 0xFF;
        int green = nativeColor >> 8 & 0xFF;
        int blue = nativeColor >> 16 & 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
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
