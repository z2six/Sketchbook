package net.z2six.sketchbook.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.z2six.sketchbook.network.SketchActionFeedbackPayload;

public final class SketchActionFeedbackClientHandler {
    private SketchActionFeedbackClientHandler() {
    }

    public static void handle(SketchActionFeedbackPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        minecraft.player.displayClientMessage(Component.translatable(payload.translationKey()), true);
    }
}
