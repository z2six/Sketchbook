package net.z2six.sketchbook.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.z2six.sketchbook.book.PageSketch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

public final class PageSketchTextureCache {
    private static final Map<PageSketch, ResourceLocation> CACHE = new HashMap<>();

    private PageSketchTextureCache() {
    }

    public static ResourceLocation get(PageSketch sketch) {
        return CACHE.computeIfAbsent(sketch, PageSketchTextureCache::createTexture);
    }

    private static ResourceLocation createTexture(PageSketch sketch) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(sketch.pixels())) {
            NativeImage image = NativeImage.read(input);
            return Minecraft.getInstance().getTextureManager().register("sketchbook_page", new DynamicTexture(image));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to decode stored sketch image", exception);
        }
    }
}
