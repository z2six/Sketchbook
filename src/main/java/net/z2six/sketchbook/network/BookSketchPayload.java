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
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.compat.scholar.ScholarCommonCompat;

import java.util.Optional;

public record BookSketchPayload(BookSketchTarget target, int pageIndex, Optional<PageSketch> sketch) implements CustomPacketPayload {
    private static final Codec<BookSketchPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BookSketchTarget.CODEC.fieldOf("target").forGetter(BookSketchPayload::target),
        Codec.intRange(0, 99).fieldOf("page_index").forGetter(BookSketchPayload::pageIndex),
        PageSketch.CODEC.optionalFieldOf("sketch").forGetter(BookSketchPayload::sketch)
    ).apply(instance, BookSketchPayload::new));
    public static final Type<BookSketchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "book_sketch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BookSketchPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public BookSketchPayload(BookSketchTarget target, int pageIndex, PageSketch sketch) {
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

            if (!SketchbookItems.hasPencil(serverPlayer)) {
                return;
            }

            if (payload.target().isLectern()) {
                ScholarCommonCompat.handleSketchUpdate(serverPlayer, payload.target(), payload.pageIndex(), payload.sketch());
                return;
            }

            ItemStack book = serverPlayer.getItemInHand(payload.target().hand());
            if (!book.is(Items.WRITABLE_BOOK)) {
                return;
            }

            applySketch(book, payload.pageIndex(), payload.sketch());
            serverPlayer.inventoryMenu.broadcastChanges();
            serverPlayer.containerMenu.broadcastChanges();
            PacketDistributor.sendToPlayer(serverPlayer, new BookSketchSyncPayload(payload.target(), payload.pageIndex(), payload.sketch()));
        });
    }

    private static void applySketch(ItemStack book, int pageIndex, Optional<PageSketch> sketch) {
        if (sketch.isPresent()) {
            BookSketches.applySketch(book, pageIndex, sketch.get());
        } else {
            BookSketches.removeSketch(book, pageIndex);
        }
    }
}
