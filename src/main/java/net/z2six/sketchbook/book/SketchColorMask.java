package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import net.z2six.sketchbook.item.PencilColor;

public final class SketchColorMask {
    public static final int NONE = 0;
    public static final int WHITE = PencilColor.WHITE.bit();
    public static final int YELLOW = PencilColor.YELLOW.bit();
    public static final int CYAN = PencilColor.CYAN.bit();
    public static final int MAGENTA = PencilColor.MAGENTA.bit();
    public static final int ALL = WHITE | YELLOW | CYAN | MAGENTA;
    public static final Codec<Integer> CODEC = Codec.INT.xmap(SketchColorMask::normalize, SketchColorMask::normalize);

    private SketchColorMask() {
    }

    public static int normalize(int mask) {
        return mask & ALL;
    }

    public static boolean isSelected(int mask, PencilColor color) {
        return color != PencilColor.GRAPHITE && (normalize(mask) & color.bit()) != 0;
    }

    public static int withColor(int mask, PencilColor color, boolean selected) {
        if (color == PencilColor.GRAPHITE) {
            return normalize(mask);
        }
        return selected ? normalize(mask) | color.bit() : normalize(mask) & ~color.bit();
    }
}
