package net.z2six.sketchbook;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.z2six.sketchbook.compat.curios.CuriosCompat;
import net.z2six.sketchbook.item.PencilCaseItem;
import net.z2six.sketchbook.item.PencilColor;

import java.util.Optional;

public final class SketchbookItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Sketchbook.MODID);

    public static final DeferredHolder<Item, Item> PENCIL = ITEMS.register("pencil", SketchbookItems::createPencilItem);
    public static final DeferredHolder<Item, Item> WHITE_PENCIL = ITEMS.register("white_pencil", SketchbookItems::createPencilItem);
    public static final DeferredHolder<Item, Item> YELLOW_PENCIL = ITEMS.register("yellow_pencil", SketchbookItems::createPencilItem);
    public static final DeferredHolder<Item, Item> CYAN_PENCIL = ITEMS.register("cyan_pencil", SketchbookItems::createPencilItem);
    public static final DeferredHolder<Item, Item> MAGENTA_PENCIL = ITEMS.register("magenta_pencil", SketchbookItems::createPencilItem);
    public static final DeferredHolder<Item, Item> PENCIL_CASE = ITEMS.register("pencil_case", () -> new PencilCaseItem(new Item.Properties().stacksTo(1)));

    private SketchbookItems() {
    }

    public static boolean hasPencil(Player player) {
        return getAvailablePencilMask(player) != 0;
    }

    public static boolean hasRequiredPencils(Player player, int requiredMask) {
        return requiredMask == 0 || (getAvailablePencilMask(player) & requiredMask) == requiredMask;
    }

    public static int getAvailableColoredPencilMask(Player player) {
        return getAvailablePencilMask(player) & PencilColor.coloredMask();
    }

    public static int getAvailablePencilMask(Player player) {
        if (player == null) {
            return 0;
        }

        int mask = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            mask |= getLoosePencilMask(stack);
            if (stack.is(PENCIL_CASE.get())) {
                mask |= PencilCaseItem.getStoredMask(stack);
            }
        }

        if (ModList.get().isLoaded(CuriosCompat.MODID)) {
            mask |= CuriosCompat.getEquippedPencilMask(player);
        }

        return mask;
    }

    public static int getLoosePencilMask(ItemStack stack) {
        return getPencilColor(stack).map(PencilColor::bit).orElse(0);
    }

    public static boolean isPencil(ItemStack stack) {
        return getPencilColor(stack).isPresent();
    }

    public static Optional<PencilColor> getPencilColor(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        if (stack.is(PENCIL.get())) {
            return Optional.of(PencilColor.GRAPHITE);
        }
        if (stack.is(WHITE_PENCIL.get())) {
            return Optional.of(PencilColor.WHITE);
        }
        if (stack.is(YELLOW_PENCIL.get())) {
            return Optional.of(PencilColor.YELLOW);
        }
        if (stack.is(CYAN_PENCIL.get())) {
            return Optional.of(PencilColor.CYAN);
        }
        if (stack.is(MAGENTA_PENCIL.get())) {
            return Optional.of(PencilColor.MAGENTA);
        }
        return Optional.empty();
    }

    public static ItemStack createPencilStack(PencilColor color) {
        return new ItemStack(getPencilItem(color));
    }

    public static Item getPencilItem(PencilColor color) {
        return switch (color) {
            case GRAPHITE -> PENCIL.get();
            case WHITE -> WHITE_PENCIL.get();
            case YELLOW -> YELLOW_PENCIL.get();
            case CYAN -> CYAN_PENCIL.get();
            case MAGENTA -> MAGENTA_PENCIL.get();
        };
    }

    private static Item createPencilItem() {
        return new Item(new Item.Properties());
    }
}
