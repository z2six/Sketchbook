package net.z2six.sketchbook.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.network.RememberScenePayload;
import org.lwjgl.glfw.GLFW;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Sketchbook.MODID, value = Dist.CLIENT)
public final class SceneMemoryCaptureController {
    private static final String CATEGORY = "key.categories.sketchbook";
    private static final KeyMapping REMEMBER_SCENE = new KeyMapping("key.sketchbook.remember_scene", GLFW.GLFW_KEY_Z, CATEGORY);
    private static PendingMemoryCapture pendingCapture;

    private SceneMemoryCaptureController() {
    }

    @EventBusSubscriber(modid = Sketchbook.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(REMEMBER_SCENE);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientSceneMemoryCache.ensureRequested();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        while (REMEMBER_SCENE.consumeClick()) {
            requestCapture();
        }
    }

    public static void requestCapture() {
        if (pendingCapture != null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            return;
        }

        pendingCapture = new PendingMemoryCapture(minecraft.options.hideGui);
    }

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        if (pendingCapture == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            minecraft.options.hideGui = pendingCapture.wasHideGui;
            pendingCapture = null;
            return;
        }

        minecraft.options.hideGui = true;
        pendingCapture.hideGuiApplied = true;
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (pendingCapture != null) {
            Screen screen = event.getScreen();
            if (screen != null) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        if (pendingCapture == null || !pendingCapture.hideGuiApplied) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            minecraft.options.hideGui = pendingCapture.wasHideGui;
            pendingCapture = null;
            return;
        }

        NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
        try {
            PacketDistributor.sendToServer(new RememberScenePayload(SketchCaptureController.createSketch(image)));
        } finally {
            image.close();
            minecraft.options.hideGui = pendingCapture.wasHideGui;
            pendingCapture = null;
        }

        minecraft.player.displayClientMessage(Component.translatable("message.sketchbook.memory_saved"), true);
    }

    private static final class PendingMemoryCapture {
        private final boolean wasHideGui;
        private boolean hideGuiApplied;

        private PendingMemoryCapture(boolean wasHideGui) {
            this.wasHideGui = wasHideGui;
        }
    }
}
