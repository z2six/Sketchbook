package net.z2six.sketchbook.mixin.client.scholar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "io.github.mortuusars.scholar.client.gui.screen.SpreadBookScreen", remap = false)
public interface SpreadBookScreenAccessor {
    @Accessor("leftPos")
    int sketchbook$getLeftPos();

    @Accessor("topPos")
    int sketchbook$getTopPos();

    @Accessor("currentSpread")
    int sketchbook$getCurrentSpread();
}
