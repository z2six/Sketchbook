package net.z2six.sketchbook.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.book.PageSketch;

import java.util.Optional;
import java.util.UUID;

public final class TornSketchFrameRenderer {
    private static final ResourceLocation CANVAS_TEXTURE = ResourceLocation.fromNamespaceAndPath(Sketchbook.MODID, "textures/item/torn_sketch_canvas.png");
    private static final float PAGE_WIDTH = 104.0F;
    private static final float PAGE_HEIGHT = 118.0F;
    private static final float PAGE_MARGIN_X = 8.0F;
    private static final float PAGE_MARGIN_Y = 7.0F;
    private static final float SKETCH_Z_OFFSET = -0.5F;

    private TornSketchFrameRenderer() {
    }

    public static boolean render(UUID referenceId, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Optional<PageSketch> sketch = ClientSketchCache.get(referenceId);
        if (sketch.isEmpty()) {
            ClientSketchReferenceRequestManager.request(referenceId);
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(0.0078125F, 0.0078125F, 0.0078125F);
        poseStack.translate(-64.0F, -64.0F, 0.0F);
        poseStack.translate(0.0F, 0.0F, -1.0F);

        float pageLeft = (128.0F - PAGE_WIDTH) * 0.5F;
        float pageTop = (128.0F - PAGE_HEIGHT) * 0.5F;
        float pageRight = pageLeft + PAGE_WIDTH;
        float pageBottom = pageTop + PAGE_HEIGHT;

        thisOrStaticDraw(buffer.getBuffer(RenderType.text(CANVAS_TEXTURE)), poseStack.last().pose(), pageLeft, pageTop, pageRight, pageBottom, packedLight, 0.0F);

        if (sketch.isPresent()) {
            PageSketch pageSketch = sketch.get();
            float availableWidth = PAGE_WIDTH - PAGE_MARGIN_X * 2.0F;
            float availableHeight = PAGE_HEIGHT - PAGE_MARGIN_Y * 2.0F;
            float aspect = pageSketch.width() / (float)pageSketch.height();
            float sketchWidth = Math.min(availableWidth, availableHeight * aspect);
            float sketchHeight = sketchWidth / aspect;
            float sketchLeft = 64.0F - sketchWidth * 0.5F;
            float sketchTop = 64.0F - sketchHeight * 0.5F;
            ResourceLocation sketchTexture = PageSketchTextureCache.get(pageSketch);
            thisOrStaticDraw(
                buffer.getBuffer(RenderType.text(sketchTexture)),
                poseStack.last().pose(),
                sketchLeft,
                sketchTop,
                sketchLeft + sketchWidth,
                sketchTop + sketchHeight,
                packedLight,
                SKETCH_Z_OFFSET
            );
        }

        poseStack.popPose();
        return true;
    }

    private static void thisOrStaticDraw(VertexConsumer consumer, Matrix4f pose, float left, float top, float right, float bottom, int packedLight, float z) {
        add(consumer, pose, left, bottom, z, 0.0F, 1.0F, packedLight);
        add(consumer, pose, right, bottom, z, 1.0F, 1.0F, packedLight);
        add(consumer, pose, right, top, z, 1.0F, 0.0F, packedLight);
        add(consumer, pose, left, top, z, 0.0F, 0.0F, packedLight);
    }

    private static void add(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, float u, float v, int packedLight) {
        consumer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setLight(packedLight);
    }
}
