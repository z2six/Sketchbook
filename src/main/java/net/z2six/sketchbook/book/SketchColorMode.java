package net.z2six.sketchbook.book;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.z2six.sketchbook.item.PencilColor;

public enum SketchColorMode {
    GRAPHITE("graphite", 0),
    ALL("all", PencilColor.WHITE.bit() | PencilColor.YELLOW.bit() | PencilColor.CYAN.bit() | PencilColor.MAGENTA.bit()),
    WHITE("white", PencilColor.WHITE.bit()),
    YELLOW("yellow", PencilColor.YELLOW.bit()),
    CYAN("cyan", PencilColor.CYAN.bit()),
    MAGENTA("magenta", PencilColor.MAGENTA.bit());

    public static final Codec<SketchColorMode> CODEC = Codec.STRING.comapFlatMap(SketchColorMode::byId, SketchColorMode::id);

    private final String id;
    private final int requiredPencilMask;

    SketchColorMode(String id, int requiredPencilMask) {
        this.id = id;
        this.requiredPencilMask = requiredPencilMask;
    }

    public String id() {
        return this.id;
    }

    public int requiredPencilMask() {
        return this.requiredPencilMask;
    }

    private static DataResult<SketchColorMode> byId(String id) {
        for (SketchColorMode mode : values()) {
            if (mode.id.equals(id)) {
                return DataResult.success(mode);
            }
        }
        return DataResult.error(() -> "Unknown sketch color mode: " + id);
    }
}
