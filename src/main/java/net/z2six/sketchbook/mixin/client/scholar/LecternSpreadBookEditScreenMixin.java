package net.z2six.sketchbook.mixin.client.scholar;

import io.github.mortuusars.scholar.client.gui.screen.edit.LecternSpreadBookEditScreen;
import io.github.mortuusars.scholar.menu.LecternSpreadBookEditMenu;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.z2six.sketchbook.book.BookSketchTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LecternSpreadBookEditScreen.class, remap = false)
public abstract class LecternSpreadBookEditScreenMixin extends Screen {
    protected LecternSpreadBookEditScreenMixin(Component title) {
        super(title);
    }

    @Shadow public abstract LecternSpreadBookEditMenu getMenu();

    public BookSketchTarget sketchbook$getTarget() {
        return BookSketchTarget.lectern(this.getMenu().getLecternPos());
    }
}
