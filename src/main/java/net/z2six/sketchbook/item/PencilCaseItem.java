package net.z2six.sketchbook.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.SketchbookItems;

import java.util.ArrayList;
import java.util.List;

public final class PencilCaseItem extends Item {
    public PencilCaseItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (stack.getCount() != 1 || !slot.allowModification(player)) {
            return false;
        }

        if (other.isEmpty()) {
            if (action != ClickAction.SECONDARY) {
                return false;
            }

            return this.removeOne(stack, extracted -> {
                this.playRemoveSound(player);
                access.set(extracted);
            });
        }

        return this.insert(stack, other, player);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        if (stack.getCount() != 1 || !slot.allowModification(player)) {
            return false;
        }

        ItemStack slotStack = slot.getItem();
        if (slotStack.isEmpty()) {
            if (action != ClickAction.SECONDARY) {
                return false;
            }

            return this.removeOne(stack, extracted -> {
                this.playRemoveSound(player);
                ItemStack remainder = slot.safeInsert(extracted);
                if (!remainder.isEmpty()) {
                    this.insertDirect(stack, remainder);
                }
            });
        }

        PencilColor color = SketchbookItems.getPencilColor(slotStack).orElse(null);
        if (color == null) {
            return false;
        }

        PencilCaseContents contents = getContents(stack);
        if (contents.has(color)) {
            return false;
        }

        ItemStack extracted = slot.safeTake(1, 1, player);
        if (extracted.isEmpty()) {
            return false;
        }

        setContents(stack, contents.with(color));
        this.playInsertSound(player);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        PencilCaseContents contents = getContents(stack);
        if (contents.isEmpty()) {
            tooltipComponents.add(Component.translatable("item.sketchbook.pencil_case.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltipComponents.add(Component.translatable("item.sketchbook.pencil_case.contains").withStyle(ChatFormatting.GRAY));
        for (PencilColor color : contents.colors()) {
            tooltipComponents.add(Component.translatable("item.sketchbook.pencil_case.entry", SketchbookItems.createPencilStack(color).getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        PencilCaseContents contents = getContents(itemEntity.getItem());
        if (contents.isEmpty()) {
            return;
        }

        itemEntity.getItem().remove(Sketchbook.PENCIL_CASE_CONTENTS);
        List<ItemStack> pencils = new ArrayList<>();
        for (PencilColor color : contents.colors()) {
            pencils.add(SketchbookItems.createPencilStack(color));
        }
        ItemUtils.onContainerDestroyed(itemEntity, pencils);
    }

    public static PencilCaseContents getContents(ItemStack stack) {
        return PencilCaseContents.of(stack.getOrDefault(Sketchbook.PENCIL_CASE_CONTENTS, 0));
    }

    public static int getStoredMask(ItemStack stack) {
        return getContents(stack).mask();
    }

    private boolean insert(ItemStack caseStack, ItemStack other, Player player) {
        PencilColor color = SketchbookItems.getPencilColor(other).orElse(null);
        if (color == null) {
            return false;
        }

        PencilCaseContents contents = getContents(caseStack);
        if (contents.has(color)) {
            return false;
        }

        other.shrink(1);
        setContents(caseStack, contents.with(color));
        this.playInsertSound(player);
        return true;
    }

    private void insertDirect(ItemStack caseStack, ItemStack other) {
        PencilColor color = SketchbookItems.getPencilColor(other).orElse(null);
        if (color == null) {
            return;
        }

        PencilCaseContents contents = getContents(caseStack);
        if (!contents.has(color)) {
            setContents(caseStack, contents.with(color));
        }
    }

    private boolean removeOne(ItemStack caseStack, java.util.function.Consumer<ItemStack> consumer) {
        PencilCaseContents contents = getContents(caseStack);
        PencilColor color = contents.firstColor().orElse(null);
        if (color == null) {
            return false;
        }

        setContents(caseStack, contents.without(color));
        consumer.accept(SketchbookItems.createPencilStack(color));
        return true;
    }

    private static void setContents(ItemStack stack, PencilCaseContents contents) {
        if (contents.isEmpty()) {
            stack.remove(Sketchbook.PENCIL_CASE_CONTENTS);
        } else {
            stack.set(Sketchbook.PENCIL_CASE_CONTENTS, contents.mask());
        }
    }

    private void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private void playRemoveSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }
}
