package com.boffo.addon;

import com.boffo.addon.commands.RelogsCommand;
import com.boffo.addon.hud.RelogCounterHud;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;

public class BoffoAddon extends MeteorAddon {
    public static final HudGroup HUD_GROUP = new HudGroup("Boffo Addon");

    @Override
    public void onInitialize() {
        // Der Tracker laeuft immer, auch wenn die Anzeige ausgeschaltet ist.
        MeteorClient.EVENT_BUS.subscribe(RelogTracker.INSTANCE);

        Hud.get().register(RelogCounterHud.INFO);
        Commands.add(new RelogsCommand());
    }

    @Override
    public String getPackage() {
        return "com.boffo.addon";
    }
}
