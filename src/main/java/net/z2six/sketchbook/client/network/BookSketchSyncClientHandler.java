package net.z2six.sketchbook.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.client.SketchBookScreenBridge;
import net.z2six.sketchbook.network.BookSketchSyncPayload;

public final class BookSketchSyncClientHandler {
    private BookSketchSyncClientHandler() {
    }

    public static void handle(BookSketchSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SketchBookScreenBridge bridge && bridge.sketchbook$getTarget().equals(payload.target())) {
            if (payload.sketch().isPresent()) {
                bridge.sketchbook$applySketch(payload.pageIndex(), payload.sketch().get());
            } else {
                bridge.sketchbook$removeSketch(payload.pageIndex());
            }
            return;
        }

        if (minecraft.player == null || payload.target().isLectern()) {
            return;
        }

        ItemStack book = minecraft.player.getItemInHand(payload.target().hand());
        if (!book.is(Items.WRITABLE_BOOK)) {
            return;
        }

        if (payload.sketch().isPresent()) {
            BookSketches.applySketch(book, payload.pageIndex(), payload.sketch().get());
        } else {
            BookSketches.removeSketch(book, payload.pageIndex());
        }
    }
}
