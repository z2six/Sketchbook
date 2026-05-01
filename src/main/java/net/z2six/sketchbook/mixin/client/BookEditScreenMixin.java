package net.z2six.sketchbook.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.client.SketchCaptureController;
import net.z2six.sketchbook.client.SketchBookScreenBridge;
import net.z2six.sketchbook.client.SketchPageRenderer;
import net.z2six.sketchbook.network.BookSketchPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen implements SketchBookScreenBridge {
    @Shadow private ItemStack book;
    @Shadow private InteractionHand hand;
    @Shadow private boolean isModified;
    @Shadow private boolean isSigning;
    @Shadow private int currentPage;
    @Shadow private List<String> pages;
    @Shadow private Button signButton;

    @Shadow protected abstract void clearDisplayCache();

    @Unique private Button sketchbook$actionButton;

    protected BookEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sketchbook$init(CallbackInfo ci) {
        this.sketchbook$actionButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (this.sketchbook$hasSketch(this.currentPage)) {
                this.sketchbook$removeSketch(this.currentPage);
                PacketDistributor.sendToServer(BookSketchPayload.remove(this.sketchbook$getTarget(), this.currentPage));
            } else if (this.sketchbook$canCaptureSketch(this.currentPage)) {
                SketchCaptureController.requestCapture(this, this.currentPage);
            }
        }).bounds(this.width / 2 - 49, this.signButton.getY() + 24, 98, 20).build());
        this.sketchbook$updateButtons();
    }

    @Inject(method = "updateButtonVisibility", at = @At("TAIL"))
    private void sketchbook$updateVisibility(CallbackInfo ci) {
        this.sketchbook$updateButtons();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sketchbook$renderSketch(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.isSigning) {
            return;
        }

        int pageLeft = (this.width - 192) / 2 + 36;
        int pageTop = 32;
        PageSketch sketch = BookSketches.getSketch(this.book, this.currentPage);
        if (sketch != null) {
            SketchPageRenderer.render(graphics, pageLeft, pageTop, 114, 128, sketch);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void sketchbook$blockTyping(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isSigning && this.sketchbook$hasSketch(this.currentPage)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "bookKeyPressed", at = @At("HEAD"), cancellable = true)
    private void sketchbook$blockBookEditing(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isSigning && this.sketchbook$hasSketch(this.currentPage) && keyCode != 266 && keyCode != 267) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private void sketchbook$updateButtons() {
        if (this.sketchbook$actionButton == null) {
            return;
        }

        boolean hasPencil = SketchbookItems.hasPencil(this.minecraft.player);
        boolean hasSketch = this.sketchbook$hasSketch(this.currentPage);
        this.sketchbook$actionButton.visible = !this.isSigning && hasPencil;
        this.sketchbook$actionButton.active = !this.isSigning && hasPencil && (hasSketch || this.sketchbook$canCaptureSketch(this.currentPage));
        this.sketchbook$actionButton.setMessage(Component.translatable(hasSketch ? "button.sketchbook.delete" : "button.sketchbook.sketch"));
    }

    @Override
    public BookSketchTarget sketchbook$getTarget() {
        return BookSketchTarget.hand(this.hand);
    }

    @Override
    public boolean sketchbook$canCaptureSketch(int pageIndex) {
        String pageText = pageIndex >= 0 && pageIndex < this.pages.size() ? this.pages.get(pageIndex) : "";
        return !this.sketchbook$hasSketch(pageIndex) && BookSketches.canSketchOnText(pageText);
    }

    @Override
    public boolean sketchbook$hasSketch(int pageIndex) {
        return BookSketches.hasSketch(this.book, pageIndex);
    }

    @Override
    public void sketchbook$applySketch(int pageIndex, PageSketch sketch) {
        BookSketches.applySketch(this.book, this.pages, pageIndex, sketch);
        this.isModified = true;
        this.clearDisplayCache();
        this.sketchbook$updateButtons();
    }

    @Override
    public void sketchbook$removeSketch(int pageIndex) {
        BookSketches.removeSketch(this.book, this.pages, pageIndex);
        this.isModified = true;
        this.clearDisplayCache();
        this.sketchbook$updateButtons();
    }

    @Override
    public Screen sketchbook$asScreen() {
        return this;
    }
}
