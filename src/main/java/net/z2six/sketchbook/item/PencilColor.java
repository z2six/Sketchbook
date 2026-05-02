package net.z2six.sketchbook.item;

import java.util.List;

public enum PencilColor {
    GRAPHITE("pencil", 1 << 0, 58, 56, 54, false),
    WHITE("white_pencil", 1 << 1, 247, 246, 240, true),
    YELLOW("yellow_pencil", 1 << 2, 200, 173, 77, true),
    CYAN("cyan_pencil", 1 << 3, 81, 157, 177, true),
    MAGENTA("magenta_pencil", 1 << 4, 182, 86, 145, true),
    ORANGE("orange_pencil", 1 << 5, 214, 130, 62, true),
    LIGHT_BLUE("light_blue_pencil", 1 << 6, 120, 174, 212, true),
    LIME("lime_pencil", 1 << 7, 140, 185, 68, true),
    PINK("pink_pencil", 1 << 8, 219, 152, 179, true),
    GRAY("gray_pencil", 1 << 9, 106, 106, 112, true),
    LIGHT_GRAY("light_gray_pencil", 1 << 10, 176, 176, 182, true),
    PURPLE("purple_pencil", 1 << 11, 115, 82, 157, true),
    BLUE("blue_pencil", 1 << 12, 74, 98, 184, true),
    BROWN("brown_pencil", 1 << 13, 112, 77, 56, true),
    GREEN("green_pencil", 1 << 14, 88, 131, 69, true),
    RED("red_pencil", 1 << 15, 182, 74, 74, true),
    BLACK("black_pencil", 1 << 16, 26, 24, 27, true);

    private static final List<PencilColor> MENU_COLORS = List.of(
        WHITE,
        ORANGE,
        MAGENTA,
        LIGHT_BLUE,
        YELLOW,
        LIME,
        PINK,
        GRAY,
        LIGHT_GRAY,
        CYAN,
        PURPLE,
        BLUE,
        BROWN,
        GREEN,
        RED,
        BLACK
    );

    private final String itemId;
    private final int bit;
    private final int red;
    private final int green;
    private final int blue;
    private final boolean sketchColor;

    PencilColor(String itemId, int bit, int red, int green, int blue, boolean sketchColor) {
        this.itemId = itemId;
        this.bit = bit;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.sketchColor = sketchColor;
    }

    public String itemId() {
        return this.itemId;
    }

    public String translationKey() {
        return "item.sketchbook." + this.itemId;
    }

    public int bit() {
        return this.bit;
    }

    public int red() {
        return this.red;
    }

    public int green() {
        return this.green;
    }

    public int blue() {
        return this.blue;
    }

    public boolean isSketchColor() {
        return this.sketchColor;
    }

    public static List<PencilColor> menuColors() {
        return MENU_COLORS;
    }

    public static int coloredMask() {
        int mask = 0;
        for (PencilColor color : values()) {
            if (color.isSketchColor()) {
                mask |= color.bit();
            }
        }
        return mask;
    }
}
