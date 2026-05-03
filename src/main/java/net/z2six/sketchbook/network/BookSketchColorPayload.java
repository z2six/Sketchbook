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
import net.z2six.sketchbook.book.SketchColorMask;
import net.z2six.sketchbook.book.ServerBookSketches;
import net.z2six.sketchbook.compat.scholar.ScholarCommonCompat;
import net.z2six.sketchbook.image.SketchImageProcessor;

public record BookSketchColorPayload(BookSketchTarget target, int pageIndex, int colorMask) implements CustomPacketPayload {
    private static final Codec<BookSketchColorPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BookSketchTarget.CODEC.fieldOf("target").forGetter(BookSketchColorPayload::target),
        Codec.intRange(0, 99).fieldOf("page_index").forGetter(BookSketchColorPayload::pageIndex),
        SketchColorMask.CODEC.fieldOf("color_mask").forGetter(BookSketchColorPayload::colorMask)
    ).apply(instance, BookSketchColorPayload::new));
    public static final Type<BookSketchColorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "book_sketch_color"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BookSketchColorPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<BookSketchColorPayload> type() {
        return TYPE;
    }

    public static void handle(BookSketchColorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            if (!payload.target().isLectern()) {
                ItemStack book = serverPlayer.getItemInHand(payload.target().hand());
                if (!book.is(Items.WRITABLE_BOOK)) {
                    fail(serverPlayer, "message.sketchbook.color_failed_book_missing");
                    return;
                }

                if (BookSketches.getSketchReference(book, payload.pageIndex()).isEmpty()) {
                    fail(serverPlayer, "message.sketchbook.color_failed_missing_sketch");
                    return;
                }
            }

            int appliedColorMask = SketchColorMask.normalize(payload.colorMask()) & SketchbookItems.getAvailableColoredPencilMask(serverPlayer);

            var resolved = ServerBookSketches.recolor(serverPlayer, payload.target(), payload.pageIndex(), appliedColorMask, SketchImageProcessor.SketchStyle.V1);
            if (resolved.isEmpty()) {
                fail(serverPlayer, "message.sketchbook.color_failed_unavailable");
                return;
            }

            resolved.ifPresent(resolvedSketch -> {
                BookSketchSyncPayload syncPayload = new BookSketchSyncPayload(
                    payload.target(),
                    payload.pageIndex(),
                    java.util.Optional.of(resolvedSketch.referenceId()),
                    java.util.Optional.of(resolvedSketch.sketch()),
                    resolvedSketch.sourceImage(),
                    resolvedSketch.colorMask()
                );
                if (payload.target().isLectern()) {
                    ScholarCommonCompat.broadcastLecternUpdate(serverPlayer, payload.target(), syncPayload);
                } else {
                    serverPlayer.inventoryMenu.broadcastChanges();
                    serverPlayer.containerMenu.broadcastChanges();
                    PacketDistributor.sendToPlayer(serverPlayer, syncPayload);
                }
            });
        });
    }

    private static void fail(ServerPlayer player, String translationKey) {
        PacketDistributor.sendToPlayer(player, new SketchActionFeedbackPayload(translationKey));
    }
}
