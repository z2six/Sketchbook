package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.ServerSceneMemories;

public record SceneMemoryListRequestPayload() implements CustomPacketPayload {
    private static final Codec<SceneMemoryListRequestPayload> CODEC = Codec.unit(new SceneMemoryListRequestPayload());
    public static final Type<SceneMemoryListRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "scene_memory_list_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SceneMemoryListRequestPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<SceneMemoryListRequestPayload> type() {
        return TYPE;
    }

    public static void handle(SceneMemoryListRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServerSceneMemories.sync(serverPlayer);
            }
        });
    }
}
