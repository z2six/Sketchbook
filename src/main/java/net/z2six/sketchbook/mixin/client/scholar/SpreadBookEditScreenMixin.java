package net.z2six.sketchbook.mixin.client.scholar;

import io.github.mortuusars.scholar.client.gui.screen.edit.SpreadBookEditScreen;
import io.github.mortuusars.scholar.client.gui.widget.textbox.TextBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.book.SceneMemorySummary;
import net.z2six.sketchbook.book.SceneMemoryTitles;
import net.z2six.sketchbook.book.SketchColorMask;
import net.z2six.sketchbook.book.SketchSourceImage;
import net.z2six.sketchbook.client.ClientSceneMemoryCache;
import net.z2six.sketchbook.client.ClientSketchCache;
import net.z2six.sketchbook.client.ClientSketchRequestManager;
import net.z2six.sketchbook.client.SketchBookScreenBridge;
import net.z2six.sketchbook.client.SketchCaptureController;
import net.z2six.sketchbook.client.SketchContextMenu;
import net.z2six.sketchbook.client.SketchPageRenderer;
import net.z2six.sketchbook.image.SketchImageProcessor;
import net.z2six.sketchbook.network.BookSketchColorPayload;
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

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

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

    @Unique private final SketchContextMenu sketchbook$contextMenu = new SketchContextMenu();
    @Unique private int sketchbook$menuMouseX;
    @Unique private int sketchbook$menuMouseY;
    @Unique private int sketchbook$menuPageIndex = -1;
    @Unique private int sketchbook$currentMouseX;
    @Unique private int sketchbook$currentMouseY;
    @Unique private int sketchbook$pendingColorPageIndex = -1;
    @Unique private UUID sketchbook$pendingColorReferenceId;
    @Unique private int sketchbook$pendingColorMask = SketchColorMask.NONE;
    @Unique private PageSketch sketchbook$pendingColorPreview;

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

    @Inject(method = "updateButtonVisibility", at = @At("TAIL"))
    private void sketchbook$updateButtonVisibility(CallbackInfo ci) {
        this.sketchbook$updateSketchUi();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sketchbook$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
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

        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }

        int pageIndex = this.sketchbook$getPageAt(mouseX, mouseY);
        if (pageIndex < 0) {
            return;
        }

        if (!this.sketchbook$hasSketch(pageIndex) && !SketchbookItems.hasPencil(this.minecraft.player)) {
            return;
        }

        this.sketchbook$openContextMenu(pageIndex, (int)Math.round(mouseX), (int)Math.round(mouseY));
        cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sketchbook$renderSketches(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.sketchbook$currentMouseX = mouseX;
        this.sketchbook$currentMouseY = mouseY;
        SpreadBookScreenAccessor spread = (SpreadBookScreenAccessor)this;
        this.sketchbook$renderPageSketch(graphics, this.sketchbook$getLeftPageIndex(), spread.sketchbook$getLeftPos() + 22, spread.sketchbook$getTopPos() + 21);
        this.sketchbook$renderPageSketch(graphics, this.sketchbook$getRightPageIndex(), spread.sketchbook$getLeftPos() + 159, spread.sketchbook$getTopPos() + 21);
        this.sketchbook$contextMenu.render(graphics, this.font, mouseX, mouseY, this.width);
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
        BookSketches.applyLegacySketch(this.bookStack, this.pages, pageIndex, sketch);
        this.bookModified = true;
        this.setTextBoxes();
        this.updateButtonVisibility();
        this.sketchbook$updateSketchUi();
    }

    @Override
    public void sketchbook$setSketchReference(int pageIndex, UUID referenceId) {
        BookSketches.applyReference(this.bookStack, this.pages, pageIndex, referenceId);
        this.bookModified = true;
        this.setTextBoxes();
        this.updateButtonVisibility();
        this.sketchbook$updateSketchUi();
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
        BookSketches.removeSketch(this.bookStack, this.pages, pageIndex);
        this.bookModified = true;
        this.setTextBoxes();
        this.updateButtonVisibility();
        this.sketchbook$updateSketchUi();
    }

    @Override
    public boolean sketchbook$handleContextScroll(double mouseX, double mouseY, double scrollY) {
        return this.sketchbook$contextMenu.scroll(mouseX, mouseY, scrollY, this.font);
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
    private List<SketchContextMenu.Entry> sketchbook$buildContextEntries(int pageIndex) {
        if (this.sketchbook$hasSketch(pageIndex)) {
            boolean sourceAvailable = this.sketchbook$hasColorSource(pageIndex);
            return List.of(
                SketchContextMenu.Entry.action(Component.translatable("button.sketchbook.delete"), true, () -> {
                    PacketDistributor.sendToServer(BookSketchPayload.remove(this.sketchbook$getTarget(), pageIndex));
                }),
                SketchContextMenu.Entry.action(Component.translatable("button.sketchbook.rip_page"), true, () -> {
                    PacketDistributor.sendToServer(new RipSketchPagePayload(this.sketchbook$getTarget(), pageIndex));
                }),
                SketchContextMenu.Entry.submenu(
                    Component.translatable("menu.sketchbook.color"),
                    sourceAvailable,
                    this.sketchbook$buildColorEntries(pageIndex, sourceAvailable)
                )
            );
        }

        boolean canCapture = SketchbookItems.hasPencil(this.minecraft.player) && this.sketchbook$canCaptureSketch(pageIndex);
        Component label = canCapture
            ? Component.translatable("button.sketchbook.sketch")
            : Component.translatable("menu.sketchbook.sketch_page_must_be_empty");
        List<SceneMemorySummary> memories = ClientSceneMemoryCache.getMemories();
        return List.of(
            SketchContextMenu.Entry.action(label, canCapture, () -> SketchCaptureController.requestCapture(this, pageIndex)),
            SketchContextMenu.Entry.submenu(Component.translatable("menu.sketchbook.memories"), !memories.isEmpty() && this.sketchbook$canCaptureSketch(pageIndex), this.sketchbook$buildMemoryEntries(pageIndex, memories))
        );
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
        return BookSketches.getSketchReference(this.bookStack, pageIndex).map(ClientSketchCache::hasSource).orElse(false);
    }

    @Unique
    private List<SketchContextMenu.Entry> sketchbook$buildMemoryEntries(int pageIndex, List<SceneMemorySummary> memories) {
        return memories.stream()
            .map(memory -> SketchContextMenu.Entry.memoryAction(SceneMemoryTitles.component(memory.createdGameTime()), true, memory.memoryId(), () -> PacketDistributor.sendToServer(new UseSceneMemoryPayload(this.sketchbook$getTarget(), pageIndex, memory.memoryId()))))
            .toList();
    }

    @Unique
    private int sketchbook$getCurrentColorMask(int pageIndex) {
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
        UUID referenceId = BookSketches.getSketchReference(this.bookStack, pageIndex).orElse(null);
        if (referenceId == null) {
            return;
        }

        SketchSourceImage sourceImage = ClientSketchCache.getSourceImage(referenceId).orElse(null);
        if (sourceImage == null) {
            return;
        }

        int normalizedColorMask = SketchColorMask.normalize(colorMask);
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
