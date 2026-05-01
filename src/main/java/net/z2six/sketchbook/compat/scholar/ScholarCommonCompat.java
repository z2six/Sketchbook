package net.z2six.sketchbook.compat.scholar;

import io.github.mortuusars.scholar.menu.LecternSpreadMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.network.BookSketchSyncPayload;

import java.util.Optional;

public final class ScholarCommonCompat {
    private ScholarCommonCompat() {
    }

    public static void handleSketchUpdate(ServerPlayer serverPlayer, BookSketchTarget target, int pageIndex, Optional<PageSketch> sketch) {
        if (!ModList.get().isLoaded("scholar") || target.lecternPos().isEmpty()) {
            return;
        }

        if (!SketchbookItems.hasPencil(serverPlayer)) {
            return;
        }

        if (!(serverPlayer.containerMenu instanceof LecternSpreadMenu menu)) {
            return;
        }

        BlockPos lecternPos = target.lecternPos().get();
        if (!lecternPos.equals(menu.getLecternPos())) {
            return;
        }

        if (!(serverPlayer.serverLevel().getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern)) {
            return;
        }

        ItemStack book = lectern.getBook();
        if (!book.is(Items.WRITABLE_BOOK)) {
            return;
        }

        if (sketch.isPresent()) {
            BookSketches.applySketch(book, pageIndex, sketch.get());
        } else {
            BookSketches.removeSketch(book, pageIndex);
        }

        lectern.setChanged();
        serverPlayer.containerMenu.broadcastChanges();

        BookSketchSyncPayload syncPayload = new BookSketchSyncPayload(target, pageIndex, sketch);
        for (ServerPlayer otherPlayer : serverPlayer.serverLevel().players()) {
            if (otherPlayer.containerMenu instanceof LecternSpreadMenu otherMenu && lecternPos.equals(otherMenu.getLecternPos())) {
                otherPlayer.containerMenu.broadcastChanges();
                PacketDistributor.sendToPlayer(otherPlayer, syncPayload);
            }
        }
    }
}
