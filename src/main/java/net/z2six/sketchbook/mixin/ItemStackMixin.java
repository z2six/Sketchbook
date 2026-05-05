package net.z2six.sketchbook.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.z2six.sketchbook.book.SignedBookDates;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void sketchbook$addSignedDateTooltip(
        Item.TooltipContext tooltipContext,
        @Nullable Player player,
        TooltipFlag tooltipFlag,
        CallbackInfoReturnable<List<Component>> cir
    ) {
        SignedBookDates.getSignedDate((ItemStack)(Object)this)
            .map(SignedBookDates::tooltipLine)
            .ifPresent(cir.getReturnValue()::add);
    }
}
