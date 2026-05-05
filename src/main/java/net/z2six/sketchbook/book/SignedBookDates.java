package net.z2six.sketchbook.book;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.z2six.sketchbook.Sketchbook;
import net.z2six.sketchbook.SketchbookLog;

import java.lang.reflect.Method;
import java.util.Optional;

public final class SignedBookDates {
    private static final String RPG_TIMELINE_MOD_ID = "rpgtimeline";
    private static final String RPG_TIMELINE_API_CLASS = "org.z2six.rpgtimeline.api.RPGTimelineApi";

    private SignedBookDates() {
    }

    public static Optional<String> currentDateString(ServerPlayer player) {
        if (player == null || !ModList.get().isLoaded(RPG_TIMELINE_MOD_ID)) {
            SketchbookLog.infoOnce(
                "signed_book_date_rpgtimeline_missing",
                "Sketchbook skipped signed-book date stamping because Timeline (mod id '{}') is not loaded.",
                RPG_TIMELINE_MOD_ID
            );
            return Optional.empty();
        }

        return currentDateString(player.level().getDayTime());
    }

    public static Optional<String> currentDateString(long gameTime) {
        if (!ModList.get().isLoaded(RPG_TIMELINE_MOD_ID)) {
            return Optional.empty();
        }

        try {
            Class<?> apiClass = Class.forName(RPG_TIMELINE_API_CLASS);
            Method method = apiClass.getMethod("buildDateStringFromGameTime", long.class);
            Object result = method.invoke(null, gameTime);
            if (result instanceof String date && !date.isBlank()) {
                return Optional.of(date);
            }
        } catch (Throwable t) {
            SketchbookLog.LOGGER.warn("Sketchbook could not read the RPG Calendar date for a signed book.", t);
        }

        return Optional.empty();
    }

    public static void stampSignedDate(ItemStack stack, ServerPlayer player) {
        if (!stack.is(Items.WRITTEN_BOOK)) {
            return;
        }

        currentDateString(player).ifPresent(date -> {
            stack.set(Sketchbook.SIGNED_DATE, date);
            SketchbookLog.infoOnce("signed_book_date_stamped", "Sketchbook stamped signed-book date '{}'.", date);
        });
    }

    public static Optional<String> getSignedDate(ItemStack stack) {
        if (!stack.is(Items.WRITTEN_BOOK)) {
            return Optional.empty();
        }

        String date = stack.get(Sketchbook.SIGNED_DATE);
        return date == null || date.isBlank() ? Optional.empty() : Optional.of(date);
    }

    public static Component tooltipLine(String date) {
        return Component.translatable("item.sketchbook.signed_date", date).withStyle(ChatFormatting.GRAY);
    }
}
