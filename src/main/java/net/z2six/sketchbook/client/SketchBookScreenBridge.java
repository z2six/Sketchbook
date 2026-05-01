package net.z2six.sketchbook.client;

import net.minecraft.client.gui.screens.Screen;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.PageSketch;

public interface SketchBookScreenBridge {
    BookSketchTarget sketchbook$getTarget();

    boolean sketchbook$canCaptureSketch(int pageIndex);

    boolean sketchbook$hasSketch(int pageIndex);

    void sketchbook$applySketch(int pageIndex, PageSketch sketch);

    void sketchbook$removeSketch(int pageIndex);

    Screen sketchbook$asScreen();
}
