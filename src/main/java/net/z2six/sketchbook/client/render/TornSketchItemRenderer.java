package net.z2six.sketchbook.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.book.PageSketch;
import net.z2six.sketchbook.client.ClientSketchCache;
import net.z2six.sketchbook.client.ClientSketchReferenceRequestManager;
import net.z2six.sketchbook.client.PageSketchTextureCache;

import java.util.Optional;
import java.util.UUID;

public final class TornSketchItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float PAGE_MARGIN_X = 0.08F;
    private static final float PAGE_MARGIN_Y = 0.07F;
    private static final ResourceLocation CANVAS_TEXTURE = ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "item/torn_sketch_canvas");
    private static TornSketchItemRenderer instance;

    private TornSketchItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static TornSketchItemRenderer getInstance() {
        if (instance == null) {
            instance = new TornSketchItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        UUID referenceId = SketchbookItems.getTornSketchReference(stack).orElse(null);
        if (referenceId == null) {
            this.renderCanvasOnly(displayContext, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        Optional<PageSketch> sketch = ClientSketchCache.get(referenceId);
        if (sketch.isEmpty()) {
            ClientSketchReferenceRequestManager.request(referenceId);
            this.renderCanvasOnly(displayContext, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        this.renderSketchPage(sketch.get(), displayContext, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private void renderCanvasOnly(ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        MultiBufferSource.BufferSource immediate = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer canvasConsumer = ItemRenderer.getFoilBufferDirect(immediate, RenderType.entityCutoutNoCull(CANVAS_TEXTURE), true, false);
        float pageScale = this.pageScale(displayContext);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(pageScale, pageScale, pageScale);
        this.drawTexturedQuad(poseStack, canvasConsumer, packedLight, packedOverlay, -0.5F, -0.5F, 0.5F, 0.5F);
        poseStack.popPose();
        immediate.endBatch();
    }

    private void renderSketchPage(PageSketch sketch, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        MultiBufferSource.BufferSource immediate = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer canvasConsumer = ItemRenderer.getFoilBufferDirect(immediate, RenderType.entityCutoutNoCull(CANVAS_TEXTURE), true, false);
        ResourceLocation texture = PageSketchTextureCache.get(sketch);
        VertexConsumer sketchConsumer = ItemRenderer.getFoilBufferDirect(immediate, RenderType.entityCutoutNoCull(texture), true, false);
        float pageScale = this.pageScale(displayContext);
        float availableWidth = 1.0F - PAGE_MARGIN_X * 2.0F;
        float availableHeight = 1.0F - PAGE_MARGIN_Y * 2.0F;
        float sketchAspect = sketch.width() / (float)sketch.height();
        float sketchWidth = Math.min(availableWidth, availableHeight * sketchAspect);
        float sketchHeight = sketchWidth / sketchAspect;
        float halfSketchWidth = sketchWidth * 0.5F;
        float halfSketchHeight = sketchHeight * 0.5F;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(pageScale, pageScale, pageScale);
        this.drawTexturedQuad(poseStack, canvasConsumer, packedLight, packedOverlay, -0.5F, -0.5F, 0.5F, 0.5F);
        poseStack.translate(0.0F, 0.0F, 0.01F);
        this.drawTexturedQuad(poseStack, sketchConsumer, packedLight, packedOverlay, -halfSketchWidth, -halfSketchHeight, halfSketchWidth, halfSketchHeight);
        poseStack.popPose();
        immediate.endBatch();
    }

    private float pageScale(ItemDisplayContext displayContext) {
        return switch (displayContext) {
            case FIXED -> 0.78F;
            case GUI -> 0.84F;
            case GROUND -> 0.72F;
            default -> 0.82F;
        };
    }

    private void drawTexturedQuad(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, float minX, float minY, float maxX, float maxY) {
        Matrix4f pose = poseStack.last().pose();
        this.vertex(consumer, pose, minX, maxY, 0.0F, 0.0F, 1.0F, packedLight, packedOverlay);
        this.vertex(consumer, pose, maxX, maxY, 0.0F, 1.0F, 1.0F, packedLight, packedOverlay);
        this.vertex(consumer, pose, maxX, minY, 0.0F, 1.0F, 0.0F, packedLight, packedOverlay);
        this.vertex(consumer, pose, minX, minY, 0.0F, 0.0F, 0.0F, packedLight, packedOverlay);
    }

    private void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, float u, float v, int packedLight, int packedOverlay) {
        consumer.addVertex(pose, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(packedOverlay)
            .setLight(packedLight)
            .setNormal(0.0F, 0.0F, 1.0F);
    }
}
