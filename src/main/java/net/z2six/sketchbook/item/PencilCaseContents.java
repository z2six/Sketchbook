package net.z2six.sketchbook.item;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public record PencilCaseContents(int mask) {
    private static final int ALL_BITS = java.util.Arrays.stream(PencilColor.values()).mapToInt(PencilColor::bit).reduce(0, (left, right) -> left | right);

    public static final PencilCaseContents EMPTY = new PencilCaseContents(0);

    public PencilCaseContents {
        mask &= ALL_BITS;
    }

    public static PencilCaseContents of(int mask) {
        return mask == 0 ? EMPTY : new PencilCaseContents(mask);
    }

    public boolean isEmpty() {
        return this.mask == 0;
    }

    public boolean has(PencilColor color) {
        return (this.mask & color.bit()) != 0;
    }

    public PencilCaseContents with(PencilColor color) {
        return this.has(color) ? this : of(this.mask | color.bit());
    }

    public PencilCaseContents without(PencilColor color) {
        return this.has(color) ? of(this.mask & ~color.bit()) : this;
    }

    public Optional<PencilColor> firstColor() {
        return Arrays.stream(PencilColor.values()).filter(this::has).findFirst();
    }

    public List<PencilColor> colors() {
        return Arrays.stream(PencilColor.values()).filter(this::has).toList();
    }
}
