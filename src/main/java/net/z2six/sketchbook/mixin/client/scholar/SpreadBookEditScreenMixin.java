package net.z2six.sketchbook.mixin.client.scholar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.book.BookItemLink;
import net.z2six.sketchbook.book.BookItemLinks;
import net.z2six.sketchbook.book.BookEntitySketch;
import net.z2six.sketchbook.book.BookItemSketch;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.book.EntityDetail;
import net.z2six.sketchbook.book.EntityStudy;
import net.z2six.sketchbook.book.ItemDetail;
import net.z2six.sketchbook.book.ItemDetailData;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.book.SceneMemorySummary;
import net.z2six.sketchbook.book.SceneMemoryTitles;
import net.z2six.sketchbook.book.SignedBookDates;
import net.z2six.sketchbook.book.SketchColorMask;
import net.z2six.sketchbook.book.SketchSourceImage;
import net.z2six.sketchbook.client.ClientSceneMemoryCache;
import net.z2six.sketchbook.client.ClientEntityScanCache;
import net.z2six.sketchbook.client.ClientSketchCache;
import net.z2six.sketchbook.client.ClientSketchRequestManager;
import net.z2six.sketchbook.client.EntitySketchRenderer;
import net.z2six.sketchbook.client.ItemLinkRenderer;
import net.z2six.sketchbook.client.ItemSearchOverlay;
import net.z2six.sketchbook.client.ItemSketchRenderer;
import net.z2six.sketchbook.client.SketchBookScreenBridge;
import net.z2six.sketchbook.client.SketchCaptureController;
import net.z2six.sketchbook.client.SketchContextMenu;
import net.z2six.sketchbook.client.SketchPageRenderer;
import net.z2six.sketchbook.image.SketchImageProcessor;
import net.z2six.sketchbook.network.BookSketchColorPayload;
import net.z2six.sketchbook.network.BookEntitySketchPayload;
import net.z2six.sketchbook.network.BookItemLinkPayload;
import net.z2six.sketchbook.network.BookItemSketchPayload;
import net.z2six.sketchbook.network.BookSketchPayload;
import net.z2six.sketchbook.network.RipSketchPagePayload;
import net.z2six.sketchbook.network.UseSceneMemoryPayload;
import net.z2six.sketchbook.item.PencilColor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Mixin(targets = "io.github.mortuusars.scholar.client.gui.screen.edit.SpreadBookEditScreen", remap = false)
public abstract class SpreadBookEditScreenMixin extends Screen implements SketchBookScreenBridge {
    @Shadow @Final protected ItemStack bookStack;
    @Shadow @Final protected List<String> pages;
    @Shadow protected boolean bookModified;

    @Shadow protected abstract void updateButtons();
    @Shadow protected abstract void setTextBoxes();

    @Unique private final SketchContextMenu sketchbook$contextMenu = new SketchContextMenu();
    @Unique private final ItemSearchOverlay sketchbook$itemSearch = new ItemSearchOverlay();
    @Unique private static final long sketchbook$ENTITY_SYNC_IDLE_DELAY_MS = 5000L;
    @Unique private final Map<Integer, BookEntitySketch> sketchbook$entitySketchPreviews = new HashMap<>();
    @Unique private final Map<Integer, BookItemSketch> sketchbook$itemSketchPreviews = new HashMap<>();
    @Unique private int sketchbook$menuMouseX;
    @Unique private int sketchbook$menuMouseY;
    @Unique private int sketchbook$menuPageIndex = -1;
    @Unique private int sketchbook$currentMouseX;
    @Unique private int sketchbook$currentMouseY;
    @Unique private int sketchbook$pendingColorPageIndex = -1;
    @Unique private UUID sketchbook$pendingColorReferenceId;
    @Unique private int sketchbook$pendingColorMask = SketchColorMask.NONE;
    @Unique private PageSketch sketchbook$pendingColorPreview;
    @Unique private int sketchbook$dragEntityPageIndex = -1;
    @Unique private int sketchbook$dragButton = -1;
    @Unique private boolean sketchbook$dragRotates;
    @Unique private double sketchbook$lastDragMouseX;
    @Unique private double sketchbook$lastDragMouseY;
    @Unique private final Map<Integer, BookEntitySketch> sketchbook$pendingEntitySyncs = new HashMap<>();
    @Unique private final Map<Integer, Long> sketchbook$pendingEntitySyncDeadlines = new HashMap<>();
    @Unique private final Map<Integer, BookItemSketch> sketchbook$pendingItemSyncs = new HashMap<>();
    @Unique private final Map<Integer, Long> sketchbook$pendingItemSyncDeadlines = new HashMap<>();

    protected SpreadBookEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createWidgets", at = @At("TAIL"))
    private void sketchbook$createWidgets(CallbackInfo ci) {
        this.sketchbook$updateSketchUi();
    }

    @Inject(method = "setTextBoxes()V", at = @At("TAIL"))
    private void sketchbook$setTextBoxes(CallbackInfo ci) {
        this.sketchbook$commitPendingColorChange();
        this.sketchbook$contextMenu.clear();
        this.sketchbook$updateSketchUi();
    }

    @Inject(method = "updateButtons", at = @At("TAIL"))
    private void sketchbook$updateButtons(CallbackInfo ci) {
        this.sketchbook$updateSketchUi();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sketchbook$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.sketchbook$itemSearch.isVisible()) {
            if (this.sketchbook$itemSearch.mouseClicked(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
                return;
            }
            this.sketchbook$itemSearch.clear();
            this.sketchbook$updateSketchUi();
        }

        if (this.sketchbook$contextMenu.isVisible()) {
            if (this.sketchbook$contextMenu.contains(mouseX, mouseY, this.font, this.width)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    this.sketchbook$contextMenu.click(mouseX, mouseY, this.font, this.width);
                    cir.setReturnValue(true);
                }
                return;
            }
            this.sketchbook$commitPendingColorChange();
            this.sketchbook$contextMenu.clear();
        }

        int entityPageIndex = this.sketchbook$getPageAt(mouseX, mouseY);
        if (entityPageIndex >= 0 && this.sketchbook$canTransformEntitySketch() && this.sketchbook$isOverEntitySketch(entityPageIndex, mouseX, mouseY) && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
            this.sketchbook$beginEntityDrag(entityPageIndex, button, mouseX, mouseY);
            cir.setReturnValue(true);
            return;
        }
        if (entityPageIndex >= 0 && this.sketchbook$canTransformEntitySketch() && this.sketchbook$isOverItemSketch(entityPageIndex, mouseX, mouseY) && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
            this.sketchbook$beginEntityDrag(entityPageIndex, button, mouseX, mouseY);
            cir.setReturnValue(true);
            return;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }

        int pageIndex = this.sketchbook$getPageAt(mouseX, mouseY);
        if (pageIndex < 0) {
            return;
        }

        if (!this.sketchbook$hasSketch(pageIndex) && !this.sketchbook$hasSelection(pageIndex) && !SketchbookItems.hasPencil(this.minecraft.player) && this.sketchbook$getCurrentDate().isEmpty() && ClientEntityScanCache.getIdentified().isEmpty()) {
            return;
        }

        this.sketchbook$openContextMenu(pageIndex, (int)Math.round(mouseX), (int)Math.round(mouseY));
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, require = 0)
    private void sketchbook$mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (this.sketchbook$itemSearch.mouseDragged(mouseY)) {
            cir.setReturnValue(true);
            return;
        }
        if (this.sketchbook$dragEntityPageIndex < 0 || button != this.sketchbook$dragButton) {
            return;
        }

        BookEntitySketch sketch = this.sketchbook$getDisplayedEntitySketch(this.sketchbook$dragEntityPageIndex).orElse(null);
        if (sketch == null) {
            BookItemSketch itemSketch = this.sketchbook$getDisplayedItemSketch(this.sketchbook$dragEntityPageIndex).orElse(null);
            if (itemSketch == null) {
                this.sketchbook$dragEntityPageIndex = -1;
                return;
            }
            BookItemSketch updated = this.sketchbook$dragRotates
                ? itemSketch.withTransform(itemSketch.x(), itemSketch.y(), itemSketch.scale(), itemSketch.yaw() + (float)(mouseX - this.sketchbook$lastDragMouseX), itemSketch.roll())
                : itemSketch.withTransform(itemSketch.x() + (float)(mouseX - this.sketchbook$lastDragMouseX), itemSketch.y() + (float)(mouseY - this.sketchbook$lastDragMouseY), itemSketch.scale(), itemSketch.yaw(), itemSketch.roll());
            this.sketchbook$lastDragMouseX = mouseX;
            this.sketchbook$lastDragMouseY = mouseY;
            this.sketchbook$setItemSketchPreview(this.sketchbook$dragEntityPageIndex, updated);
            this.sketchbook$scheduleItemSketchSync(this.sketchbook$dragEntityPageIndex, updated);
            cir.setReturnValue(true);
            return;
        }

        BookEntitySketch updated = this.sketchbook$dragRotates
            ? sketch.withTransform(sketch.x(), sketch.y(), sketch.scale(), sketch.rotation() + (float)(mouseX - this.sketchbook$lastDragMouseX))
            : sketch.withTransform(sketch.x() + (float)(mouseX - this.sketchbook$lastDragMouseX), sketch.y() + (float)(mouseY - this.sketchbook$lastDragMouseY), sketch.scale(), sketch.rotation());
        this.sketchbook$lastDragMouseX = mouseX;
        this.sketchbook$lastDragMouseY = mouseY;
        this.sketchbook$setEntitySketchPreview(this.sketchbook$dragEntityPageIndex, updated);
        this.sketchbook$scheduleEntitySketchSync(this.sketchbook$dragEntityPageIndex, updated);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void sketchbook$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.sketchbook$itemSearch.mouseReleased()) {
            cir.setReturnValue(true);
            return;
        }
        if (button == this.sketchbook$dragButton) {
            this.sketchbook$dragEntityPageIndex = -1;
            this.sketchbook$dragButton = -1;
            this.sketchbook$dragRotates = false;
        }
    }

    @Unique
    private void sketchbook$updateEntityDrag(double mouseX, double mouseY) {
        if (this.minecraft == null) {
            return;
        }

        long window = this.minecraft.getWindow().getWindow();
        if (this.sketchbook$dragEntityPageIndex < 0) {
            int pageIndex = this.sketchbook$getPageAt(mouseX, mouseY);
            if (pageIndex >= 0 && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS && this.sketchbook$canTransformEntitySketch() && this.sketchbook$isOverEntitySketch(pageIndex, mouseX, mouseY)) {
                this.sketchbook$beginEntityDrag(pageIndex, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, mouseX, mouseY);
            } else if (pageIndex >= 0 && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS && this.sketchbook$canTransformEntitySketch() && this.sketchbook$isOverItemSketch(pageIndex, mouseX, mouseY)) {
                this.sketchbook$beginEntityDrag(pageIndex, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, mouseX, mouseY);
            } else {
                return;
            }
        }

        if (GLFW.glfwGetMouseButton(window, this.sketchbook$dragButton) != GLFW.GLFW_PRESS) {
            this.sketchbook$dragEntityPageIndex = -1;
            this.sketchbook$dragButton = -1;
            this.sketchbook$dragRotates = false;
            return;
        }

        double deltaX = mouseX - this.sketchbook$lastDragMouseX;
        double deltaY = mouseY - this.sketchbook$lastDragMouseY;
        if (deltaX == 0.0D && deltaY == 0.0D) {
            return;
        }

        BookEntitySketch sketch = this.sketchbook$getDisplayedEntitySketch(this.sketchbook$dragEntityPageIndex).orElse(null);
        if (sketch == null) {
            BookItemSketch itemSketch = this.sketchbook$getDisplayedItemSketch(this.sketchbook$dragEntityPageIndex).orElse(null);
            if (itemSketch == null) {
                this.sketchbook$dragEntityPageIndex = -1;
                this.sketchbook$dragButton = -1;
                this.sketchbook$dragRotates = false;
                return;
            }
            BookItemSketch updated = this.sketchbook$dragRotates
                ? itemSketch.withTransform(itemSketch.x(), itemSketch.y(), itemSketch.scale(), itemSketch.yaw() + (float)deltaX, itemSketch.roll())
                : itemSketch.withTransform(itemSketch.x() + (float)deltaX, itemSketch.y() + (float)deltaY, itemSketch.scale(), itemSketch.yaw(), itemSketch.roll());
            this.sketchbook$lastDragMouseX = mouseX;
            this.sketchbook$lastDragMouseY = mouseY;
            this.sketchbook$setItemSketchPreview(this.sketchbook$dragEntityPageIndex, updated);
            this.sketchbook$scheduleItemSketchSync(this.sketchbook$dragEntityPageIndex, updated);
            return;
        }

        BookEntitySketch updated = this.sketchbook$dragRotates
            ? sketch.withTransform(sketch.x(), sketch.y(), sketch.scale(), sketch.rotation() + (float)deltaX)
            : sketch.withTransform(sketch.x() + (float)deltaX, sketch.y() + (float)deltaY, sketch.scale(), sketch.rotation());
        this.sketchbook$lastDragMouseX = mouseX;
        this.sketchbook$lastDragMouseY = mouseY;
        this.sketchbook$setEntitySketchPreview(this.sketchbook$dragEntityPageIndex, updated);
        this.sketchbook$scheduleEntitySketchSync(this.sketchbook$dragEntityPageIndex, updated);
    }

    @Unique
    private void sketchbook$beginEntityDrag(int pageIndex, int button, double mouseX, double mouseY) {
        this.sketchbook$dragEntityPageIndex = pageIndex;
        this.sketchbook$dragButton = button;
        this.sketchbook$dragRotates = button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        this.sketchbook$lastDragMouseX = mouseX;
        this.sketchbook$lastDragMouseY = mouseY;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sketchbook$renderSketches(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.sketchbook$currentMouseX = mouseX;
        this.sketchbook$currentMouseY = mouseY;
        SpreadBookScreenAccessor spread = (SpreadBookScreenAccessor)this;
        this.sketchbook$renderPageSketch(graphics, this.sketchbook$getLeftPageIndex(), spread.sketchbook$getLeftPos() + 22, spread.sketchbook$getTopPos() + 21);
        this.sketchbook$renderPageSketch(graphics, this.sketchbook$getRightPageIndex(), spread.sketchbook$getLeftPos() + 159, spread.sketchbook$getTopPos() + 21);
        this.sketchbook$contextMenu.render(graphics, this.font, mouseX, mouseY, this.width);
        this.sketchbook$itemSearch.render(graphics, this.font, mouseX, mouseY);
        this.sketchbook$updateEntityDrag(mouseX, mouseY);
        this.sketchbook$tickEntitySketchSync();
        this.sketchbook$tickItemSketchSync();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
    private void sketchbook$charTyped(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.sketchbook$itemSearch.isVisible() && this.sketchbook$itemSearch.charTyped(codePoint)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void sketchbook$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.sketchbook$itemSearch.isVisible() && this.sketchbook$itemSearch.keyPressed(keyCode)) {
            cir.setReturnValue(true);
            return;
        }
        if (this.sketchbook$itemSearch.isVisible()) {
            cir.setReturnValue(true);
            return;
        }
        if (this.sketchbook$handleItemLinkDeletion(keyCode)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "saveChanges", at = @At("HEAD"), remap = false)
    private void sketchbook$flushEntitySketchesBeforeSave(boolean sign, String title, CallbackInfo ci) {
        this.sketchbook$flushEntitySketchSync();
        this.sketchbook$flushItemSketchSync();
    }

    @Override
    public BookSketchTarget sketchbook$getTarget() {
        return BookSketchTarget.hand(InteractionHand.MAIN_HAND);
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
        BookSketches.applyLegacySketch(this.bookStack, this.pages, pageIndex, sketch);
        this.bookModified = true;
        this.setTextBoxes();
        this.updateButtons();
        this.sketchbook$updateSketchUi();
    }

    @Override
    public void sketchbook$setSketchReference(int pageIndex, UUID referenceId) {
        BookSketches.applyReference(this.bookStack, this.pages, pageIndex, referenceId);
        this.bookModified = true;
        this.setTextBoxes();
        this.updateButtons();
        this.sketchbook$updateSketchUi();
    }

    @Override
    public void sketchbook$setEntitySketch(int pageIndex, BookEntitySketch sketch) {
        if (!this.sketchbook$pendingEntitySyncs.containsKey(pageIndex)) {
            this.sketchbook$entitySketchPreviews.remove(pageIndex);
        }
        boolean hadSketch = this.sketchbook$hasSketch(pageIndex);
        BookSketches.applyEntitySketch(this.bookStack, this.pages, pageIndex, sketch);
        this.bookModified = true;
        if (!hadSketch) {
            this.setTextBoxes();
            this.updateButtons();
            this.sketchbook$updateSketchUi();
        }
    }

    @Override
    public void sketchbook$setItemSketch(int pageIndex, BookItemSketch sketch) {
        if (!this.sketchbook$pendingItemSyncs.containsKey(pageIndex)) {
            this.sketchbook$itemSketchPreviews.remove(pageIndex);
        }
        boolean hadSketch = this.sketchbook$hasSketch(pageIndex);
        BookSketches.applyItemSketch(this.bookStack, this.pages, pageIndex, sketch);
        this.bookModified = true;
        if (!hadSketch) {
            this.setTextBoxes();
            this.updateButtons();
            this.sketchbook$updateSketchUi();
        }
    }

    @Override
    public java.util.Optional<UUID> sketchbook$getSketchReference(int pageIndex) {
        return BookSketches.getSketchReference(this.bookStack, pageIndex);
    }

    @Override
    public void sketchbook$cacheSketch(UUID referenceId, PageSketch sketch, java.util.Optional<SketchSourceImage> sourceImage, int colorMask) {
        ClientSketchCache.put(referenceId, sketch, sourceImage, colorMask);
    }

    @Override
    public void sketchbook$removeSketch(int pageIndex) {
        this.sketchbook$entitySketchPreviews.remove(pageIndex);
        this.sketchbook$itemSketchPreviews.remove(pageIndex);
        this.sketchbook$pendingEntitySyncs.remove(pageIndex);
        this.sketchbook$pendingEntitySyncDeadlines.remove(pageIndex);
        this.sketchbook$pendingItemSyncs.remove(pageIndex);
        this.sketchbook$pendingItemSyncDeadlines.remove(pageIndex);
        BookSketches.removeSketch(this.bookStack, this.pages, pageIndex);
        this.bookModified = true;
        this.setTextBoxes();
        this.updateButtons();
        this.sketchbook$updateSketchUi();
    }

    @Override
    public boolean sketchbook$handleContextScroll(double mouseX, double mouseY, double scrollY) {
        if (this.sketchbook$itemSearch.contains(mouseX, mouseY)) {
            return this.sketchbook$itemSearch.scroll(scrollY);
        }
        if (this.sketchbook$contextMenu.contains(mouseX, mouseY, this.font, this.width)) {
            return this.sketchbook$contextMenu.scroll(mouseX, mouseY, scrollY, this.font);
        }
        int pageIndex = this.sketchbook$getPageAt(mouseX, mouseY);
        if (pageIndex >= 0 && this.sketchbook$canTransformEntitySketch() && this.sketchbook$isOverItemSketch(pageIndex, mouseX, mouseY) && this.sketchbook$scaleItemSketch(pageIndex, scrollY)) {
            return true;
        }
        if (pageIndex >= 0 && this.sketchbook$canTransformEntitySketch() && this.sketchbook$isOverEntitySketch(pageIndex, mouseX, mouseY) && this.sketchbook$scaleEntitySketch(pageIndex, scrollY)) {
            return true;
        }
        return this.sketchbook$contextMenu.scroll(mouseX, mouseY, scrollY, this.font);
    }

    @Override
    public boolean sketchbook$handleOverlayChar(char codePoint) {
        return this.sketchbook$itemSearch.isVisible() && this.sketchbook$itemSearch.charTyped(codePoint);
    }

    @Override
    public boolean sketchbook$handleOverlayKey(int keyCode, int scanCode, int modifiers) {
        if (!this.sketchbook$itemSearch.isVisible()) {
            return false;
        }
        this.sketchbook$itemSearch.keyPressed(keyCode);
        if (!this.sketchbook$itemSearch.isVisible()) {
            this.sketchbook$updateSketchUi();
        }
        return true;
    }

    @Override
    public Screen sketchbook$asScreen() {
        return this;
    }

    @Unique
    private void sketchbook$openContextMenu(int pageIndex, int mouseX, int mouseY) {
        this.sketchbook$menuMouseX = mouseX;
        this.sketchbook$menuMouseY = mouseY;
        this.sketchbook$menuPageIndex = pageIndex;
        this.sketchbook$contextMenu.open(this.sketchbook$buildContextEntries(pageIndex), this.font, mouseX, mouseY, this.width, this.height);
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
        this.sketchbook$updateTextBox(this.sketchbook$getScholarField("leftPageTextBox"), this.sketchbook$getLeftPageIndex());
        this.sketchbook$updateTextBox(this.sketchbook$getScholarField("rightPageTextBox"), this.sketchbook$getRightPageIndex());
    }

    @Unique
    private void sketchbook$updateTextBox(Object textBox, int pageIndex) {
        if (textBox == null) {
            return;
        }

        boolean visible = !this.sketchbook$itemSearch.isVisible() && !this.sketchbook$hasSketch(pageIndex);
        this.sketchbook$setTextBoxBoolean(textBox, "visible", visible);
        this.sketchbook$setTextBoxBoolean(textBox, "active", visible);
        if (!visible) {
            this.sketchbook$setTextBoxBoolean(textBox, "focused", false);
            this.sketchbook$setTextBoxBoolean(textBox, "isFocused", false);
            this.sketchbook$invokeTextBoxFocus(textBox, false);
        }
    }

    @Unique
    private void sketchbook$setTextBoxBoolean(Object textBox, String fieldName, boolean value) {
        try {
            Field field = this.sketchbook$getField(textBox.getClass(), fieldName);
            if (field == null) {
                return;
            }
            field.setAccessible(true);
            field.setBoolean(textBox, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private void sketchbook$invokeTextBoxFocus(Object textBox, boolean focused) {
        try {
            java.lang.reflect.Method method = textBox.getClass().getMethod("setFocused", boolean.class);
            method.invoke(textBox, focused);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private Object sketchbook$getScholarField(String fieldName) {
        try {
            Field field = this.sketchbook$getField(this.getClass(), fieldName);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            return field.get(this);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private Field sketchbook$getField(Class<?> owner, String fieldName) {
        Class<?> current = owner;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Unique
    private void sketchbook$renderPageSketch(GuiGraphics graphics, int pageIndex, int left, int top) {
        PageSketch sketch = BookSketches.getInlineSketch(this.bookStack, pageIndex);
        if (sketch == null) {
            sketch = this.sketchbook$getPendingPreview(pageIndex);
            if (sketch == null) {
                sketch = BookSketches.getSketchReference(this.bookStack, pageIndex).flatMap(ClientSketchCache::get).orElse(null);
            }
            if (sketch == null && BookSketches.hasSketch(this.bookStack, pageIndex)) {
                ClientSketchRequestManager.request(this.sketchbook$getTarget(), pageIndex);
            }
        }
        if (sketch == null && this.sketchbook$menuPageIndex == pageIndex) {
            sketch = this.sketchbook$hoveredMemoryPreview(this.sketchbook$currentMouseX, this.sketchbook$currentMouseY);
        }
        if (sketch != null) {
            SketchPageRenderer.render(graphics, left, top, 114, 128, sketch);
        } else {
            this.sketchbook$getDisplayedEntitySketch(pageIndex).ifPresent(entitySketch -> EntitySketchRenderer.render(graphics, left, top, 114, 128, entitySketch));
            this.sketchbook$getDisplayedItemSketch(pageIndex).ifPresent(itemSketch -> ItemSketchRenderer.render(graphics, left, top, 114, 128, itemSketch));
        }
        ItemLinkRenderer.renderLinks(graphics, this.font, this.bookStack, this.pages, pageIndex, left, top, this.sketchbook$currentMouseX, this.sketchbook$currentMouseY);
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
    private List<SketchContextMenu.Entry> sketchbook$buildContextEntries(int pageIndex) {
        Optional<String> currentDate = this.sketchbook$getCurrentDate();
        boolean hasPencil = SketchbookItems.hasPencil(this.minecraft.player);
        ClientEntityScanCache.ensureRequested();
        if (this.sketchbook$hasSketch(pageIndex)) {
            boolean sourceAvailable = this.sketchbook$hasColorSource(pageIndex);
            Optional<BookEntitySketch> entitySketch = this.sketchbook$getDisplayedEntitySketch(pageIndex);
            Optional<BookItemSketch> itemSketch = this.sketchbook$getDisplayedItemSketch(pageIndex);
            List<SketchContextMenu.Entry> entries = new ArrayList<>();
            entries.add(
                SketchContextMenu.Entry.action(Component.translatable("button.sketchbook.delete"), true, () -> {
                    PacketDistributor.sendToServer(BookSketchPayload.remove(this.sketchbook$getTarget(), pageIndex));
                }));
            entries.add(
                SketchContextMenu.Entry.action(Component.translatable("button.sketchbook.rip_page"), true, () -> {
                    PacketDistributor.sendToServer(new RipSketchPagePayload(this.sketchbook$getTarget(), pageIndex));
                }));
            entries.add(
                SketchContextMenu.Entry.tallSubmenu(
                    Component.translatable("menu.sketchbook.color"),
                    hasPencil && sourceAvailable,
                    this.sketchbook$buildColorEntries(pageIndex, sourceAvailable)
                ));
            entitySketch.ifPresent(sketch -> entries.add(SketchContextMenu.Entry.submenu(
                Component.translatable("menu.sketchbook.details"),
                hasPencil && !ClientEntityScanCache.getDetails(sketch.study()).isEmpty(),
                this.sketchbook$buildDetailEntries(pageIndex, sketch, hasPencil)
            )));
            itemSketch.ifPresent(sketch -> entries.add(SketchContextMenu.Entry.submenu(
                Component.translatable("menu.sketchbook.details"),
                hasPencil && !this.sketchbook$getItemDetails(sketch).isEmpty(),
                this.sketchbook$buildItemDetailEntries(pageIndex, sketch, hasPencil)
            )));
            entries.add(SketchContextMenu.Entry.action(Component.translatable("button.sketchbook.add_date"), false, () -> { }));
            return entries;
        }

        boolean canCapture = hasPencil && this.sketchbook$canCaptureSketch(pageIndex);
        Component label = canCapture
            ? Component.translatable("button.sketchbook.sketch")
            : Component.translatable("menu.sketchbook.sketch_page_must_be_empty");
        List<SceneMemorySummary> memories = ClientSceneMemoryCache.getMemories();
        List<EntityStudy> entities = ClientEntityScanCache.getIdentified();
        Optional<String> insertableDate = currentDate.filter(date -> this.sketchbook$canInsertDate(pageIndex, date));
        boolean hasSelection = this.sketchbook$hasSelection(pageIndex);
        return List.of(
            SketchContextMenu.Entry.action(Component.translatable("button.sketchbook.add_date"), insertableDate.isPresent(), () -> insertableDate.ifPresent(date -> this.sketchbook$insertDate(pageIndex, date))),
            SketchContextMenu.Entry.action(Component.translatable("menu.sketchbook.item_link"), hasSelection, () -> this.sketchbook$openItemLinkSearch(pageIndex)),
            SketchContextMenu.Entry.action(label, canCapture, () -> SketchCaptureController.requestCapture(this, pageIndex)),
            SketchContextMenu.Entry.action(Component.translatable("menu.sketchbook.items"), canCapture, () -> this.sketchbook$openItemSearch(pageIndex)),
            SketchContextMenu.Entry.tallSubmenu(Component.translatable("menu.sketchbook.entities"), hasPencil && !entities.isEmpty() && this.sketchbook$canCaptureSketch(pageIndex), this.sketchbook$buildEntityEntries(pageIndex, entities, hasPencil)),
            SketchContextMenu.Entry.submenu(Component.translatable("menu.sketchbook.memories"), hasPencil && !memories.isEmpty() && this.sketchbook$canCaptureSketch(pageIndex), this.sketchbook$buildMemoryEntries(pageIndex, memories, hasPencil))
        );
    }

    @Unique
    private void sketchbook$openItemSearch(int pageIndex) {
        this.sketchbook$contextMenu.clear();
        this.sketchbook$itemSearch.open(pageIndex, this.sketchbook$menuMouseX, this.sketchbook$menuMouseY, this.width, this.height, itemId -> {
            BookItemSketch sketch = BookItemSketch.create(itemId);
            this.sketchbook$setItemSketch(pageIndex, sketch);
            this.sketchbook$sendItemSketchSync(pageIndex, sketch);
        });
        this.sketchbook$updateSketchUi();
    }

    @Unique
    private void sketchbook$openItemLinkSearch(int pageIndex) {
        Object editor = this.sketchbook$getEditorForPage(pageIndex);
        int start = this.sketchbook$selectionStart(editor);
        int end = this.sketchbook$selectionEnd(editor);
        if (editor == null || start == end) {
            return;
        }
        this.sketchbook$contextMenu.clear();
        this.sketchbook$itemSearch.open(pageIndex, this.sketchbook$menuMouseX, this.sketchbook$menuMouseY, this.width, this.height, false, itemId -> this.sketchbook$applyItemLink(pageIndex, editor, start, end, itemId));
        this.sketchbook$updateSketchUi();
    }

    @Unique
    private boolean sketchbook$hasSelection(int pageIndex) {
        Object editor = this.sketchbook$getEditorForPage(pageIndex);
        return editor != null && this.sketchbook$selectionStart(editor) != this.sketchbook$selectionEnd(editor);
    }

    @Unique
    private Object sketchbook$getEditorForPage(int pageIndex) {
        Object textBox = pageIndex == this.sketchbook$getLeftPageIndex()
            ? this.sketchbook$getScholarField("leftPageTextBox")
            : pageIndex == this.sketchbook$getRightPageIndex() ? this.sketchbook$getScholarField("rightPageTextBox") : null;
        if (textBox == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = textBox.getClass().getMethod("getEditor");
            return method.invoke(textBox);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private int sketchbook$selectionStart(Object editor) {
        return this.sketchbook$invokeEditorInt(editor, "getSelectionStart");
    }

    @Unique
    private int sketchbook$selectionEnd(Object editor) {
        return this.sketchbook$invokeEditorInt(editor, "getSelectionEnd");
    }

    @Unique
    private int sketchbook$invokeEditorInt(Object editor, String methodName) {
        if (editor == null) {
            return 0;
        }
        try {
            java.lang.reflect.Method method = editor.getClass().getMethod(methodName);
            return ((Number)method.invoke(editor)).intValue();
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    @Unique
    private void sketchbook$applyItemLink(int pageIndex, Object editor, int start, int end, ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
        if (item == Items.AIR || editor == null) {
            return;
        }

        String currentText = pageIndex >= 0 && pageIndex < this.pages.size() ? this.pages.get(pageIndex) : "";
        BookItemLinks itemLinks = this.bookStack.getOrDefault(Sketchbook.BOOK_ITEM_LINKS, BookItemLinks.EMPTY).withReplacement(pageIndex, start, end, end - start, itemId);
        this.bookStack.set(Sketchbook.BOOK_ITEM_LINKS, itemLinks);
        PacketDistributor.sendToServer(new BookItemLinkPayload(this.sketchbook$getTarget(), pageIndex, currentText, itemLinks.get(pageIndex)));
    }

    @Unique
    private boolean sketchbook$handleItemLinkDeletion(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_BACKSPACE && keyCode != GLFW.GLFW_KEY_DELETE) {
            return false;
        }

        int pageIndex = this.sketchbook$getFocusedEditorPage();
        Object editor = this.sketchbook$getEditorForPage(pageIndex);
        if (editor == null || pageIndex < 0 || pageIndex >= this.pages.size()) {
            return false;
        }

        String pageText = this.pages.get(pageIndex);
        int start;
        int end;
        int selectionStart = this.sketchbook$selectionStart(editor);
        int selectionEnd = this.sketchbook$selectionEnd(editor);
        if (selectionStart != selectionEnd) {
            start = this.sketchbook$clampTextIndex(selectionStart, pageText);
            end = this.sketchbook$clampTextIndex(selectionEnd, pageText);
        } else {
            int cursor = this.sketchbook$clampTextIndex(this.sketchbook$invokeEditorInt(editor, "getCursorPos"), pageText);
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                start = Math.max(0, cursor - 1);
                end = cursor;
            } else {
                start = cursor;
                end = Math.min(pageText.length(), cursor + 1);
            }
        }
        if (start >= end) {
            return false;
        }

        int expandedStart = start;
        int expandedEnd = end;
        boolean touchedLink = false;
        BookItemLinks itemLinks = this.bookStack.getOrDefault(Sketchbook.BOOK_ITEM_LINKS, BookItemLinks.EMPTY);
        for (BookItemLink link : itemLinks.get(pageIndex)) {
            int linkStart = this.sketchbook$clampTextIndex(link.start(), pageText);
            int linkEnd = this.sketchbook$clampTextIndex(link.end(), pageText);
            if (linkStart < expandedEnd && linkEnd > expandedStart) {
                expandedStart = Math.min(expandedStart, linkStart);
                expandedEnd = Math.max(expandedEnd, linkEnd);
                touchedLink = true;
            }
        }
        if (!touchedLink || expandedStart >= expandedEnd) {
            return false;
        }

        String editedText = pageText.substring(0, expandedStart) + pageText.substring(expandedEnd);
        try {
            editor.getClass().getMethod("setSelectionRange", int.class, int.class).invoke(editor, expandedStart, expandedEnd);
            editor.getClass().getMethod("insertTextAtCursor", String.class).invoke(editor, "");
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
        this.pages.set(pageIndex, editedText);
        this.bookModified = true;
        this.setTextBoxes();
        this.updateButtons();
        this.sketchbook$updateSketchUi();

        BookItemLinks updatedLinks = itemLinks.withEdit(pageIndex, expandedStart, expandedEnd, 0);
        if (updatedLinks.isEmpty()) {
            this.bookStack.remove(Sketchbook.BOOK_ITEM_LINKS);
        } else {
            this.bookStack.set(Sketchbook.BOOK_ITEM_LINKS, updatedLinks);
        }
        PacketDistributor.sendToServer(new BookItemLinkPayload(this.sketchbook$getTarget(), pageIndex, editedText, updatedLinks.get(pageIndex)));
        return true;
    }

    @Unique
    private int sketchbook$clampTextIndex(int index, String text) {
        return Math.max(0, Math.min(text.length(), index));
    }

    @Unique
    private int sketchbook$getFocusedEditorPage() {
        Object left = this.sketchbook$getScholarField("leftPageTextBox");
        if (left != null && this.sketchbook$getTextBoxBoolean(left, "focused", "isFocused")) {
            return this.sketchbook$getLeftPageIndex();
        }
        Object right = this.sketchbook$getScholarField("rightPageTextBox");
        if (right != null && this.sketchbook$getTextBoxBoolean(right, "focused", "isFocused")) {
            return this.sketchbook$getRightPageIndex();
        }
        return -1;
    }

    @Unique
    private boolean sketchbook$getTextBoxBoolean(Object textBox, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Field field = this.sketchbook$getField(textBox.getClass(), fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    return field.getBoolean(textBox);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    @Unique
    private Optional<String> sketchbook$getCurrentDate() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return Optional.empty();
        }
        return SignedBookDates.currentDateString(this.minecraft.level.getDayTime());
    }

    @Unique
    private boolean sketchbook$canInsertDate(int pageIndex, String date) {
        return !this.sketchbook$hasSketch(pageIndex) && this.sketchbook$isValidPageText(this.sketchbook$dateInsertedText(pageIndex, date));
    }

    @Unique
    private void sketchbook$insertDate(int pageIndex, String date) {
        if (!this.sketchbook$canInsertDate(pageIndex, date)) {
            return;
        }

        this.sketchbook$ensurePageCapacity(pageIndex);
        this.pages.set(pageIndex, this.sketchbook$dateInsertedText(pageIndex, date));
        this.bookModified = true;
        this.setTextBoxes();
        this.updateButtons();
        this.sketchbook$updateSketchUi();
    }

    @Unique
    private String sketchbook$dateInsertedText(int pageIndex, String date) {
        String currentText = pageIndex >= 0 && pageIndex < this.pages.size() ? this.pages.get(pageIndex) : "";
        return currentText.isBlank() ? date : currentText + "\n" + date;
    }

    @Unique
    private boolean sketchbook$isValidPageText(String text) {
        return text.length() < 1024 && this.font.wordWrapHeight(text, 114) <= 128;
    }

    @Unique
    private void sketchbook$ensurePageCapacity(int targetPageIndex) {
        while (this.pages.size() <= targetPageIndex && this.pages.size() < 100) {
            this.pages.add("");
        }
    }

    @Unique
    private List<SketchContextMenu.Entry> sketchbook$buildColorEntries(int pageIndex, boolean sourceAvailable) {
        int availableColorMask = SketchbookItems.getAvailableColoredPencilMask(this.minecraft.player);
        int currentColorMask = this.sketchbook$getDisplayedColorMask(pageIndex);
        boolean allSelected = availableColorMask != 0 && (currentColorMask & availableColorMask) == availableColorMask;
        List<SketchContextMenu.Entry> entries = new ArrayList<>();
        entries.add(this.sketchbook$allColorEntry(pageIndex, sourceAvailable, availableColorMask, allSelected));
        for (PencilColor color : PencilColor.menuColors()) {
            entries.add(this.sketchbook$colorEntry(pageIndex, color, sourceAvailable, currentColorMask));
        }
        return entries;
    }

    @Unique
    private SketchContextMenu.Entry sketchbook$allColorEntry(int pageIndex, boolean sourceAvailable, int availableColorMask, boolean allSelected) {
        int updatedColorMask = allSelected ? SketchColorMask.NONE : availableColorMask;
        return SketchContextMenu.Entry.stickyAction(
            this.sketchbook$checkedLabel(allSelected, Component.translatable("menu.sketchbook.color_all")),
            sourceAvailable && availableColorMask != 0,
            () -> this.sketchbook$setColorMask(pageIndex, updatedColorMask)
        );
    }

    @Unique
    private SketchContextMenu.Entry sketchbook$colorEntry(int pageIndex, PencilColor color, boolean sourceAvailable, int currentColorMask) {
        boolean selected = SketchColorMask.isSelected(currentColorMask, color);
        boolean available = SketchbookItems.hasRequiredPencils(this.minecraft.player, color.bit());
        boolean active = sourceAvailable && (available || selected);
        int updatedColorMask = SketchColorMask.withColor(currentColorMask, color, !selected);
        return SketchContextMenu.Entry.stickyAction(
            this.sketchbook$checkedLabel(selected, Component.translatable(color.translationKey())),
            active,
            () -> this.sketchbook$setColorMask(pageIndex, updatedColorMask)
        );
    }

    @Unique
    private boolean sketchbook$hasColorSource(int pageIndex) {
        return BookSketches.getSketchReference(this.bookStack, pageIndex).map(ClientSketchCache::hasSource).orElse(false)
            || this.sketchbook$getDisplayedEntitySketch(pageIndex).isPresent()
            || this.sketchbook$getDisplayedItemSketch(pageIndex).isPresent();
    }

    @Unique
    private List<SketchContextMenu.Entry> sketchbook$buildMemoryEntries(int pageIndex, List<SceneMemorySummary> memories, boolean enabled) {
        return memories.stream()
            .map(memory -> SketchContextMenu.Entry.memoryAction(SceneMemoryTitles.component(memory.createdGameTime()), enabled, memory.memoryId(), () -> PacketDistributor.sendToServer(new UseSceneMemoryPayload(this.sketchbook$getTarget(), pageIndex, memory.memoryId()))))
            .toList();
    }

    @Unique
    private List<SketchContextMenu.Entry> sketchbook$buildEntityEntries(int pageIndex, List<EntityStudy> entities, boolean enabled) {
        return entities.stream()
            .sorted(java.util.Comparator.comparing(EntityStudy::displayLabel, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(study -> study.entityTypeId().toString())
                .thenComparing(EntityStudy::variantData))
            .map(study -> SketchContextMenu.Entry.iconAction(Component.literal(study.displayLabel()), ClientEntityScanCache.hasDetailedStudy(study) ? new ItemStack(Items.NETHER_STAR) : ItemStack.EMPTY, enabled, () -> {
                BookEntitySketch sketch = BookEntitySketch.create(study);
                this.sketchbook$setEntitySketch(pageIndex, sketch);
                this.sketchbook$sendEntitySketchSync(pageIndex, sketch);
            }))
            .toList();
    }

    @Unique
    private List<SketchContextMenu.Entry> sketchbook$buildDetailEntries(int pageIndex, BookEntitySketch sketch, boolean enabled) {
        Map<String, String> knownDetails = ClientEntityScanCache.getDetails(sketch.study());
        List<SketchContextMenu.Entry> entries = new ArrayList<>();
        for (EntityDetail detail : EntityDetail.values()) {
            if (!knownDetails.containsKey(detail.id())) {
                continue;
            }
            boolean selected = (sketch.detailMask() & detail.bit()) != 0;
            entries.add(SketchContextMenu.Entry.stickyAction(
                this.sketchbook$checkedLabel(selected, Component.translatable(detail.translationKey())),
                enabled,
                () -> this.sketchbook$toggleEntityDetail(pageIndex, detail)
            ));
        }
        return entries;
    }

    @Unique
    private void sketchbook$toggleEntityDetail(int pageIndex, EntityDetail detail) {
        BookEntitySketch sketch = this.sketchbook$getDisplayedEntitySketch(pageIndex).orElse(null);
        if (sketch == null) {
            return;
        }

        boolean selected = (sketch.detailMask() & detail.bit()) != 0;
        int updatedMask = selected ? sketch.detailMask() & ~detail.bit() : sketch.detailMask() | detail.bit();
        BookEntitySketch updated = sketch.withDetailMask(updatedMask);
        this.sketchbook$setEntitySketchPreview(pageIndex, updated);
        this.sketchbook$sendEntitySketchSync(pageIndex, updated);
        this.sketchbook$contextMenu.refresh(this.sketchbook$buildContextEntries(pageIndex), this.font, this.width, this.height);
    }

    @Unique
    private List<SketchContextMenu.Entry> sketchbook$buildItemDetailEntries(int pageIndex, BookItemSketch sketch, boolean enabled) {
        Map<ItemDetail, String> knownDetails = this.sketchbook$getItemDetails(sketch);
        List<SketchContextMenu.Entry> entries = new ArrayList<>();
        for (ItemDetail detail : ItemDetail.values()) {
            if (!knownDetails.containsKey(detail)) {
                continue;
            }
            boolean selected = (sketch.detailMask() & detail.bit()) != 0;
            entries.add(SketchContextMenu.Entry.stickyAction(
                this.sketchbook$checkedLabel(selected, Component.translatable(detail.translationKey())),
                enabled,
                () -> this.sketchbook$toggleItemDetail(pageIndex, detail)
            ));
        }
        return entries;
    }

    @Unique
    private Map<ItemDetail, String> sketchbook$getItemDetails(BookItemSketch sketch) {
        var item = BuiltInRegistries.ITEM.getOptional(sketch.itemId()).orElse(Items.AIR);
        return item == Items.AIR ? Map.of() : ItemDetailData.valuesFor(new ItemStack(item));
    }

    @Unique
    private void sketchbook$toggleItemDetail(int pageIndex, ItemDetail detail) {
        BookItemSketch sketch = this.sketchbook$getDisplayedItemSketch(pageIndex).orElse(null);
        if (sketch == null) {
            return;
        }

        boolean selected = (sketch.detailMask() & detail.bit()) != 0;
        int updatedMask = selected ? sketch.detailMask() & ~detail.bit() : sketch.detailMask() | detail.bit();
        BookItemSketch updated = sketch.withDetailMask(updatedMask);
        this.sketchbook$setItemSketchPreview(pageIndex, updated);
        this.sketchbook$sendItemSketchSync(pageIndex, updated);
        this.sketchbook$contextMenu.refresh(this.sketchbook$buildContextEntries(pageIndex), this.font, this.width, this.height);
    }

    @Unique
    private boolean sketchbook$isOverEntitySketch(int pageIndex, double mouseX, double mouseY) {
        BookEntitySketch sketch = this.sketchbook$getDisplayedEntitySketch(pageIndex).orElse(null);
        if (sketch == null) {
            return false;
        }
        return this.sketchbook$getPageAt(mouseX, mouseY) == pageIndex;
    }

    @Unique
    private boolean sketchbook$isOverItemSketch(int pageIndex, double mouseX, double mouseY) {
        BookItemSketch sketch = this.sketchbook$getDisplayedItemSketch(pageIndex).orElse(null);
        return sketch != null && this.sketchbook$getPageAt(mouseX, mouseY) == pageIndex;
    }

    @Unique
    private boolean sketchbook$canTransformEntitySketch() {
        return this.bookStack.is(Items.WRITABLE_BOOK);
    }

    @Unique
    private boolean sketchbook$scaleEntitySketch(int pageIndex, double scrollY) {
        BookEntitySketch sketch = this.sketchbook$getDisplayedEntitySketch(pageIndex).orElse(null);
        if (sketch == null || scrollY == 0.0D) {
            return false;
        }

        float nextScale = Math.max(8.0F, sketch.scale() + (float)scrollY * 3.0F);
        if (nextScale == sketch.scale()) {
            return true;
        }

        BookEntitySketch updated = sketch.withTransform(sketch.x(), sketch.y(), nextScale, sketch.rotation());
        this.sketchbook$setEntitySketchPreview(pageIndex, updated);
        this.sketchbook$scheduleEntitySketchSync(pageIndex, updated);
        return true;
    }

    @Unique
    private boolean sketchbook$scaleItemSketch(int pageIndex, double scrollY) {
        BookItemSketch sketch = this.sketchbook$getDisplayedItemSketch(pageIndex).orElse(null);
        if (sketch == null || scrollY == 0.0D) {
            return false;
        }

        float nextScale = Math.max(8.0F, sketch.scale() + (float)scrollY * 3.0F);
        if (nextScale == sketch.scale()) {
            return true;
        }

        BookItemSketch updated = sketch.withTransform(sketch.x(), sketch.y(), nextScale, sketch.yaw(), sketch.roll());
        this.sketchbook$setItemSketchPreview(pageIndex, updated);
        this.sketchbook$scheduleItemSketchSync(pageIndex, updated);
        return true;
    }

    @Unique
    private Optional<BookEntitySketch> sketchbook$getDisplayedEntitySketch(int pageIndex) {
        BookEntitySketch preview = this.sketchbook$entitySketchPreviews.get(pageIndex);
        return preview == null ? BookSketches.getEntitySketch(this.bookStack, pageIndex) : Optional.of(preview);
    }

    @Unique
    private void sketchbook$setEntitySketchPreview(int pageIndex, BookEntitySketch sketch) {
        this.sketchbook$entitySketchPreviews.put(pageIndex, sketch);
    }

    @Unique
    private void sketchbook$scheduleEntitySketchSync(int pageIndex, BookEntitySketch sketch) {
        this.sketchbook$pendingEntitySyncs.put(pageIndex, sketch);
        this.sketchbook$pendingEntitySyncDeadlines.put(pageIndex, System.currentTimeMillis() + sketchbook$ENTITY_SYNC_IDLE_DELAY_MS);
    }

    @Unique
    private void sketchbook$tickEntitySketchSync() {
        if (this.sketchbook$pendingEntitySyncs.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Integer> readyPages = this.sketchbook$pendingEntitySyncDeadlines.entrySet().stream()
            .filter(entry -> now >= entry.getValue())
            .map(Map.Entry::getKey)
            .toList();
        readyPages.forEach(this::sketchbook$flushEntitySketchSync);
    }

    @Unique
    private void sketchbook$flushEntitySketchSync() {
        List<Integer> pageIndexes = new ArrayList<>(this.sketchbook$pendingEntitySyncs.keySet());
        pageIndexes.forEach(this::sketchbook$flushEntitySketchSync);
    }

    @Unique
    private void sketchbook$flushEntitySketchSync(int pageIndex) {
        BookEntitySketch sketch = this.sketchbook$pendingEntitySyncs.remove(pageIndex);
        this.sketchbook$pendingEntitySyncDeadlines.remove(pageIndex);
        if (sketch == null) {
            return;
        }
        this.sketchbook$sendEntitySketchSync(pageIndex, sketch);
    }

    @Unique
    private void sketchbook$sendEntitySketchSync(int pageIndex, BookEntitySketch sketch) {
        PacketDistributor.sendToServer(new BookEntitySketchPayload(this.sketchbook$getTarget(), pageIndex, sketch));
    }

    @Unique
    private Optional<BookItemSketch> sketchbook$getDisplayedItemSketch(int pageIndex) {
        BookItemSketch preview = this.sketchbook$itemSketchPreviews.get(pageIndex);
        return preview == null ? BookSketches.getItemSketch(this.bookStack, pageIndex) : Optional.of(preview);
    }

    @Unique
    private void sketchbook$setItemSketchPreview(int pageIndex, BookItemSketch sketch) {
        this.sketchbook$itemSketchPreviews.put(pageIndex, sketch);
    }

    @Unique
    private void sketchbook$scheduleItemSketchSync(int pageIndex, BookItemSketch sketch) {
        this.sketchbook$pendingItemSyncs.put(pageIndex, sketch);
        this.sketchbook$pendingItemSyncDeadlines.put(pageIndex, System.currentTimeMillis() + sketchbook$ENTITY_SYNC_IDLE_DELAY_MS);
    }

    @Unique
    private void sketchbook$tickItemSketchSync() {
        if (this.sketchbook$pendingItemSyncs.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Integer> readyPages = this.sketchbook$pendingItemSyncDeadlines.entrySet().stream()
            .filter(entry -> now >= entry.getValue())
            .map(Map.Entry::getKey)
            .toList();
        readyPages.forEach(this::sketchbook$flushItemSketchSync);
    }

    @Unique
    private void sketchbook$flushItemSketchSync() {
        List<Integer> pageIndexes = new ArrayList<>(this.sketchbook$pendingItemSyncs.keySet());
        pageIndexes.forEach(this::sketchbook$flushItemSketchSync);
    }

    @Unique
    private void sketchbook$flushItemSketchSync(int pageIndex) {
        BookItemSketch sketch = this.sketchbook$pendingItemSyncs.remove(pageIndex);
        this.sketchbook$pendingItemSyncDeadlines.remove(pageIndex);
        if (sketch == null) {
            return;
        }
        this.sketchbook$sendItemSketchSync(pageIndex, sketch);
    }

    @Unique
    private void sketchbook$sendItemSketchSync(int pageIndex, BookItemSketch sketch) {
        PacketDistributor.sendToServer(new BookItemSketchPayload(this.sketchbook$getTarget(), pageIndex, sketch));
    }

    @Unique
    private int sketchbook$getCurrentColorMask(int pageIndex) {
        Optional<BookEntitySketch> entitySketch = this.sketchbook$getDisplayedEntitySketch(pageIndex);
        if (entitySketch.isPresent()) {
            return entitySketch.get().colorMask();
        }
        Optional<BookItemSketch> itemSketch = this.sketchbook$getDisplayedItemSketch(pageIndex);
        if (itemSketch.isPresent()) {
            return itemSketch.get().colorMask();
        }
        return BookSketches.getSketchReference(this.bookStack, pageIndex).map(ClientSketchCache::getColorMask).orElse(SketchColorMask.NONE);
    }

    @Unique
    private int sketchbook$getDisplayedColorMask(int pageIndex) {
        if (this.sketchbook$pendingColorPageIndex == pageIndex) {
            return this.sketchbook$pendingColorMask;
        }
        return this.sketchbook$getCurrentColorMask(pageIndex);
    }

    @Unique
    private void sketchbook$setColorMask(int pageIndex, int colorMask) {
        int normalizedColorMask = SketchColorMask.normalize(colorMask);
        BookEntitySketch entitySketch = this.sketchbook$getDisplayedEntitySketch(pageIndex).orElse(null);
        if (entitySketch != null) {
            BookEntitySketch updated = entitySketch.withColorMask(normalizedColorMask);
            this.sketchbook$setEntitySketchPreview(pageIndex, updated);
            this.sketchbook$pendingEntitySyncs.remove(pageIndex);
            this.sketchbook$pendingEntitySyncDeadlines.remove(pageIndex);
            this.sketchbook$sendEntitySketchSync(pageIndex, updated);
            this.sketchbook$contextMenu.refresh(this.sketchbook$buildContextEntries(pageIndex), this.font, this.width, this.height);
            return;
        }

        BookItemSketch itemSketch = this.sketchbook$getDisplayedItemSketch(pageIndex).orElse(null);
        if (itemSketch != null) {
            BookItemSketch updated = itemSketch.withColorMask(normalizedColorMask);
            this.sketchbook$setItemSketchPreview(pageIndex, updated);
            this.sketchbook$pendingItemSyncs.remove(pageIndex);
            this.sketchbook$pendingItemSyncDeadlines.remove(pageIndex);
            this.sketchbook$sendItemSketchSync(pageIndex, updated);
            this.sketchbook$contextMenu.refresh(this.sketchbook$buildContextEntries(pageIndex), this.font, this.width, this.height);
            return;
        }

        UUID referenceId = BookSketches.getSketchReference(this.bookStack, pageIndex).orElse(null);
        if (referenceId == null) {
            return;
        }

        SketchSourceImage sourceImage = ClientSketchCache.getSourceImage(referenceId).orElse(null);
        if (sourceImage == null) {
            return;
        }

        this.sketchbook$pendingColorPageIndex = pageIndex;
        this.sketchbook$pendingColorReferenceId = referenceId;
        this.sketchbook$pendingColorMask = normalizedColorMask;
        this.sketchbook$pendingColorPreview = SketchImageProcessor.render(sourceImage.width(), sourceImage.height(), sourceImage.readArgb(), normalizedColorMask, SketchImageProcessor.SketchStyle.V1);
        this.sketchbook$contextMenu.refresh(this.sketchbook$buildContextEntries(pageIndex), this.font, this.width, this.height);
    }

    @Unique
    private PageSketch sketchbook$getPendingPreview(int pageIndex) {
        if (this.sketchbook$pendingColorPageIndex != pageIndex) {
            return null;
        }
        return this.sketchbook$pendingColorPreview;
    }

    @Unique
    private void sketchbook$commitPendingColorChange() {
        if (this.sketchbook$pendingColorPageIndex < 0 || this.sketchbook$pendingColorReferenceId == null) {
            this.sketchbook$clearPendingColorChange();
            return;
        }

        java.util.Optional<UUID> liveReference = BookSketches.getSketchReference(this.bookStack, this.sketchbook$pendingColorPageIndex);
        int committedColorMask = liveReference.map(ClientSketchCache::getColorMask).orElse(SketchColorMask.NONE);
        if (liveReference.filter(this.sketchbook$pendingColorReferenceId::equals).isPresent() && committedColorMask != this.sketchbook$pendingColorMask) {
            PacketDistributor.sendToServer(new BookSketchColorPayload(this.sketchbook$getTarget(), this.sketchbook$pendingColorPageIndex, this.sketchbook$pendingColorMask));
        }
        this.sketchbook$clearPendingColorChange();
    }

    @Unique
    private void sketchbook$clearPendingColorChange() {
        this.sketchbook$pendingColorPageIndex = -1;
        this.sketchbook$pendingColorReferenceId = null;
        this.sketchbook$pendingColorMask = SketchColorMask.NONE;
        this.sketchbook$pendingColorPreview = null;
    }

    @Unique
    private Component sketchbook$checkedLabel(boolean checked, Component label) {
        return Component.translatable(checked ? "menu.sketchbook.checked" : "menu.sketchbook.unchecked", label);
    }

    @Unique
    private PageSketch sketchbook$hoveredMemoryPreview(int mouseX, int mouseY) {
        return this.sketchbook$contextMenu.hoveredMemoryId(mouseX, mouseY, this.font)
            .flatMap(ClientSceneMemoryCache::getMemory)
            .map(SceneMemorySummary::previewSketch)
            .orElse(null);
    }
}
