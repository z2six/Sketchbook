package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;

import java.util.Optional;

public record BookSketchTarget(boolean offHand, Optional<BlockPos> lecternPos) {
    public static final Codec<BookSketchTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("off_hand", false).forGetter(BookSketchTarget::offHand),
        BlockPos.CODEC.optionalFieldOf("lectern_pos").forGetter(BookSketchTarget::lecternPos)
    ).apply(instance, BookSketchTarget::new));

    public static BookSketchTarget hand(InteractionHand hand) {
        return new BookSketchTarget(hand == InteractionHand.OFF_HAND, Optional.empty());
    }

    public static BookSketchTarget lectern(BlockPos lecternPos) {
        return new BookSketchTarget(false, Optional.of(lecternPos));
    }

    public boolean isLectern() {
        return this.lecternPos.isPresent();
    }

    public InteractionHand hand() {
        return this.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
