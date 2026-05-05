package net.z2six.sketchbook.mixin.client.scholar;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.z2six.sketchbook.book.BookSketchTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.reflect.Method;

@Mixin(targets = "io.github.mortuusars.scholar.client.gui.screen.edit.LecternSpreadBookEditScreen", remap = false)
public abstract class LecternSpreadBookEditScreenMixin extends Screen {
    protected LecternSpreadBookEditScreenMixin(Component title) {
        super(title);
    }

    @Shadow public abstract Object getMenu();

    public BookSketchTarget sketchbook$getTarget() {
        try {
            Method method = this.getMenu().getClass().getMethod("getLecternPos");
            Object value = method.invoke(this.getMenu());
            if (value instanceof net.minecraft.core.BlockPos pos) {
                return BookSketchTarget.lectern(pos);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return BookSketchTarget.hand(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
}
