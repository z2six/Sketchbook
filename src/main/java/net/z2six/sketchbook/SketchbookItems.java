package net.z2six.sketchbook;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.z2six.sketchbook.compat.curios.CuriosCompat;
import net.z2six.sketchbook.item.PencilCaseItem;
import net.z2six.sketchbook.item.PencilColor;

import java.util.EnumMap;
import java.util.Optional;
import java.util.UUID;

public final class SketchbookItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Sketchbook.MODID);

    private static final EnumMap<PencilColor, DeferredHolder<Item, Item>> PENCILS = new EnumMap<>(PencilColor.class);

    public static final DeferredHolder<Item, Item> PENCIL = registerPencil(PencilColor.GRAPHITE);
    public static final DeferredHolder<Item, Item> WHITE_PENCIL = registerPencil(PencilColor.WHITE);
    public static final DeferredHolder<Item, Item> YELLOW_PENCIL = registerPencil(PencilColor.YELLOW);
    public static final DeferredHolder<Item, Item> CYAN_PENCIL = registerPencil(PencilColor.CYAN);
    public static final DeferredHolder<Item, Item> MAGENTA_PENCIL = registerPencil(PencilColor.MAGENTA);
    public static final DeferredHolder<Item, Item> ORANGE_PENCIL = registerPencil(PencilColor.ORANGE);
    public static final DeferredHolder<Item, Item> LIGHT_BLUE_PENCIL = registerPencil(PencilColor.LIGHT_BLUE);
    public static final DeferredHolder<Item, Item> LIME_PENCIL = registerPencil(PencilColor.LIME);
    public static final DeferredHolder<Item, Item> PINK_PENCIL = registerPencil(PencilColor.PINK);
    public static final DeferredHolder<Item, Item> GRAY_PENCIL = registerPencil(PencilColor.GRAY);
    public static final DeferredHolder<Item, Item> LIGHT_GRAY_PENCIL = registerPencil(PencilColor.LIGHT_GRAY);
    public static final DeferredHolder<Item, Item> PURPLE_PENCIL = registerPencil(PencilColor.PURPLE);
    public static final DeferredHolder<Item, Item> BLUE_PENCIL = registerPencil(PencilColor.BLUE);
    public static final DeferredHolder<Item, Item> BROWN_PENCIL = registerPencil(PencilColor.BROWN);
    public static final DeferredHolder<Item, Item> GREEN_PENCIL = registerPencil(PencilColor.GREEN);
    public static final DeferredHolder<Item, Item> RED_PENCIL = registerPencil(PencilColor.RED);
    public static final DeferredHolder<Item, Item> BLACK_PENCIL = registerPencil(PencilColor.BLACK);
    public static final DeferredHolder<Item, Item> PENCIL_CASE = ITEMS.register("pencil_case", () -> new PencilCaseItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> TORN_SKETCH = ITEMS.register("torn_sketch", () -> new Item(new Item.Properties().stacksTo(1)));

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

        for (var entry : PENCILS.entrySet()) {
            if (stack.is(entry.getValue().get())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public static ItemStack createPencilStack(PencilColor color) {
        return new ItemStack(getPencilItem(color));
    }

    public static ItemStack createTornSketch(UUID referenceId) {
        ItemStack stack = new ItemStack(TORN_SKETCH.get());
        stack.set(Sketchbook.TORN_SKETCH_REFERENCE, referenceId);
        return stack;
    }

    public static Optional<UUID> getTornSketchReference(ItemStack stack) {
        if (!stack.is(TORN_SKETCH.get())) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(Sketchbook.TORN_SKETCH_REFERENCE));
    }

    public static boolean isTornSketch(ItemStack stack) {
        return stack.is(TORN_SKETCH.get()) && stack.has(Sketchbook.TORN_SKETCH_REFERENCE);
    }

    public static Item getPencilItem(PencilColor color) {
        return PENCILS.get(color).get();
    }

    public static void acceptCreativeItems(CreativeModeTab.Output output) {
        for (PencilColor color : PencilColor.values()) {
            output.accept(getPencilItem(color));
        }
        output.accept(PENCIL_CASE.get());
        output.accept(TORN_SKETCH.get());
    }

    private static DeferredHolder<Item, Item> registerPencil(PencilColor color) {
        DeferredHolder<Item, Item> holder = ITEMS.register(color.itemId(), SketchbookItems::createPencilItem);
        PENCILS.put(color, holder);
        return holder;
    }

    private static Item createPencilItem() {
        return new Item(new Item.Properties());
    }
}
