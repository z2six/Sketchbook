package net.z2six.sketchbook.client;

import net.minecraft.client.gui.screens.Screen;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.PageSketch;

import java.util.Optional;
import java.util.UUID;

public interface SketchBookScreenBridge {
    BookSketchTarget sketchbook$getTarget();

    boolean sketchbook$canCaptureSketch(int pageIndex);

    boolean sketchbook$hasSketch(int pageIndex);

    void sketchbook$applySketch(int pageIndex, PageSketch sketch);

    void sketchbook$setSketchReference(int pageIndex, UUID referenceId);

    Optional<UUID> sketchbook$getSketchReference(int pageIndex);

    void sketchbook$cacheSketch(UUID referenceId, PageSketch sketch, boolean sourceAvailable, int colorMask);

    void sketchbook$removeSketch(int pageIndex);

    Screen sketchbook$asScreen();
}
