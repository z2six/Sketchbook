package net.z2six.sketchbook.book;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.compat.scholar.ScholarCommonCompat;
import net.z2six.sketchbook.network.BookSketchSyncPayload;
import net.z2six.sketchbook.network.SceneMemoryListSyncPayload;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ServerSceneMemories {
    private ServerSceneMemories() {
    }

    public static void remember(ServerPlayer player, CapturedSketch capture) {
        SceneMemorySavedData.get(player.getServer()).addMemory(player.getUUID(), player.serverLevel().getGameTime(), capture);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        List<SceneMemorySummary> summaries = SceneMemorySavedData.get(player.getServer()).getSummaries(player.getUUID(), player.serverLevel().getGameTime());
        PacketDistributor.sendToPlayer(player, new SceneMemoryListSyncPayload(summaries));
    }

    public static Optional<UUID> applyMemoryToBook(ServerPlayer player, BookSketchTarget target, int pageIndex, UUID memoryId) {
        if (!SketchbookItems.hasPencil(player)) {
            return Optional.empty();
        }

        Optional<SceneMemory> memory = SceneMemorySavedData.get(player.getServer()).getMemory(player.getUUID(), memoryId, player.serverLevel().getGameTime());
        if (memory.isEmpty()) {
            return Optional.empty();
        }

        ItemStack book = target.isLectern() ? ScholarCommonCompat.getLecternBook(player, target) : player.getItemInHand(target.hand());
        if (!book.is(Items.WRITABLE_BOOK)) {
            return Optional.empty();
        }

        String pageText = BookSketches.getPageText(book, pageIndex);
        if (BookSketches.hasSketch(book, pageIndex) || !BookSketches.canSketchOnText(pageText)) {
            return Optional.empty();
        }

        StoredSketchData storedSketch = memory.get().storedSketch();
        UUID referenceId = UUID.randomUUID();
        SketchStorageSavedData.get(player.getServer()).put(referenceId, storedSketch);
        BookSketches.applyReference(book, pageIndex, referenceId);

        BookSketchSyncPayload payload = new BookSketchSyncPayload(target, pageIndex, Optional.of(referenceId), Optional.of(storedSketch.sketch()), storedSketch.hasSourceImage(), storedSketch.colorMask());
        if (target.isLectern()) {
            ScholarCommonCompat.broadcastLecternUpdate(player, target, payload);
        } else {
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            PacketDistributor.sendToPlayer(player, payload);
        }

        return Optional.of(referenceId);
    }
}
