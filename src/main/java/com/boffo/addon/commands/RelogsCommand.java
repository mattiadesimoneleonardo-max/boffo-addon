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
            String server = RelogTracker.INSTANCE.getCurrentServer();
            info("Server: " + (server == null ? "-" : server) + " | Relogs: " + RelogTracker.INSTANCE.getRelogs());
            return SINGLE_SUCCESS;
        });

        builder.then(literal("reset").executes(ctx -> {
            RelogTracker.INSTANCE.resetCurrent();
            info("Relogs fuer diesen Server zurueckgesetzt.");
            return SINGLE_SUCCESS;
        }).then(literal("all").executes(ctx -> {
            RelogTracker.INSTANCE.resetAll();
            info("Relogs fuer alle Server zurueckgesetzt.");
            return SINGLE_SUCCESS;
        })));
    }
}
