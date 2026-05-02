package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.ServerBookSketches;

public record BookSketchRequestPayload(BookSketchTarget target, int pageIndex) implements CustomPacketPayload {
    private static final Codec<BookSketchRequestPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BookSketchTarget.CODEC.fieldOf("target").forGetter(BookSketchRequestPayload::target),
        Codec.intRange(0, 99).fieldOf("page_index").forGetter(BookSketchRequestPayload::pageIndex)
    ).apply(instance, BookSketchRequestPayload::new));
    public static final Type<BookSketchRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "book_sketch_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BookSketchRequestPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<BookSketchRequestPayload> type() {
        return TYPE;
    }

    public static void handle(BookSketchRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            ServerBookSketches.resolve(serverPlayer, payload.target(), payload.pageIndex()).ifPresent(resolved -> {
                if (resolved.migratedBook()) {
                    serverPlayer.inventoryMenu.broadcastChanges();
                    serverPlayer.containerMenu.broadcastChanges();
                }
                PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new BookSketchSyncPayload(
                        payload.target(),
                        payload.pageIndex(),
                        java.util.Optional.of(resolved.referenceId()),
                        java.util.Optional.of(resolved.sketch()),
                        resolved.sourceAvailable(),
                        resolved.colorMask()
                    )
                );
            });
        });
    }
}
