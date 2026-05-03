package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.book.CapturedSketch;
import net.z2six.sketchbook.book.ServerBookSketches;
import net.z2six.sketchbook.compat.scholar.ScholarCommonCompat;

import java.util.Optional;
import java.util.UUID;

public record BookSketchPayload(BookSketchTarget target, int pageIndex, Optional<CapturedSketch> sketch) implements CustomPacketPayload {
    private static final Codec<BookSketchPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BookSketchTarget.CODEC.fieldOf("target").forGetter(BookSketchPayload::target),
        Codec.intRange(0, 99).fieldOf("page_index").forGetter(BookSketchPayload::pageIndex),
        CapturedSketch.CODEC.optionalFieldOf("sketch").forGetter(BookSketchPayload::sketch)
    ).apply(instance, BookSketchPayload::new));
    public static final Type<BookSketchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "book_sketch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BookSketchPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public BookSketchPayload(BookSketchTarget target, int pageIndex, CapturedSketch sketch) {
        this(target, pageIndex, Optional.of(sketch));
    }

    public static BookSketchPayload remove(BookSketchTarget target, int pageIndex) {
        return new BookSketchPayload(target, pageIndex, Optional.empty());
    }

    @Override
    public Type<BookSketchPayload> type() {
        return TYPE;
    }

    public static void handle(BookSketchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            if (payload.sketch().isPresent() && !SketchbookItems.hasPencil(serverPlayer)) {
                fail(serverPlayer, "message.sketchbook.sketch_failed_no_pencil");
                return;
            }

            if (payload.target().isLectern()) {
                if (!ScholarCommonCompat.handleSketchUpdate(serverPlayer, payload.target(), payload.pageIndex(), payload.sketch())) {
                    fail(serverPlayer, payload.sketch().isPresent() ? "message.sketchbook.sketch_failed_page_unavailable" : "message.sketchbook.sketch_failed_book_missing");
                }
                return;
            }

            ItemStack book = serverPlayer.getItemInHand(payload.target().hand());
            if (!book.is(Items.WRITABLE_BOOK)) {
                fail(serverPlayer, "message.sketchbook.sketch_failed_book_missing");
                return;
            }

            if (payload.sketch().isPresent()) {
                String pageText = BookSketches.getPageText(book, payload.pageIndex());
                if (BookSketches.hasSketch(book, payload.pageIndex()) || !BookSketches.canSketchOnText(pageText)) {
                    fail(serverPlayer, "message.sketchbook.sketch_failed_page_unavailable");
                    return;
                }

                UUID referenceId = ServerBookSketches.storeNewSketch(serverPlayer, payload.sketch().get());
                BookSketches.applyReference(book, payload.pageIndex(), referenceId);
                serverPlayer.inventoryMenu.broadcastChanges();
                serverPlayer.containerMenu.broadcastChanges();
                PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new BookSketchSyncPayload(payload.target(), payload.pageIndex(), Optional.of(referenceId), Optional.of(payload.sketch().get().sketch()), Optional.of(payload.sketch().get().sourceImage()), 0)
                );
            } else {
                BookSketches.removeSketch(book, payload.pageIndex());
                serverPlayer.inventoryMenu.broadcastChanges();
                serverPlayer.containerMenu.broadcastChanges();
                PacketDistributor.sendToPlayer(serverPlayer, BookSketchSyncPayload.remove(payload.target(), payload.pageIndex()));
            }
        });
    }

    private static void fail(ServerPlayer player, String translationKey) {
        PacketDistributor.sendToPlayer(player, new SketchActionFeedbackPayload(translationKey));
    }
}
