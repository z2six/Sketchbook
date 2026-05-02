package net.z2six.sketchbook.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.client.ClientSketchCache;
import net.z2six.sketchbook.client.ClientSketchRequestManager;
import net.z2six.sketchbook.client.SketchBookScreenBridge;
import net.z2six.sketchbook.network.BookSketchSyncPayload;

public final class BookSketchSyncClientHandler {
    private BookSketchSyncClientHandler() {
    }

    public static void handle(BookSketchSyncPayload payload) {
        payload.sketchId().ifPresent(referenceId -> payload.sketch().ifPresent(sketch -> ClientSketchCache.put(referenceId, sketch, payload.sourceAvailable(), payload.colorMask())));
        ClientSketchRequestManager.clear(payload.target(), payload.pageIndex());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SketchBookScreenBridge bridge && bridge.sketchbook$getTarget().equals(payload.target())) {
            if (payload.sketchId().isPresent()) {
                if (!bridge.sketchbook$getSketchReference(payload.pageIndex()).filter(payload.sketchId().get()::equals).isPresent()) {
                    bridge.sketchbook$setSketchReference(payload.pageIndex(), payload.sketchId().get());
                }
                payload.sketch().ifPresent(sketch -> bridge.sketchbook$cacheSketch(payload.sketchId().get(), sketch, payload.sourceAvailable(), payload.colorMask()));
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

        if (payload.sketchId().isPresent()) {
            BookSketches.applyReference(book, payload.pageIndex(), payload.sketchId().get());
        } else {
            BookSketches.removeSketch(book, payload.pageIndex());
        }
    }
}
