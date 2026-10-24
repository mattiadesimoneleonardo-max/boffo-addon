package com.boffo.addon.modules;

import com.boffo.addon.SusManager;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Eigene Kategorie "/sus detector" mit einem Modul ohne Einstellungen - nur ein Button,
 * der die Animation sofort abspielt. Die zufaellige Ausloesung nach 15-30 Relogs laeuft
 * unabhaengig davon (SusManager).
 */
public class SusDetectorModule extends Module {
    public static final Category CATEGORY = new Category("/sus detector");

    public SusDetectorModule() {
        super(CATEGORY, "/sus detector", "Spielt die Sus-Animation ab.");
        MeteorClient.EVENT_BUS.subscribe(SusManager.INSTANCE);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton button = theme.button("Animation abspielen");
        button.action = SusManager.INSTANCE::playNow;
        return button;
    }
}
