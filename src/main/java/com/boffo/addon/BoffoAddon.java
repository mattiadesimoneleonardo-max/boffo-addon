package com.boffo.addon;

import com.boffo.addon.commands.RelogsCommand;
import com.boffo.addon.hud.RelogCounterHud;
import com.boffo.addon.modules.RelogsModule;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class BoffoAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Boffo Addon");
    public static final HudGroup HUD_GROUP = new HudGroup("Boffo Addon");

    @Override
    public void onInitialize() {
        // Der Tracker laeuft immer im Hintergrund, auch wenn das Modul aus ist.
        MeteorClient.EVENT_BUS.subscribe(RelogTracker.INSTANCE);

        Modules.get().add(new RelogsModule());
        Hud.get().register(RelogCounterHud.INFO);
        Commands.add(new RelogsCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.boffo.addon";
    }
}
