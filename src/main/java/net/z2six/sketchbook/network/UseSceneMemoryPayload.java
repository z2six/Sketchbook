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
import net.z2six.sketchbook.SketchbookLog;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.ServerSceneMemories;

import java.util.UUID;

public record UseSceneMemoryPayload(BookSketchTarget target, int pageIndex, UUID memoryId) implements CustomPacketPayload {
    private static final Codec<UseSceneMemoryPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BookSketchTarget.CODEC.fieldOf("target").forGetter(UseSceneMemoryPayload::target),
        Codec.intRange(0, 99).fieldOf("page_index").forGetter(UseSceneMemoryPayload::pageIndex),
        UUIDUtil.STRING_CODEC.fieldOf("memory_id").forGetter(UseSceneMemoryPayload::memoryId)
    ).apply(instance, UseSceneMemoryPayload::new));
    public static final Type<UseSceneMemoryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "use_scene_memory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UseSceneMemoryPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<UseSceneMemoryPayload> type() {
        return TYPE;
    }

    public static void handle(UseSceneMemoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (ServerSceneMemories.applyMemoryToBook(serverPlayer, payload.target(), payload.pageIndex(), payload.memoryId()).isEmpty()) {
                    SketchbookLog.info(
                        "Sketchbook rejected memory application {} for player {} page {} target {}.",
                        payload.memoryId(),
                        serverPlayer.getGameProfile().getName(),
                        payload.pageIndex(),
                        payload.target()
                    );
                    PacketDistributor.sendToPlayer(serverPlayer, new SketchActionFeedbackPayload("message.sketchbook.memory_apply_failed"));
                }
            }
        });
    }
}
