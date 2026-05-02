package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.StoredSketchData;
import net.z2six.sketchbook.book.SketchStorageSavedData;

import java.util.UUID;

public record SketchReferenceRequestPayload(UUID referenceId) implements CustomPacketPayload {
    private static final Codec<SketchReferenceRequestPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("reference_id").forGetter(SketchReferenceRequestPayload::referenceId)
    ).apply(instance, SketchReferenceRequestPayload::new));

    public static final Type<SketchReferenceRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "sketch_reference_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SketchReferenceRequestPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<SketchReferenceRequestPayload> type() {
        return TYPE;
    }

    public static void handle(SketchReferenceRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }

            StoredSketchData stored = SketchStorageSavedData.get(serverPlayer.getServer()).getData(payload.referenceId()).orElse(null);
            if (stored == null) {
                return;
            }

            PacketDistributor.sendToPlayer(
                serverPlayer,
                new SketchReferenceSyncPayload(payload.referenceId(), stored.sketch(), stored.hasSourceImage(), stored.colorMask())
            );
        });
    }
}
