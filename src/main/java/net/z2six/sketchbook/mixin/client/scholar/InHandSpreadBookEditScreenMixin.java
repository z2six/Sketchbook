package net.z2six.sketchbook.mixin.client.scholar;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.z2six.sketchbook.book.BookSketchTarget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "io.github.mortuusars.scholar.client.gui.screen.edit.InHandSpreadBookEditScreen", remap = false)
public abstract class InHandSpreadBookEditScreenMixin extends Screen {
   @Shadow @Final protected InteractionHand hand;

   protected InHandSpreadBookEditScreenMixin(Component title) {
      super(title);
   }

   public BookSketchTarget sketchbook$getTarget() {
      return BookSketchTarget.hand(this.hand);
   }
}