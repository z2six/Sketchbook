package net.z2six.sketchbook.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.book.SketchColorMask;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public record SketchReferenceSyncPayload(UUID referenceId, PageSketch sketch, boolean sourceAvailable, int colorMask) implements CustomPacketPayload {
    private static final Codec<SketchReferenceSyncPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.STRING_CODEC.fieldOf("reference_id").forGetter(SketchReferenceSyncPayload::referenceId),
        PageSketch.NETWORK_CODEC.fieldOf("sketch").forGetter(SketchReferenceSyncPayload::sketch),
        Codec.BOOL.optionalFieldOf("source_available", false).forGetter(SketchReferenceSyncPayload::sourceAvailable),
        SketchColorMask.CODEC.optionalFieldOf("color_mask", SketchColorMask.NONE).forGetter(SketchReferenceSyncPayload::colorMask)
    ).apply(instance, SketchReferenceSyncPayload::new));

    public static final Type<SketchReferenceSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "sketch_reference_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SketchReferenceSyncPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<SketchReferenceSyncPayload> type() {
        return TYPE;
    }

    public static void handle(SketchReferenceSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName("net.z2six.sketchbook.client.network.SketchReferenceSyncClientHandler");
                handlerClass.getMethod("handle", SketchReferenceSyncPayload.class).invoke(null, payload);
            } catch (ClassNotFoundException exception) {
                // Dedicated servers do not load the client sync handler.
            } catch (IllegalAccessException | NoSuchMethodException exception) {
                throw new RuntimeException("Failed to access sketch reference sync client handler", exception);
            } catch (InvocationTargetException exception) {
                throw new RuntimeException("Sketch reference sync client handler failed", exception.getCause());
            }
        });
    }
}
