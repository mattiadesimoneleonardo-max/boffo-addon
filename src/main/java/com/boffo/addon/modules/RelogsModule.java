package com.boffo.addon.modules;

import com.boffo.addon.BoffoAddon;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Schalter fuer die Relog-Anzeige. Ist das Modul aus, wird die Anzeige im HUD
 * ausgeblendet. Gezaehlt wird im Hintergrund immer weiter.
 */
public class RelogsModule extends Module {
    public RelogsModule() {
        super(BoffoAddon.CATEGORY, "relogs", "Zaehlt pro Server, wie oft du relogged hast, und zeigt es im HUD an.");
    }
}
