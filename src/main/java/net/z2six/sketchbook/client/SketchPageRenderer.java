package net.z2six.sketchbook.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.z2six.sketchbook.book.PageSketch;

public final class SketchPageRenderer {
    private SketchPageRenderer() {
    }

    public static void render(GuiGraphics graphics, int left, int top, int width, int height, PageSketch sketch) {
        graphics.fill(left, top, left + width, top + height, 0xD8F0E6CF);
        graphics.renderOutline(left, top, width, height, 0x50302718);

        float scale = Math.min((width - 2.0F) / sketch.width(), (height - 1.0F) / sketch.height());
        scale = Math.min(scale, 1.0F);
        int renderWidth = Math.max(1, Math.round(sketch.width() * scale));
        int renderHeight = Math.max(1, Math.round(sketch.height() * scale));
        int imageLeft = left + (width - renderWidth) / 2;
        int imageTop = top + (height - renderHeight) / 2;
        ResourceLocation texture = PageSketchTextureCache.get(sketch);
        graphics.blit(texture, imageLeft, imageTop, renderWidth, renderHeight, 0.0F, 0.0F, sketch.width(), sketch.height(), sketch.width(), sketch.height());
        graphics.renderOutline(imageLeft - 1, imageTop - 1, renderWidth + 2, renderHeight + 2, 0x6643311F);
    }
}
