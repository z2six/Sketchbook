package net.z2six.sketchbook.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SketchContextMenu {
    private static final int ROW_HEIGHT = 18;
    private static final int MAX_SUBMENU_ROWS = 5;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SUBMENU_ARROW_PADDING = 14;

    private List<Entry> entries = List.of();
    private int left;
    private int top;
    private int width;
    private int screenWidth;
    private int screenHeight;
    private int openSubmenuIndex = -1;
    private int submenuScrollOffset;

    public void open(List<Entry> entries, Font font, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        this.entries = List.copyOf(entries);
        this.width = menuWidth(this.entries, font, false);
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.left = Mth.clamp(mouseX, 4, screenWidth - this.width - 4);
        this.top = Mth.clamp(mouseY, 4, screenHeight - this.entries.size() * ROW_HEIGHT - 4);
        this.openSubmenuIndex = -1;
        this.submenuScrollOffset = 0;
    }

    public void refresh(List<Entry> entries, Font font, int screenWidth, int screenHeight) {
        this.entries = List.copyOf(entries);
        this.width = menuWidth(this.entries, font, false);
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.left = Mth.clamp(this.left, 4, screenWidth - this.width - 4);
        this.top = Mth.clamp(this.top, 4, screenHeight - this.entries.size() * ROW_HEIGHT - 4);
        if (this.openSubmenuIndex < 0 || this.openSubmenuIndex >= this.entries.size() || this.entries.get(this.openSubmenuIndex).children().isEmpty()) {
            this.openSubmenuIndex = -1;
            this.submenuScrollOffset = 0;
        } else {
            this.submenuScrollOffset = clampSubmenuScroll(this.entries.get(this.openSubmenuIndex).children(), this.submenuScrollOffset);
        }
    }

    public void clear() {
        this.entries = List.of();
        this.openSubmenuIndex = -1;
        this.submenuScrollOffset = 0;
    }

    public boolean isVisible() {
        return !this.entries.isEmpty();
    }

    public boolean contains(double mouseX, double mouseY, Font font, int screenWidth) {
        if (!this.isVisible()) {
            return false;
        }

        if (this.containsRoot(mouseX, mouseY)) {
            return true;
        }

        Submenu submenu = this.getActiveSubmenu(mouseX, mouseY, font);
        return submenu != null && submenu.contains(mouseX, mouseY);
    }

    public boolean click(double mouseX, double mouseY, Font font, int screenWidth) {
        if (!this.isVisible()) {
            return false;
        }

        int rootIndex = this.rootIndexAt(mouseX, mouseY);
        if (rootIndex >= 0) {
            Entry entry = this.entries.get(rootIndex);
            if (entry.children().isEmpty()) {
                if (entry.active()) {
                    entry.action().run();
                }
                if (entry.closeOnClick()) {
                    this.clear();
                }
                return true;
            }

            this.setOpenSubmenuIndex(rootIndex);
            return true;
        }

        Submenu submenu = this.getActiveSubmenu(mouseX, mouseY, font);
        if (submenu != null) {
            int submenuIndex = submenu.indexAt(mouseX, mouseY);
            if (submenuIndex >= 0) {
                Entry child = submenu.entries().get(submenuIndex);
                if (child.active()) {
                    child.action().run();
                }
                if (child.closeOnClick()) {
                    this.clear();
                }
                return true;
            }
        }

        this.clear();
        return false;
    }

    public boolean scroll(double mouseX, double mouseY, double deltaY, Font font) {
        if (!this.isVisible()) {
            return false;
        }

        Submenu submenu = this.getActiveSubmenu(mouseX, mouseY, font);
        if (submenu == null || !submenu.contains(mouseX, mouseY) || !submenu.canScroll() || deltaY == 0.0D) {
            return false;
        }

        int nextOffset = this.submenuScrollOffset + (deltaY < 0.0D ? 1 : -1);
        int clampedOffset = clampSubmenuScroll(submenu.entries(), nextOffset);
        if (clampedOffset != this.submenuScrollOffset) {
            this.submenuScrollOffset = clampedOffset;
        }
        return true;
    }

    public Optional<UUID> hoveredMemoryId(double mouseX, double mouseY, Font font) {
        if (!this.isVisible()) {
            return Optional.empty();
        }

        int rootIndex = this.rootIndexAt(mouseX, mouseY);
        if (rootIndex >= 0) {
            return this.entries.get(rootIndex).memoryId();
        }

        Submenu submenu = this.getActiveSubmenu(mouseX, mouseY, font);
        if (submenu != null) {
            int submenuIndex = submenu.indexAt(mouseX, mouseY);
            if (submenuIndex >= 0) {
                return submenu.entries().get(submenuIndex).memoryId();
            }
        }
        return Optional.empty();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, int screenWidth) {
        if (!this.isVisible()) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        this.renderMenu(graphics, font, this.entries, this.left, this.top, this.width, 0, this.entries.size(), mouseX, mouseY, true, false);

        Submenu submenu = this.getActiveSubmenu(mouseX, mouseY, font);
        if (submenu != null) {
            this.renderMenu(
                graphics,
                font,
                submenu.entries(),
                submenu.left(),
                submenu.top(),
                submenu.width(),
                submenu.scrollOffset(),
                submenu.visibleRowCount(),
                mouseX,
                mouseY,
                false,
                submenu.canScroll()
            );
        }
        graphics.pose().popPose();
    }

    private void renderMenu(GuiGraphics graphics, Font font, List<Entry> entries, int left, int top, int width, int startIndex, int visibleRowCount, int mouseX, int mouseY, boolean drawArrow, boolean drawScrollbar) {
        int contentWidth = drawScrollbar ? width - SCROLLBAR_WIDTH - 2 : width;
        graphics.fill(left, top, left + width, top + visibleRowCount * ROW_HEIGHT, 0xF4F0E6CF);
        graphics.renderOutline(left, top, width, visibleRowCount * ROW_HEIGHT, 0x7F302718);

        for (int row = 0; row < visibleRowCount; row++) {
            int index = startIndex + row;
            Entry entry = entries.get(index);
            int rowTop = top + row * ROW_HEIGHT;
            boolean hovered = mouseX >= left && mouseX < left + contentWidth && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
            if (hovered) {
                graphics.fill(left + 1, rowTop + 1, left + contentWidth - 1, rowTop + ROW_HEIGHT - 1, 0x30A58F6A);
            }
            graphics.drawString(font, entry.label(), left + 6, rowTop + 5, entry.active() ? 0x3A342E : 0x7A756D, false);
            if (drawArrow && !entry.children().isEmpty()) {
                graphics.drawString(font, Component.translatable("menu.sketchbook.submenu_arrow"), left + contentWidth - 10, rowTop + 5, entry.active() ? 0x3A342E : 0x7A756D, false);
            }
        }

        if (drawScrollbar) {
            int trackLeft = left + width - SCROLLBAR_WIDTH - 1;
            int trackTop = top + 1;
            int trackHeight = visibleRowCount * ROW_HEIGHT - 2;
            graphics.fill(trackLeft, trackTop, trackLeft + SCROLLBAR_WIDTH, trackTop + trackHeight, 0x302B241C);

            int maxOffset = entries.size() - visibleRowCount;
            int thumbHeight = Math.max(10, Math.round((visibleRowCount / (float)entries.size()) * trackHeight));
            int thumbTravel = Math.max(0, trackHeight - thumbHeight);
            int thumbOffset = maxOffset <= 0 ? 0 : Math.round((startIndex / (float)maxOffset) * thumbTravel);
            graphics.fill(trackLeft + 1, trackTop + thumbOffset, trackLeft + SCROLLBAR_WIDTH - 1, trackTop + thumbOffset + thumbHeight, 0x9073604A);
        }
    }

    private boolean containsRoot(double mouseX, double mouseY) {
        return mouseX >= this.left && mouseX < this.left + this.width && mouseY >= this.top && mouseY < this.top + this.entries.size() * ROW_HEIGHT;
    }

    private int rootIndexAt(double mouseX, double mouseY) {
        if (!this.containsRoot(mouseX, mouseY)) {
            return -1;
        }
        return (int)((mouseY - this.top) / ROW_HEIGHT);
    }

    private Submenu getActiveSubmenu(double mouseX, double mouseY, Font font) {
        int rootIndex = this.rootIndexAt(mouseX, mouseY);
        if (rootIndex >= 0) {
            Entry rootEntry = this.entries.get(rootIndex);
            if (!rootEntry.children().isEmpty()) {
                this.setOpenSubmenuIndex(rootIndex);
                return this.createSubmenu(rootIndex, font);
            }
            return null;
        }

        if (this.openSubmenuIndex >= 0) {
            return this.createSubmenu(this.openSubmenuIndex, font);
        }

        return null;
    }

    private void setOpenSubmenuIndex(int submenuIndex) {
        if (this.openSubmenuIndex != submenuIndex) {
            this.openSubmenuIndex = submenuIndex;
            this.submenuScrollOffset = 0;
        }
    }

    private Submenu createSubmenu(int rootIndex, Font font) {
        if (rootIndex < 0 || rootIndex >= this.entries.size()) {
            return null;
        }

        Entry root = this.entries.get(rootIndex);
        if (!root.active() || root.children().isEmpty()) {
            return null;
        }

        List<Entry> children = root.children();
        int visibleRowCount = Math.min(children.size(), MAX_SUBMENU_ROWS);
        int scrollOffset = clampSubmenuScroll(children, this.submenuScrollOffset);
        int submenuWidth = menuWidth(children, font, children.size() > visibleRowCount);
        int preferredRightLeft = this.left + this.width + 2;
        int preferredLeftLeft = this.left - submenuWidth - 2;
        int submenuLeft;
        if (preferredRightLeft + submenuWidth <= this.screenWidth - 4) {
            submenuLeft = preferredRightLeft;
        } else if (preferredLeftLeft >= 4) {
            submenuLeft = preferredLeftLeft;
        } else {
            submenuLeft = Mth.clamp(preferredRightLeft, 4, this.screenWidth - submenuWidth - 4);
        }

        int submenuTop = Mth.clamp(this.top + rootIndex * ROW_HEIGHT, 4, this.screenHeight - visibleRowCount * ROW_HEIGHT - 4);
        return new Submenu(children, submenuLeft, submenuTop, submenuWidth, scrollOffset, visibleRowCount);
    }

    private static int menuWidth(List<Entry> entries, Font font, boolean hasScrollbar) {
        int baseWidth = entries.stream().mapToInt(entry -> {
            int width = font.width(entry.label()) + 12;
            if (!entry.children().isEmpty()) {
                width += SUBMENU_ARROW_PADDING;
            }
            return width;
        }).max().orElse(60);
        return hasScrollbar ? baseWidth + SCROLLBAR_WIDTH + 4 : baseWidth;
    }

    private static int clampSubmenuScroll(List<Entry> children, int scrollOffset) {
        int maxOffset = Math.max(0, children.size() - MAX_SUBMENU_ROWS);
        return Mth.clamp(scrollOffset, 0, maxOffset);
    }

    public record Entry(Component label, boolean active, Runnable action, List<Entry> children, boolean closeOnClick, Optional<UUID> memoryId) {
        public static Entry action(Component label, boolean active, Runnable action) {
            return new Entry(label, active, action, List.of(), true, Optional.empty());
        }

        public static Entry memoryAction(Component label, boolean active, UUID memoryId, Runnable action) {
            return new Entry(label, active, action, List.of(), true, Optional.of(memoryId));
        }

        public static Entry stickyAction(Component label, boolean active, Runnable action) {
            return new Entry(label, active, action, List.of(), false, Optional.empty());
        }

        public static Entry submenu(Component label, boolean active, List<Entry> children) {
            return new Entry(label, active, () -> { }, children, false, Optional.empty());
        }
    }

    private record Submenu(List<Entry> entries, int left, int top, int width, int scrollOffset, int visibleRowCount) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.left && mouseX < this.left + this.width && mouseY >= this.top && mouseY < this.top + this.visibleRowCount * ROW_HEIGHT;
        }

        private int indexAt(double mouseX, double mouseY) {
            if (!this.contains(mouseX, mouseY)) {
                return -1;
            }

            int localIndex = (int)((mouseY - this.top) / ROW_HEIGHT);
            int globalIndex = this.scrollOffset + localIndex;
            return globalIndex >= 0 && globalIndex < this.entries.size() ? globalIndex : -1;
        }

        private boolean canScroll() {
            return this.entries.size() > this.visibleRowCount;
        }
    }
}
