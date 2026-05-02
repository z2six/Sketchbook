package net.z2six.sketchbook.item;

public enum PencilColor {
    GRAPHITE,
    WHITE,
    YELLOW,
    CYAN,
    MAGENTA;

    public int bit() {
        return 1 << this.ordinal();
    }

    public boolean isColored() {
        return this != GRAPHITE;
    }

    public static int coloredMask() {
        int mask = 0;
        for (PencilColor color : values()) {
            if (color.isColored()) {
                mask |= color.bit();
            }
        }
        return mask;
    }
}
