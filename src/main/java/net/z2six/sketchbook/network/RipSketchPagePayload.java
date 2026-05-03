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
import net.z2six.sketchbook.SketchbookLog;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.ServerBookSketches;
import net.z2six.sketchbook.compat.scholar.ScholarCommonCompat;

import java.util.Optional;
import java.util.UUID;

public record RipSketchPagePayload(BookSketchTarget target, int pageIndex) implements CustomPacketPayload {
    private static final Codec<RipSketchPagePayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BookSketchTarget.CODEC.fieldOf("target").forGetter(RipSketchPagePayload::target),
        Codec.intRange(0, 99).fieldOf("page_index").forGetter(RipSketchPagePayload::pageIndex)
    ).apply(instance, RipSketchPagePayload::new));

    public static final Type<RipSketchPagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "rip_sketch_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RipSketchPagePayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<RipSketchPagePayload> type() {
        return TYPE;
    }

    public static void handle(RipSketchPagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            if (!payload.target().isLectern()) {
                ItemStack book = serverPlayer.getItemInHand(payload.target().hand());
                if (!book.is(Items.WRITABLE_BOOK)) {
                    fail(serverPlayer, payload, "message.sketchbook.rip_failed_book_missing", "Sketchbook rejected page rip for player {} page {} target {} because no writable book was held.");
                    return;
                }
            }

            Optional<UUID> referenceId = ServerBookSketches.ripOut(serverPlayer, payload.target(), payload.pageIndex());
            if (referenceId.isEmpty()) {
                fail(serverPlayer, payload, "message.sketchbook.rip_failed_missing_sketch", "Sketchbook rejected page rip for player {} page {} target {} because no sketch could be resolved.");
                return;
            }

            ItemStack tornSketch = SketchbookItems.createTornSketch(referenceId.get());
            if (!serverPlayer.getInventory().add(tornSketch)) {
                serverPlayer.drop(tornSketch, false);
            }

            if (payload.target().isLectern()) {
                ScholarCommonCompat.broadcastLecternUpdate(serverPlayer, payload.target(), BookSketchSyncPayload.remove(payload.target(), payload.pageIndex()));
            } else {
                serverPlayer.inventoryMenu.broadcastChanges();
                serverPlayer.containerMenu.broadcastChanges();
                PacketDistributor.sendToPlayer(serverPlayer, BookSketchSyncPayload.remove(payload.target(), payload.pageIndex()));
            }
            SketchbookLog.info(
                "Sketchbook ripped sketch ref {} from player {} page {} target {}.",
                referenceId.get(),
                serverPlayer.getGameProfile().getName(),
                payload.pageIndex(),
                payload.target()
            );
        });
    }

    private static void fail(ServerPlayer player, RipSketchPagePayload payload, String translationKey, String logMessage) {
        SketchbookLog.info(
            logMessage,
            player.getGameProfile().getName(),
            payload.pageIndex(),
            payload.target()
        );
        PacketDistributor.sendToPlayer(player, new SketchActionFeedbackPayload(translationKey));
    }
}
