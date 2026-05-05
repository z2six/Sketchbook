package net.z2six.sketchbook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.z2six.sketchbook.book.SignedBookDates;

public final class SketchbookCommands {
    private static final int OP_PERMISSION_LEVEL = 2;

    private SketchbookCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("sketchbook")
                .then(Commands.literal("date")
                    .requires(source -> source.hasPermission(OP_PERMISSION_LEVEL))
                    .then(Commands.argument("date", StringArgumentType.greedyString())
                        .executes(context -> setSignedDate(
                            context.getSource(),
                            StringArgumentType.getString(context, "date")
                        ))
                    )
                )
        );
    }

    private static int setSignedDate(CommandSourceStack source, String date) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String normalizedDate = date.strip();
        if (normalizedDate.isEmpty()) {
            source.sendFailure(net.minecraft.network.chat.Component.translatable("commands.sketchbook.date.empty"));
            return 0;
        }

        ItemStack book = player.getItemInHand(InteractionHand.MAIN_HAND);
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (!SignedBookDates.canHaveSignedDate(book)) {
            book = player.getItemInHand(InteractionHand.OFF_HAND);
            hand = InteractionHand.OFF_HAND;
        }

        if (!SignedBookDates.canHaveSignedDate(book)) {
            source.sendFailure(net.minecraft.network.chat.Component.translatable("commands.sketchbook.date.no_book"));
            return 0;
        }

        SignedBookDates.setSignedDate(book, normalizedDate);
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        String handName = hand == InteractionHand.MAIN_HAND ? "main hand" : "off hand";
        source.sendSuccess(
            () -> net.minecraft.network.chat.Component.translatable("commands.sketchbook.date.success", normalizedDate, handName),
            true
        );
        return 1;
    }
}
