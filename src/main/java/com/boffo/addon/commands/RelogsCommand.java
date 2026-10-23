package com.boffo.addon.commands;

import com.boffo.addon.RelogTracker;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class RelogsCommand extends Command {
    public RelogsCommand() {
        super("relogs", "Zeigt den Relog-Zaehler an oder setzt ihn zurueck.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            info("Relogs: " + RelogTracker.INSTANCE.getRelogs());
            return SINGLE_SUCCESS;
        });

        builder.then(literal("reset").executes(context -> {
            RelogTracker.INSTANCE.reset();
            info("Relog-Zaehler wurde zurueckgesetzt.");
            return SINGLE_SUCCESS;
        }));
    }
}
