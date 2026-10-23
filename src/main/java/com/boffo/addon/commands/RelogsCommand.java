package com.boffo.addon.commands;

import com.boffo.addon.RelogTracker;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

public class RelogsCommand extends Command {
    public RelogsCommand() {
        super("relogs", "Zeigt die Relogs des aktuellen Servers an oder setzt sie zurueck.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            info("Relogs auf diesem Server: " + RelogTracker.INSTANCE.getRelogs());
            return SINGLE_SUCCESS;
        });

        builder.then(literal("reset").executes(ctx -> {
            RelogTracker.INSTANCE.resetCurrent();
            info("Relogs fuer diesen Server zurueckgesetzt.");
            return SINGLE_SUCCESS;
        }));
    }
}
