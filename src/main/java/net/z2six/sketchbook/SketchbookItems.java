package net.z2six.sketchbook;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SketchbookItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Sketchbook.MODID);
    public static final DeferredHolder<Item, Item> PENCIL = ITEMS.register("pencil", () -> new Item(new Item.Properties()));

    private SketchbookItems() {
    }

    public static boolean hasPencil(Player player) {
        if (player == null) {
            return false;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(PENCIL.get())) {
                return true;
            }
        }

        return false;
    }
}
