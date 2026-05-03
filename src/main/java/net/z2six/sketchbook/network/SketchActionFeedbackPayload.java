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

import java.lang.reflect.InvocationTargetException;

public record SketchActionFeedbackPayload(String translationKey) implements CustomPacketPayload {
    private static final Codec<SketchActionFeedbackPayload> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("translation_key").forGetter(SketchActionFeedbackPayload::translationKey)
    ).apply(instance, SketchActionFeedbackPayload::new));

    public static final Type<SketchActionFeedbackPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "sketch_action_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SketchActionFeedbackPayload> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public Type<SketchActionFeedbackPayload> type() {
        return TYPE;
    }

    public static void handle(SketchActionFeedbackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName("net.z2six.sketchbook.client.network.SketchActionFeedbackClientHandler");
                handlerClass.getMethod("handle", SketchActionFeedbackPayload.class).invoke(null, payload);
            } catch (ClassNotFoundException exception) {
                // Dedicated servers do not load the client sync handler.
            } catch (IllegalAccessException | NoSuchMethodException exception) {
                throw new RuntimeException("Failed to access sketch action feedback client handler", exception);
            } catch (InvocationTargetException exception) {
                throw new RuntimeException("Sketch action feedback client handler failed", exception.getCause());
            }
        });
    }
}
