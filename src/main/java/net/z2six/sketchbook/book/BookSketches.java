package net.z2six.sketchbook.book;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.z2six.sketchbook.Sketchbook;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BookSketches {
    public static final String PAGE_MARKER = " ";

    private BookSketches() {
    }

    public static SketchPageEntry getEntry(ItemStack book, int pageIndex) {
        SketchBookData data = book.get(Sketchbook.BOOK_SKETCHES);
        return data == null ? null : data.get(pageIndex);
    }

    public static PageSketch getInlineSketch(ItemStack book, int pageIndex) {
        SketchPageEntry entry = getEntry(book, pageIndex);
        return entry == null ? null : entry.inlineSketch().orElse(null);
    }

    public static Optional<UUID> getSketchReference(ItemStack book, int pageIndex) {
        SketchPageEntry entry = getEntry(book, pageIndex);
        return entry == null ? Optional.empty() : entry.referenceId();
    }

    public static boolean hasSketch(ItemStack book, int pageIndex) {
        SketchBookData data = book.get(Sketchbook.BOOK_SKETCHES);
        return data != null && data.hasSketch(pageIndex);
    }

    public static void applyLegacySketch(ItemStack book, int pageIndex, PageSketch sketch) {
        List<String> pages = getPages(book, pageIndex);
        applyLegacySketch(book, pages, pageIndex, sketch);
    }

    public static void applyReference(ItemStack book, int pageIndex, UUID referenceId) {
        List<String> pages = getPages(book, pageIndex);
        applyReference(book, pages, pageIndex, referenceId);
    }

    public static void removeSketch(ItemStack book, int pageIndex) {
        List<String> pages = getPages(book, pageIndex);
        removeSketch(book, pages, pageIndex);
    }

    public static void applyLegacySketch(ItemStack book, List<String> pages, int pageIndex, PageSketch sketch) {
        applyEntry(book, pages, pageIndex, SketchPageEntry.legacy(sketch));
    }

    public static void applyReference(ItemStack book, List<String> pages, int pageIndex, UUID referenceId) {
        applyEntry(book, pages, pageIndex, SketchPageEntry.reference(referenceId));
    }

    public static void applyEntry(ItemStack book, List<String> pages, int pageIndex, SketchPageEntry entry) {
        ensurePageCapacity(pages, pageIndex);
        SketchBookData data = book.getOrDefault(Sketchbook.BOOK_SKETCHES, SketchBookData.EMPTY);
        book.set(Sketchbook.BOOK_SKETCHES, data.withEntry(pageIndex, entry));
        setPageMarker(pages, pageIndex, true);
        writePages(book, pages);
    }

    public static void removeSketch(ItemStack book, List<String> pages, int pageIndex) {
        ensurePageCapacity(pages, pageIndex);
        SketchBookData data = book.getOrDefault(Sketchbook.BOOK_SKETCHES, SketchBookData.EMPTY).withoutSketch(pageIndex);
        if (data.isEmpty()) {
            book.remove(Sketchbook.BOOK_SKETCHES);
        } else {
            book.set(Sketchbook.BOOK_SKETCHES, data);
        }
        setPageMarker(pages, pageIndex, false);
        writePages(book, pages);
    }

    public static boolean canSketchOnText(String pageText) {
        return pageText.isBlank();
    }

    public static boolean isMarker(String pageText) {
        return PAGE_MARKER.equals(pageText);
    }

    public static String getPageText(ItemStack book, int pageIndex) {
        WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (content == null) {
            return "";
        }
        List<String> pages = content.getPages(false).toList();
        if (pageIndex < 0 || pageIndex >= pages.size()) {
            return "";
        }
        return pages.get(pageIndex);
    }

    private static void setPageMarker(List<String> pages, int pageIndex, boolean present) {
        if (pageIndex < 0 || pageIndex >= pages.size()) {
            return;
        }

        String current = pages.get(pageIndex);
        if (present) {
            if (current.isBlank()) {
                pages.set(pageIndex, PAGE_MARKER);
            }
        } else if (isMarker(current)) {
            pages.set(pageIndex, "");
        }
    }

    private static List<String> getPages(ItemStack book, int targetPageIndex) {
        WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
        List<String> pages = content == null ? new ArrayList<>() : new ArrayList<>(content.getPages(false).toList());
        ensurePageCapacity(pages, targetPageIndex);
        return pages;
    }

    private static void writePages(ItemStack book, List<String> pages) {
        book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages.stream().map(Filterable::passThrough).toList()));
    }

    private static void ensurePageCapacity(List<String> pages, int targetPageIndex) {
        while (pages.size() <= targetPageIndex && pages.size() < 100) {
            pages.add("");
        }
    }
}
