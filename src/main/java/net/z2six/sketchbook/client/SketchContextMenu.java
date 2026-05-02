package net.z2six.sketchbook.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public final class SketchContextMenu {
    private static final int ROW_HEIGHT = 18;

    private List<Entry> entries = List.of();
    private int left;
    private int top;
    private int width;
    private int screenWidth;
    private int screenHeight;
    private int openSubmenuIndex = -1;

    public void open(List<Entry> entries, Font font, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        this.entries = List.copyOf(entries);
        this.width = this.entries.stream().mapToInt(entry -> font.width(entry.label()) + 12).max().orElse(60);
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.left = Mth.clamp(mouseX, 4, screenWidth - this.width - 4);
        this.top = Mth.clamp(mouseY, 4, screenHeight - this.entries.size() * ROW_HEIGHT - 4);
        this.openSubmenuIndex = -1;
    }

    public void refresh(List<Entry> entries, Font font, int screenWidth, int screenHeight) {
        this.entries = List.copyOf(entries);
        this.width = this.entries.stream().mapToInt(entry -> font.width(entry.label()) + 12).max().orElse(60);
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.left = Mth.clamp(this.left, 4, screenWidth - this.width - 4);
        this.top = Mth.clamp(this.top, 4, screenHeight - this.entries.size() * ROW_HEIGHT - 4);
        if (this.openSubmenuIndex >= this.entries.size() || this.openSubmenuIndex < 0 || this.entries.get(this.openSubmenuIndex).children().isEmpty()) {
            this.openSubmenuIndex = -1;
        }
    }

    public void clear() {
        this.entries = List.of();
        this.openSubmenuIndex = -1;
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

            this.openSubmenuIndex = rootIndex;
            Submenu submenu = this.createSubmenu(rootIndex, font);
            if (submenu == null) {
                this.clear();
                return true;
            }

            int submenuIndex = submenu.indexAt(mouseX, mouseY);
            if (submenuIndex >= 0) {
                Entry child = entry.children().get(submenuIndex);
                if (child.active()) {
                    child.action().run();
                }
                if (child.closeOnClick()) {
                    this.clear();
                }
                return true;
            }
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

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, int screenWidth) {
        if (!this.isVisible()) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        this.renderMenu(graphics, font, this.entries, this.left, this.top, this.width, mouseX, mouseY, true);

        Submenu submenu = this.getActiveSubmenu(mouseX, mouseY, font);
        if (submenu != null) {
            this.renderMenu(graphics, font, submenu.entries(), submenu.left(), submenu.top(), submenu.width(), mouseX, mouseY, false);
        }
        graphics.pose().popPose();
    }

    private void renderMenu(GuiGraphics graphics, Font font, List<Entry> entries, int left, int top, int width, int mouseX, int mouseY, boolean drawArrow) {
        graphics.fill(left, top, left + width, top + entries.size() * ROW_HEIGHT, 0xF4F0E6CF);
        graphics.renderOutline(left, top, width, entries.size() * ROW_HEIGHT, 0x7F302718);

        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            int rowTop = top + index * ROW_HEIGHT;
            boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
            if (hovered) {
                graphics.fill(left + 1, rowTop + 1, left + width - 1, rowTop + ROW_HEIGHT - 1, 0x30A58F6A);
            }
            graphics.drawString(font, entry.label(), left + 6, rowTop + 5, entry.active() ? 0x3A342E : 0x7A756D, false);
            if (drawArrow && !entry.children().isEmpty()) {
                graphics.drawString(font, Component.literal(">"), left + width - 10, rowTop + 5, entry.active() ? 0x3A342E : 0x7A756D, false);
            }
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
            this.openSubmenuIndex = rootIndex;
            return this.createSubmenu(rootIndex, font);
        }

        if (this.openSubmenuIndex >= 0) {
            Submenu submenu = this.createSubmenu(this.openSubmenuIndex, font);
            if (submenu != null) {
                if (submenu.contains(mouseX, mouseY)) {
                    return submenu;
                }
                return submenu;
            }
        }

        for (int index = 0; index < this.entries.size(); index++) {
            Submenu submenu = this.createSubmenu(index, font);
            if (submenu != null && submenu.contains(mouseX, mouseY)) {
                this.openSubmenuIndex = index;
                return submenu;
            }
        }
        return null;
    }

    private Submenu createSubmenu(int rootIndex, Font font) {
        if (rootIndex < 0 || rootIndex >= this.entries.size()) {
            return null;
        }
        Entry root = this.entries.get(rootIndex);
        if (!root.active() || root.children().isEmpty()) {
            return null;
        }

        int submenuWidth = root.children().stream().mapToInt(entry -> font.width(entry.label()) + 12).max().orElse(this.width);
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
        int submenuTop = Mth.clamp(this.top + rootIndex * ROW_HEIGHT, 4, this.screenHeight - root.children().size() * ROW_HEIGHT - 4);
        return new Submenu(root.children(), submenuLeft, submenuTop, submenuWidth);
    }

    public record Entry(Component label, boolean active, Runnable action, List<Entry> children, boolean closeOnClick) {
        public static Entry action(Component label, boolean active, Runnable action) {
            return new Entry(label, active, action, List.of(), true);
        }

        public static Entry stickyAction(Component label, boolean active, Runnable action) {
            return new Entry(label, active, action, List.of(), false);
        }

        public static Entry submenu(Component label, boolean active, List<Entry> children) {
            return new Entry(label, active, () -> { }, children, false);
        }
    }

    private record Submenu(List<Entry> entries, int left, int top, int width) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.left && mouseX < this.left + this.width && mouseY >= this.top && mouseY < this.top + this.entries.size() * ROW_HEIGHT;
        }

        private int indexAt(double mouseX, double mouseY) {
            if (!this.contains(mouseX, mouseY)) {
                return -1;
            }
            return (int)((mouseY - this.top) / ROW_HEIGHT);
        }
    }
}
