package net.z2six.sketchbook.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.client.TornSketchFrameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererMixin<T extends ItemFrame> {
    @Redirect(
        method = "render(Lnet/minecraft/world/entity/decoration/ItemFrame;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;IILcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;I)V"
        )
    )
    private void sketchbook$renderTornSketchInFrame(
        ItemRenderer itemRenderer,
        ItemStack stack,
        ItemDisplayContext displayContext,
        int packedLight,
        int packedOverlay,
        PoseStack poseStack,
        MultiBufferSource buffer,
        Level level,
        int seed
    ) {
        if (SketchbookItems.getTornSketchReference(stack).map(referenceId -> TornSketchFrameRenderer.render(referenceId, poseStack, buffer, packedLight)).orElse(false)) {
            return;
        }

        itemRenderer.renderStatic(stack, displayContext, packedLight, packedOverlay, poseStack, buffer, level, seed);
    }
}
