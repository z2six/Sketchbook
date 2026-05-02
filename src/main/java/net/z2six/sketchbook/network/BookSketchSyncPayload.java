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
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.book.SketchColorMask;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.UUID;

public record BookSketchSyncPayload(BookSketchTarget target, int pageIndex, Optional<UUID> sketchId, Optional<PageSketch> sketch, boolean sourceAvailable, int colorMask) implements CustomPacketPayload {
    private static final Codec<BookSketchSyncPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BookSketchTarget.CODEC.fieldOf("target").forGetter(BookSketchSyncPayload::target),
        Codec.intRange(0, 99).fieldOf("page_index").forGetter(BookSketchSyncPayload::pageIndex),
        UUIDUtil.STRING_CODEC.optionalFieldOf("sketch_id").forGetter(BookSketchSyncPayload::sketchId),
        PageSketch.NETWORK_CODEC.optionalFieldOf("sketch").forGetter(BookSketchSyncPayload::sketch),
        Codec.BOOL.optionalFieldOf("source_available", false).forGetter(BookSketchSyncPayload::sourceAvailable),
        SketchColorMask.CODEC.optionalFieldOf("color_mask", SketchColorMask.NONE).forGetter(BookSketchSyncPayload::colorMask)
    ).apply(instance, BookSketchSyncPayload::new));
    public static final Type<BookSketchSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "book_sketch_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BookSketchSyncPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static BookSketchSyncPayload remove(BookSketchTarget target, int pageIndex) {
        return new BookSketchSyncPayload(target, pageIndex, Optional.empty(), Optional.empty(), false, SketchColorMask.NONE);
    }

    @Override
    public Type<BookSketchSyncPayload> type() {
        return TYPE;
    }

    public static void handle(BookSketchSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName("net.z2six.sketchbook.client.network.BookSketchSyncClientHandler");
                handlerClass.getMethod("handle", BookSketchSyncPayload.class).invoke(null, payload);
            } catch (ClassNotFoundException exception) {
                // Dedicated servers do not load the client sync handler.
            } catch (IllegalAccessException | NoSuchMethodException exception) {
                throw new RuntimeException("Failed to access sketch sync client handler", exception);
            } catch (InvocationTargetException exception) {
                throw new RuntimeException("Sketch sync client handler failed", exception.getCause());
            }
        });
    }
}
