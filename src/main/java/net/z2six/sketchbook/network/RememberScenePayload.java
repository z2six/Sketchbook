package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.CapturedSketch;
import net.z2six.sketchbook.book.ServerSceneMemories;

import java.util.Optional;

public record RememberScenePayload(CapturedSketch memory) implements CustomPacketPayload {
    private static final Codec<RememberScenePayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        CapturedSketch.CODEC.fieldOf("memory").forGetter(RememberScenePayload::memory)
    ).apply(instance, RememberScenePayload::new));
    public static final Type<RememberScenePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "remember_scene"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RememberScenePayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<RememberScenePayload> type() {
        return TYPE;
    }

    public static void handle(RememberScenePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServerSceneMemories.remember(serverPlayer, payload.memory());
            }
        });
    }
}
