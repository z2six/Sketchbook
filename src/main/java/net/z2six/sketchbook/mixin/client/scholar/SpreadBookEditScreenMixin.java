package net.z2six.sketchbook.mixin.client.scholar;

import io.github.mortuusars.scholar.client.gui.screen.edit.SpreadBookEditScreen;
import io.github.mortuusars.scholar.client.gui.widget.textbox.TextBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.client.SketchBookScreenBridge;
import net.z2six.sketchbook.client.SketchCaptureController;
import net.z2six.sketchbook.client.SketchPageRenderer;
import net.z2six.sketchbook.network.BookSketchPayload;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = SpreadBookEditScreen.class, remap = false)
public abstract class SpreadBookEditScreenMixin extends Screen implements SketchBookScreenBridge {
    @Shadow @Final protected ItemStack bookStack;
    @Shadow @Final protected InteractionHand hand;
    @Shadow @Final protected List<String> pages;
    @Shadow protected TextBox leftPageTextBox;
    @Shadow protected TextBox rightPageTextBox;
    @Shadow protected boolean bookModified;

    @Shadow protected abstract void updateButtonVisibility();
    @Shadow protected abstract void setTextBoxes();

    @Unique private int sketchbook$contextPageIndex = -1;
    @Unique private int sketchbook$contextLeft;
    @Unique private int sketchbook$contextTop;
    @Unique private int sketchbook$contextWidth;
    @Unique private boolean sketchbook$contextDelete;
    @Unique private boolean sketchbook$contextActive;
    @Unique private Component sketchbook$contextLabel = CommonComponents.EMPTY;

    protected SpreadBookEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createWidgets", at = @At("TAIL"))
    private void sketchbook$createWidgets(CallbackInfo ci) {
        this.sketchbook$updateSketchUi();
    }

    @Inject(method = "setTextBoxes()V", at = @At("TAIL"))
    private void sketchbook$setTextBoxes(CallbackInfo ci) {
        this.sketchbook$clearContextMenu();
        this.sketchbook$updateSketchUi();
    }

    @Inject(method = "updateButtonVisibility", at = @At("TAIL"))
    private void sketchbook$updateButtonVisibility(CallbackInfo ci) {
        this.sketchbook$updateSketchUi();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sketchbook$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.sketchbook$contextMenuVisible()) {
            if (this.sketchbook$isInsideContextMenu(mouseX, mouseY)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (this.sketchbook$contextActive) {
                        this.sketchbook$handlePageAction(this.sketchbook$contextPageIndex);
                    }
                    this.sketchbook$clearContextMenu();
                    cir.setReturnValue(true);
                }
                return;
            }

            this.sketchbook$clearContextMenu();
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !SketchbookItems.hasPencil(this.minecraft.player)) {
            return;
        }

        int pageIndex = this.sketchbook$getPageAt(mouseX, mouseY);
        if (pageIndex >= 0) {
            this.sketchbook$openContextMenu(pageIndex, (int)Math.round(mouseX), (int)Math.round(mouseY));
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sketchbook$renderSketches(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        SpreadBookScreenAccessor spread = (SpreadBookScreenAccessor)this;
        this.sketchbook$renderPageSketch(graphics, this.sketchbook$getLeftPageIndex(), spread.sketchbook$getLeftPos() + 22, spread.sketchbook$getTopPos() + 21);
        this.sketchbook$renderPageSketch(graphics, this.sketchbook$getRightPageIndex(), spread.sketchbook$getLeftPos() + 159, spread.sketchbook$getTopPos() + 21);
        this.sketchbook$renderContextMenu(graphics);
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
        return BookSketches.hasSketch(this.bookStack, pageIndex);
    }

    @Override
    public void sketchbook$applySketch(int pageIndex, PageSketch sketch) {
        BookSketches.applySketch(this.bookStack, this.pages, pageIndex, sketch);
        this.bookModified = true;
        this.setTextBoxes();
        this.updateButtonVisibility();
        this.sketchbook$updateSketchUi();
    }

    @Override
    public void sketchbook$removeSketch(int pageIndex) {
        BookSketches.removeSketch(this.bookStack, this.pages, pageIndex);
        this.bookModified = true;
        this.setTextBoxes();
        this.updateButtonVisibility();
        this.sketchbook$updateSketchUi();
    }

    @Override
    public Screen sketchbook$asScreen() {
        return this;
    }

    @Unique
    private void sketchbook$handlePageAction(int pageIndex) {
        if (this.sketchbook$hasSketch(pageIndex)) {
            this.sketchbook$removeSketch(pageIndex);
            PacketDistributor.sendToServer(BookSketchPayload.remove(this.sketchbook$getTarget(), pageIndex));
        } else if (this.sketchbook$canCaptureSketch(pageIndex)) {
            SketchCaptureController.requestCapture(this, pageIndex);
        }
    }

    @Unique
    private int sketchbook$getLeftPageIndex() {
        return ((SpreadBookScreenAccessor)this).sketchbook$getCurrentSpread() * 2;
    }

    @Unique
    private int sketchbook$getRightPageIndex() {
        return ((SpreadBookScreenAccessor)this).sketchbook$getCurrentSpread() * 2 + 1;
    }

    @Unique
    private void sketchbook$updateSketchUi() {
        this.sketchbook$updateTextBox(this.leftPageTextBox, this.sketchbook$getLeftPageIndex());
        this.sketchbook$updateTextBox(this.rightPageTextBox, this.sketchbook$getRightPageIndex());
    }

    @Unique
    private void sketchbook$updateTextBox(TextBox textBox, int pageIndex) {
        if (textBox == null) {
            return;
        }

        boolean visible = !this.sketchbook$hasSketch(pageIndex);
        textBox.visible = visible;
        textBox.active = visible;
    }

    @Unique
    private void sketchbook$renderPageSketch(GuiGraphics graphics, int pageIndex, int left, int top) {
        PageSketch sketch = BookSketches.getSketch(this.bookStack, pageIndex);
        if (sketch != null) {
            SketchPageRenderer.render(graphics, left, top, 114, 128, sketch);
        }
    }

    @Unique
    private int sketchbook$getPageAt(double mouseX, double mouseY) {
        SpreadBookScreenAccessor spread = (SpreadBookScreenAccessor)this;
        int relativeX = (int)Math.floor(mouseX) - spread.sketchbook$getLeftPos();
        int relativeY = (int)Math.floor(mouseY) - spread.sketchbook$getTopPos();
        if (relativeX >= 22 && relativeX < 136 && relativeY >= 21 && relativeY < 149) {
            return this.sketchbook$getLeftPageIndex();
        }
        if (relativeX >= 159 && relativeX < 273 && relativeY >= 21 && relativeY < 149) {
            return this.sketchbook$getRightPageIndex();
        }
        return -1;
    }

    @Unique
    private void sketchbook$openContextMenu(int pageIndex, int mouseX, int mouseY) {
        boolean hasSketch = this.sketchbook$hasSketch(pageIndex);
        this.sketchbook$contextPageIndex = pageIndex;
        this.sketchbook$contextDelete = hasSketch;
        this.sketchbook$contextActive = hasSketch || this.sketchbook$canCaptureSketch(pageIndex);
        this.sketchbook$contextLabel = this.sketchbook$contextDelete
            ? Component.translatable("button.sketchbook.delete")
            : this.sketchbook$contextActive
                ? Component.translatable("button.sketchbook.sketch")
                : Component.translatable("menu.sketchbook.sketch_page_must_be_empty");
        this.sketchbook$contextWidth = this.font.width(this.sketchbook$contextLabel) + 12;
        this.sketchbook$contextLeft = Mth.clamp(mouseX, 4, this.width - this.sketchbook$contextWidth - 4);
        this.sketchbook$contextTop = Mth.clamp(mouseY, 4, this.height - 22);
    }

    @Unique
    private void sketchbook$clearContextMenu() {
        this.sketchbook$contextPageIndex = -1;
        this.sketchbook$contextLabel = CommonComponents.EMPTY;
    }

    @Unique
    private boolean sketchbook$contextMenuVisible() {
        return this.sketchbook$contextPageIndex >= 0;
    }

    @Unique
    private boolean sketchbook$isInsideContextMenu(double mouseX, double mouseY) {
        return this.sketchbook$contextMenuVisible()
            && mouseX >= this.sketchbook$contextLeft
            && mouseX < this.sketchbook$contextLeft + this.sketchbook$contextWidth
            && mouseY >= this.sketchbook$contextTop
            && mouseY < this.sketchbook$contextTop + 18;
    }

    @Unique
    private void sketchbook$renderContextMenu(GuiGraphics graphics) {
        if (!this.sketchbook$contextMenuVisible()) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        graphics.fill(this.sketchbook$contextLeft, this.sketchbook$contextTop, this.sketchbook$contextLeft + this.sketchbook$contextWidth, this.sketchbook$contextTop + 18, 0xF4F0E6CF);
        graphics.renderOutline(this.sketchbook$contextLeft, this.sketchbook$contextTop, this.sketchbook$contextWidth, 18, 0x7F302718);
        graphics.drawString(
            this.font,
            this.sketchbook$contextLabel,
            this.sketchbook$contextLeft + 6,
            this.sketchbook$contextTop + 5,
            this.sketchbook$contextActive ? 0x3A342E : 0x7A756D,
            false
        );
        graphics.pose().popPose();
    }
}
