package net.z2six.sketchbook.book;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
            return Optional.empty();
        }

        SketchPageEntry entry = BookSketches.getEntry(book, pageIndex);
        if (entry == null) {
            return Optional.empty();
        }

        Optional<UUID> referenceId = entry.referenceId();
        if (referenceId.isEmpty()) {
            return Optional.empty();
        }

        SketchStorageSavedData storage = SketchStorageSavedData.get(player.getServer());
        Optional<StoredSketchData> stored = storage.getData(referenceId.get());
        if (stored.isPresent()) {
            return Optional.of(new ResolvedSketch(referenceId.get(), stored.get().sketch(), stored.get().hasSourceImage(), stored.get().colorMask(), false));
        }

        if (entry.inlineSketch().isPresent()) {
            PageSketch legacySketch = entry.inlineSketch().get();
            storage.put(referenceId.get(), StoredSketchData.legacy(legacySketch));
            migrateEntry(book, pageIndex, referenceId.get());
            return Optional.of(new ResolvedSketch(referenceId.get(), legacySketch, false, SketchColorMask.NONE, true));
        }

        return Optional.empty();
    }

    public static UUID storeNewSketch(ServerPlayer player, CapturedSketch sketch) {
        UUID referenceId = UUID.randomUUID();
        SketchStorageSavedData.get(player.getServer()).put(referenceId, StoredSketchData.captured(sketch));
        return referenceId;
    }

    public static Optional<ResolvedSketch> recolor(ServerPlayer player, BookSketchTarget target, int pageIndex, int colorMask, SketchImageProcessor.SketchStyle style) {
        ItemStack book = target.isLectern() ? ScholarCommonCompat.getLecternBook(player, target) : player.getItemInHand(target.hand());
        if (!book.is(Items.WRITABLE_BOOK)) {
            return Optional.empty();
        }

        SketchPageEntry entry = BookSketches.getEntry(book, pageIndex);
        if (entry == null || entry.referenceId().isEmpty()) {
            return Optional.empty();
        }

        UUID referenceId = entry.referenceId().get();
        SketchStorageSavedData storage = SketchStorageSavedData.get(player.getServer());
        StoredSketchData stored = storage.getData(referenceId).orElse(null);
        if (stored == null || stored.sourceImage().isEmpty()) {
            return Optional.empty();
        }

        SketchSourceImage sourceImage = stored.sourceImage().get();
        int normalizedColorMask = SketchColorMask.normalize(colorMask);
        PageSketch recolored = SketchImageProcessor.render(sourceImage.width(), sourceImage.height(), sourceImage.readArgb(), normalizedColorMask, style);
        storage.put(referenceId, stored.withSketch(recolored, normalizedColorMask));
        return Optional.of(new ResolvedSketch(referenceId, recolored, true, normalizedColorMask, false));
    }

    private static void migrateEntry(ItemStack book, int pageIndex, UUID referenceId) {
        BookSketches.applyReference(book, pageIndex, referenceId);
    }

    public record ResolvedSketch(UUID referenceId, PageSketch sketch, boolean sourceAvailable, int colorMask, boolean migratedBook) {
    }
}
