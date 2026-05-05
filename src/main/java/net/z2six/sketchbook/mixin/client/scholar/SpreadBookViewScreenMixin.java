package net.z2six.sketchbook.mixin.client.scholar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.z2six.sketchbook.book.BookSketchTarget;
import net.z2six.sketchbook.book.BookSketches;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.book.SketchSourceImage;
import net.z2six.sketchbook.client.ClientSketchCache;
import net.z2six.sketchbook.client.ClientSketchRequestManager;
import net.z2six.sketchbook.client.SketchBookScreenBridge;
import net.z2six.sketchbook.client.SketchPageRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

@Mixin(targets = "io.github.mortuusars.scholar.client.gui.screen.view.SpreadBookViewScreen", remap = false)
public abstract class SpreadBookViewScreenMixin extends Screen implements SketchBookScreenBridge {
    protected SpreadBookViewScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sketchbook$renderSketches(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        SpreadBookScreenAccessor spread = (SpreadBookScreenAccessor)this;
        this.sketchbook$renderPageSketch(graphics, this.sketchbook$getLeftPageIndex(), spread.sketchbook$getLeftPos() + 22, spread.sketchbook$getTopPos() + 21);
        this.sketchbook$renderPageSketch(graphics, this.sketchbook$getRightPageIndex(), spread.sketchbook$getLeftPos() + 159, spread.sketchbook$getTopPos() + 21);
    }

    @Override
    public BookSketchTarget sketchbook$getTarget() {
        Optional<net.minecraft.core.BlockPos> lecternPos = this.sketchbook$getLecternPos();
        if (lecternPos.isPresent()) {
            return BookSketchTarget.lectern(lecternPos.get());
        }
        return BookSketchTarget.hand(this.sketchbook$getHand());
    }

    @Override
    public boolean sketchbook$canCaptureSketch(int pageIndex) {
        return false;
    }

    @Override
    public boolean sketchbook$hasSketch(int pageIndex) {
        return BookSketches.hasSketch(this.sketchbook$getBookStack(), pageIndex);
    }

    @Override
    public void sketchbook$applySketch(int pageIndex, PageSketch sketch) {
    }

    @Override
    public void sketchbook$setSketchReference(int pageIndex, UUID referenceId) {
    }

    @Override
    public Optional<UUID> sketchbook$getSketchReference(int pageIndex) {
        return BookSketches.getSketchReference(this.sketchbook$getBookStack(), pageIndex);
    }

    @Override
    public void sketchbook$cacheSketch(UUID referenceId, PageSketch sketch, Optional<SketchSourceImage> sourceImage, int colorMask) {
        ClientSketchCache.put(referenceId, sketch, sourceImage, colorMask);
    }

    @Override
    public void sketchbook$removeSketch(int pageIndex) {
    }

    @Override
    public boolean sketchbook$handleContextScroll(double mouseX, double mouseY, double scrollY) {
        return false;
    }

    @Override
    public Screen sketchbook$asScreen() {
        return this;
    }

    @Unique
    private void sketchbook$renderPageSketch(GuiGraphics graphics, int pageIndex, int left, int top) {
        ItemStack book = this.sketchbook$getBookStack();
        PageSketch sketch = BookSketches.getInlineSketch(book, pageIndex);
        if (sketch == null) {
            sketch = BookSketches.getSketchReference(book, pageIndex).flatMap(ClientSketchCache::get).orElse(null);
            if (sketch == null && BookSketches.hasSketch(book, pageIndex)) {
                ClientSketchRequestManager.request(this.sketchbook$getTarget(), pageIndex);
            }
        }
        if (sketch != null) {
            SketchPageRenderer.render(graphics, left, top, 114, 128, sketch);
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
    private ItemStack sketchbook$getBookStack() {
        ItemStack lecternBook = this.sketchbook$getLecternBook();
        if (!lecternBook.isEmpty()) {
            return lecternBook;
        }
        if (this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack mainHand = this.minecraft.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (BookSketches.isBookWithSketches(mainHand)) {
            return mainHand;
        }
        ItemStack offHand = this.minecraft.player.getItemInHand(InteractionHand.OFF_HAND);
        if (BookSketches.isBookWithSketches(offHand)) {
            return offHand;
        }
        return mainHand;
    }

    @Unique
    private InteractionHand sketchbook$getHand() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return InteractionHand.MAIN_HAND;
        }
        if (BookSketches.isBookWithSketches(this.minecraft.player.getItemInHand(InteractionHand.MAIN_HAND))) {
            return InteractionHand.MAIN_HAND;
        }
        if (BookSketches.isBookWithSketches(this.minecraft.player.getItemInHand(InteractionHand.OFF_HAND))) {
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    @Unique
    private ItemStack sketchbook$getLecternBook() {
        AbstractContainerMenu menu = this.sketchbook$getMenu();
        if (menu == null) {
            return ItemStack.EMPTY;
        }

        try {
            Method method = menu.getClass().getMethod("getBook");
            Object value = method.invoke(menu);
            return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    @Unique
    private Optional<net.minecraft.core.BlockPos> sketchbook$getLecternPos() {
        AbstractContainerMenu menu = this.sketchbook$getMenu();
        if (menu == null) {
            return Optional.empty();
        }

        try {
            Method method = menu.getClass().getMethod("getLecternPos");
            Object value = method.invoke(menu);
            return value instanceof net.minecraft.core.BlockPos pos ? Optional.of(pos) : Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    @Unique
    private AbstractContainerMenu sketchbook$getMenu() {
        if (!(this instanceof MenuAccess<?> access)) {
            return null;
        }
        return access.getMenu();
    }
}
