package net.z2six.sketchbook.mixin.client;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.z2six.sketchbook.client.SketchBookScreenBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerMixin {
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void sketchbook$mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof SketchBookScreenBridge bridge && bridge.sketchbook$handleContextScroll(mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }
}
