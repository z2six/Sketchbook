package net.z2six.sketchbook.compat.curios;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.InterModComms;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypeMessage;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import net.z2six.sketchbook.SketchbookItems;
import net.z2six.sketchbook.item.PencilCaseItem;

public final class CuriosCompat {
    public static final String MODID = "curios";
    private static boolean initialized;

    private CuriosCompat() {
    }

    public static void enqueueImc() {
        InterModComms.sendTo(MODID, SlotTypeMessage.REGISTER_TYPE, () -> new SlotTypeMessage.Builder("pencil_case")
            .icon(ResourceLocation.fromNamespaceAndPath("sketchbook", "slot/pencil_case_curios"))
            .size(1)
            .build());
        if (!initialized) {
            CuriosApi.registerCurio(SketchbookItems.PENCIL_CASE.get(), new ICurioItem() {
            });
            initialized = true;
        }
    }

    public static int getEquippedPencilMask(Player player) {
        int mask = 0;
        for (SlotResult result : CuriosApi.getCuriosInventory(player)
            .map(handler -> handler.findCurios(CuriosCompat::isPencilCase))
            .orElseGet(java.util.List::of)) {
            mask |= PencilCaseItem.getStoredMask(result.stack());
        }
        return mask;
    }

    private static boolean isPencilCase(ItemStack stack) {
        return stack.is(SketchbookItems.PENCIL_CASE.get());
    }
}
