package net.z2six.sketchbook.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
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
import net.z2six.sketchbook.book.SketchColorMask;
import net.z2six.sketchbook.client.ClientSketchCache;
import net.z2six.sketchbook.client.ClientSketchRequestManager;
import net.z2six.sketchbook.client.SketchBookScreenBridge;
import net.z2six.sketchbook.client.SketchCaptureController;
import net.z2six.sketchbook.client.SketchContextMenu;
import net.z2six.sketchbook.client.SketchPageRenderer;
import net.z2six.sketchbook.network.BookSketchColorPayload;
import net.z2six.sketchbook.network.BookSketchPayload;
import net.z2six.sketchbook.item.PencilColor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen implements SketchBookScreenBridge {
    @Shadow private ItemStack book;
    @Shadow private InteractionHand hand;
    @Shadow private boolean isModified;
    @Shadow private boolean isSigning;
    @Shadow private int currentPage;
    @Shadow private List<String> pages;

    @Shadow protected abstract void clearDisplayCache();

    @Unique private final SketchContextMenu sketchbook$contextMenu = new SketchContextMenu();
    @Unique private int sketchbook$menuMouseX;
    @Unique private int sketchbook$menuMouseY;

    protected BookEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sketchbook$renderSketch(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.isSigning) {
            return;
        }

        PageSketch sketch = BookSketches.getInlineSketch(this.book, this.currentPage);
        if (sketch == null) {
            sketch = BookSketches.getSketchReference(this.book, this.currentPage).flatMap(ClientSketchCache::get).orElse(null);
            if (sketch == null && BookSketches.hasSketch(this.book, this.currentPage)) {
                ClientSketchRequestManager.request(this.sketchbook$getTarget(), this.currentPage);
            }
        }
        if (sketch != null) {
            SketchPageRenderer.render(graphics, this.sketchbook$pageLeft(), this.sketchbook$pageTop(), 114, 128, sketch);
        }
        this.sketchbook$contextMenu.render(graphics, this.font, mouseX, mouseY, this.width);
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
            this.sketchbook$contextMenu.clear();
        }

        if (this.isSigning || button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !this.sketchbook$isOverPage(mouseX, mouseY)) {
            return;
        }

        boolean hasSketch = this.sketchbook$hasSketch(this.currentPage);
        if (!hasSketch && !SketchbookItems.hasPencil(this.minecraft.player)) {
            return;
        }

        this.sketchbook$openContextMenu((int)Math.round(mouseX), (int)Math.round(mouseY));
        cir.setReturnValue(true);
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
        BookSketches.applyLegacySketch(this.book, this.pages, pageIndex, sketch);
        this.isModified = true;
        this.clearDisplayCache();
    }

    @Override
    public void sketchbook$setSketchReference(int pageIndex, UUID referenceId) {
        BookSketches.applyReference(this.book, this.pages, pageIndex, referenceId);
        this.isModified = true;
        this.clearDisplayCache();
    }

    @Override
    public Optional<UUID> sketchbook$getSketchReference(int pageIndex) {
        return BookSketches.getSketchReference(this.book, pageIndex);
    }

    @Override
    public void sketchbook$cacheSketch(UUID referenceId, PageSketch sketch, boolean sourceAvailable, int colorMask) {
        ClientSketchCache.put(referenceId, sketch, sourceAvailable, colorMask);
    }

    @Override
    public void sketchbook$removeSketch(int pageIndex) {
        BookSketches.removeSketch(this.book, this.pages, pageIndex);
        this.isModified = true;
        this.clearDisplayCache();
    }

    @Override
    public Screen sketchbook$asScreen() {
        return this;
    }

    @Unique
    private void sketchbook$openContextMenu(int mouseX, int mouseY) {
        this.sketchbook$menuMouseX = mouseX;
        this.sketchbook$menuMouseY = mouseY;
        this.sketchbook$contextMenu.open(this.sketchbook$buildContextEntries(this.currentPage), this.font, mouseX, mouseY, this.width, this.height);
    }

    @Unique
    private List<SketchContextMenu.Entry> sketchbook$buildContextEntries(int pageIndex) {
        if (this.sketchbook$hasSketch(pageIndex)) {
            boolean sourceAvailable = this.sketchbook$hasColorSource(pageIndex);
            return List.of(
                SketchContextMenu.Entry.action(Component.translatable("button.sketchbook.delete"), true, () -> {
                    this.sketchbook$removeSketch(pageIndex);
                    PacketDistributor.sendToServer(BookSketchPayload.remove(this.sketchbook$getTarget(), pageIndex));
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
        return List.of(SketchContextMenu.Entry.action(label, canCapture, () -> SketchCaptureController.requestCapture(this, pageIndex)));
    }

    @Unique
    private List<SketchContextMenu.Entry> sketchbook$buildColorEntries(int pageIndex, boolean sourceAvailable) {
        int availableColorMask = SketchbookItems.getAvailableColoredPencilMask(this.minecraft.player);
        int currentColorMask = this.sketchbook$getCurrentColorMask(pageIndex);
        boolean allSelected = availableColorMask != 0 && currentColorMask == availableColorMask;
        return List.of(
            this.sketchbook$allColorEntry(pageIndex, sourceAvailable, availableColorMask, allSelected),
            this.sketchbook$colorEntry(pageIndex, PencilColor.WHITE, "item.sketchbook.white_pencil", sourceAvailable, currentColorMask),
            this.sketchbook$colorEntry(pageIndex, PencilColor.YELLOW, "item.sketchbook.yellow_pencil", sourceAvailable, currentColorMask),
            this.sketchbook$colorEntry(pageIndex, PencilColor.CYAN, "item.sketchbook.cyan_pencil", sourceAvailable, currentColorMask),
            this.sketchbook$colorEntry(pageIndex, PencilColor.MAGENTA, "item.sketchbook.magenta_pencil", sourceAvailable, currentColorMask)
        );
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
    private SketchContextMenu.Entry sketchbook$colorEntry(int pageIndex, PencilColor color, String key, boolean sourceAvailable, int currentColorMask) {
        boolean selected = SketchColorMask.isSelected(currentColorMask, color);
        boolean available = SketchbookItems.hasRequiredPencils(this.minecraft.player, color.bit());
        boolean active = sourceAvailable && (available || selected);
        int updatedColorMask = SketchColorMask.withColor(currentColorMask, color, !selected);
        return SketchContextMenu.Entry.stickyAction(
            this.sketchbook$checkedLabel(selected, Component.translatable(key)),
            active,
            () -> this.sketchbook$setColorMask(pageIndex, updatedColorMask)
        );
    }

    @Unique
    private boolean sketchbook$hasColorSource(int pageIndex) {
        return BookSketches.getSketchReference(this.book, pageIndex).map(ClientSketchCache::hasSource).orElse(false);
    }

    @Unique
    private int sketchbook$getCurrentColorMask(int pageIndex) {
        return BookSketches.getSketchReference(this.book, pageIndex).map(ClientSketchCache::getColorMask).orElse(SketchColorMask.NONE);
    }

    @Unique
    private void sketchbook$setColorMask(int pageIndex, int colorMask) {
        Optional<UUID> referenceId = BookSketches.getSketchReference(this.book, pageIndex);
        if (referenceId.isEmpty()) {
            return;
        }

        int normalizedColorMask = SketchColorMask.normalize(colorMask);
        ClientSketchCache.updateColorMask(referenceId.get(), normalizedColorMask);
        PacketDistributor.sendToServer(new BookSketchColorPayload(this.sketchbook$getTarget(), pageIndex, normalizedColorMask));
        this.sketchbook$contextMenu.refresh(this.sketchbook$buildContextEntries(pageIndex), this.font, this.width, this.height);
    }

    @Unique
    private Component sketchbook$checkedLabel(boolean checked, Component label) {
        return Component.literal(checked ? "[x] " : "[ ] ").append(label);
    }

    @Unique
    private boolean sketchbook$isOverPage(double mouseX, double mouseY) {
        int left = this.sketchbook$pageLeft();
        int top = this.sketchbook$pageTop();
        return mouseX >= left && mouseX < left + 114 && mouseY >= top && mouseY < top + 128;
    }

    @Unique
    private int sketchbook$pageLeft() {
        return (this.width - 192) / 2 + 36;
    }

    @Unique
    private int sketchbook$pageTop() {
        return 32;
    }
}
