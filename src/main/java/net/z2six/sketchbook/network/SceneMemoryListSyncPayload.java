package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.SceneMemorySummary;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public record SceneMemoryListSyncPayload(List<SceneMemorySummary> memories) implements CustomPacketPayload {
    private static final Codec<SceneMemoryListSyncPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.list(SceneMemorySummary.CODEC).fieldOf("memories").forGetter(SceneMemoryListSyncPayload::memories)
    ).apply(instance, SceneMemoryListSyncPayload::new));
    public static final Type<SceneMemoryListSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "scene_memory_list_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SceneMemoryListSyncPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<SceneMemoryListSyncPayload> type() {
        return TYPE;
    }

    public static void handle(SceneMemoryListSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName("net.z2six.sketchbook.client.network.SceneMemoryListSyncClientHandler");
                handlerClass.getMethod("handle", SceneMemoryListSyncPayload.class).invoke(null, payload);
            } catch (ClassNotFoundException exception) {
                // Dedicated servers do not load the client sync handler.
            } catch (IllegalAccessException | NoSuchMethodException exception) {
                throw new RuntimeException("Failed to access memory list sync client handler", exception);
            } catch (InvocationTargetException exception) {
                throw new RuntimeException("Memory list sync client handler failed", exception.getCause());
            }
        });
    }
}
