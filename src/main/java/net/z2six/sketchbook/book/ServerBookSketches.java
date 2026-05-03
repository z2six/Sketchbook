package net.z2six.sketchbook.book;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.z2six.sketchbook.SketchbookLog;
import net.z2six.sketchbook.compat.scholar.ScholarCommonCompat;
import net.z2six.sketchbook.image.SketchImageProcessor;

import java.util.Optional;
import java.util.UUID;

public final class ServerBookSketches {
    private ServerBookSketches() {
    }

    public static Optional<ResolvedSketch> resolve(ServerPlayer player, BookSketchTarget target, int pageIndex) {
        ItemStack book = target.isLectern() ? ScholarCommonCompat.getLecternBook(player, target) : player.getItemInHand(target.hand());
        if (!book.is(Items.WRITABLE_BOOK)) {
            SketchbookLog.infoOnce(
                "resolve-missing-book:" + player.getUUID() + ":" + pageIndex + ":" + target,
                "Sketchbook could not resolve sketch page {} for player {} target {} because no writable book was available.",
                pageIndex,
                player.getGameProfile().getName(),
                target
            );
            return Optional.empty();
        }

        SketchPageEntry entry = BookSketches.getEntry(book, pageIndex);
        if (entry == null) {
            if (" ".equals(BookSketches.getPageText(book, pageIndex))) {
                SketchbookLog.infoOnce(
                    "marker-without-entry:" + player.getUUID() + ":" + pageIndex + ":" + target,
                    "Sketchbook found a sketch marker without sketch entry for player {} page {} target {}.",
                    player.getGameProfile().getName(),
                    pageIndex,
                    target
                );
            }
            return Optional.empty();
        }

        Optional<UUID> referenceId = entry.referenceId();
        if (referenceId.isEmpty()) {
            SketchbookLog.infoOnce(
                "book-entry-missing-ref:" + player.serverLevel().dimension().location() + ":" + player.getUUID() + ":" + pageIndex,
                "Sketchbook could not resolve sketch page {} for player {} in {} because the book entry had no reference id.",
                pageIndex,
                player.getGameProfile().getName(),
                player.serverLevel().dimension().location()
            );
            return Optional.empty();
        }

        SketchStorageSavedData storage = SketchStorageSavedData.get(player.getServer());
        Optional<StoredSketchData> stored = storage.getData(referenceId.get());
        if (stored.isPresent()) {
            return Optional.of(new ResolvedSketch(referenceId.get(), stored.get().sketch(), stored.get().sourceImage(), stored.get().colorMask(), false));
        }

        if (entry.inlineSketch().isPresent()) {
            PageSketch legacySketch = entry.inlineSketch().get();
            storage.put(referenceId.get(), StoredSketchData.legacy(legacySketch));
            migrateEntry(book, pageIndex, referenceId.get());
            return Optional.of(new ResolvedSketch(referenceId.get(), legacySketch, Optional.empty(), SketchColorMask.NONE, true));
        }

        SketchbookLog.infoOnce(
            "missing-book-sketch-storage:" + referenceId.get(),
            "Sketchbook could not resolve sketch ref {} for player {} page {} in {} because the stored sketch data was missing.",
            referenceId.get(),
            player.getGameProfile().getName(),
            pageIndex,
            player.serverLevel().dimension().location()
        );
        return Optional.empty();
    }

    public static UUID storeNewSketch(ServerPlayer player, CapturedSketch sketch) {
        UUID referenceId = UUID.randomUUID();
        SketchStorageSavedData.get(player.getServer()).put(referenceId, StoredSketchData.captured(sketch));
        SketchbookLog.info(
            "Sketchbook stored new sketch ref {} for player {} in {}.",
            referenceId,
            player.getGameProfile().getName(),
            player.serverLevel().dimension().location()
        );
        return referenceId;
    }

    public static Optional<UUID> ripOut(ServerPlayer player, BookSketchTarget target, int pageIndex) {
        ItemStack book = target.isLectern() ? ScholarCommonCompat.getLecternBook(player, target) : player.getItemInHand(target.hand());
        if (!book.is(Items.WRITABLE_BOOK)) {
            SketchbookLog.info(
                "Sketchbook could not rip page {} for player {} target {} because no writable book was available.",
                pageIndex,
                player.getGameProfile().getName(),
                target
            );
            return Optional.empty();
        }

        ResolvedSketch resolved = resolve(player, target, pageIndex).orElse(null);
        if (resolved == null) {
            SketchbookLog.info(
                "Sketchbook could not rip page {} for player {} target {} because no sketch could be resolved.",
                pageIndex,
                player.getGameProfile().getName(),
                target
            );
            return Optional.empty();
        }

        BookSketches.removeSketch(book, pageIndex);
        return Optional.of(resolved.referenceId());
    }

    public static Optional<ResolvedSketch> recolor(ServerPlayer player, BookSketchTarget target, int pageIndex, int colorMask, SketchImageProcessor.SketchStyle style) {
        ItemStack book = target.isLectern() ? ScholarCommonCompat.getLecternBook(player, target) : player.getItemInHand(target.hand());
        if (!book.is(Items.WRITABLE_BOOK)) {
            SketchbookLog.info(
                "Sketchbook could not recolor page {} for player {} target {} because no writable book was available.",
                pageIndex,
                player.getGameProfile().getName(),
                target
            );
            return Optional.empty();
        }

        SketchPageEntry entry = BookSketches.getEntry(book, pageIndex);
        if (entry == null || entry.referenceId().isEmpty()) {
            if (" ".equals(BookSketches.getPageText(book, pageIndex))) {
                SketchbookLog.infoOnce(
                    "recolor-marker-without-ref:" + player.getUUID() + ":" + pageIndex + ":" + target,
                    "Sketchbook could not recolor page {} for player {} target {} because the page had a marker but no sketch reference.",
                    pageIndex,
                    player.getGameProfile().getName(),
                    target
                );
            }
            return Optional.empty();
        }

        UUID referenceId = entry.referenceId().get();
        SketchStorageSavedData storage = SketchStorageSavedData.get(player.getServer());
        StoredSketchData stored = storage.getData(referenceId).orElse(null);
        if (stored == null) {
            SketchbookLog.infoOnce(
                "missing-recolor-storage:" + referenceId,
                "Sketchbook could not recolor sketch ref {} for player {} page {} in {} because the stored sketch data was missing.",
                referenceId,
                player.getGameProfile().getName(),
                pageIndex,
                player.serverLevel().dimension().location()
            );
            return Optional.empty();
        }
        if (stored.sourceImage().isEmpty()) {
            SketchbookLog.infoOnce(
                "missing-recolor-source:" + referenceId,
                "Sketchbook could not recolor sketch ref {} for player {} page {} in {} because no source image was stored for that sketch.",
                referenceId,
                player.getGameProfile().getName(),
                pageIndex,
                player.serverLevel().dimension().location()
            );
            return Optional.empty();
        }

        SketchSourceImage sourceImage = stored.sourceImage().get();
        int normalizedColorMask = SketchColorMask.normalize(colorMask);
        PageSketch recolored = SketchImageProcessor.render(sourceImage.width(), sourceImage.height(), sourceImage.readArgb(), normalizedColorMask, style);
        storage.put(referenceId, stored.withSketch(recolored, normalizedColorMask));
        return Optional.of(new ResolvedSketch(referenceId, recolored, stored.sourceImage(), normalizedColorMask, false));
    }

    private static void migrateEntry(ItemStack book, int pageIndex, UUID referenceId) {
        BookSketches.applyReference(book, pageIndex, referenceId);
    }

    public record ResolvedSketch(UUID referenceId, PageSketch sketch, Optional<SketchSourceImage> sourceImage, int colorMask, boolean migratedBook) {
    }
}
