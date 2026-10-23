package com.boffo.addon;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.orbit.EventHandler;

/**
 * Zaehlt Relogs. Der Zaehler liegt nur im Arbeitsspeicher und wird deshalb
 * automatisch zurueckgesetzt, wenn die Instanz geschlossen und neu gestartet wird.
 * Sonst nur per Befehl: .relogs reset
 */
public class RelogTracker {
    public static final RelogTracker INSTANCE = new RelogTracker();

    private int relogs = 0;
    private boolean joinedBefore = false;

    private RelogTracker() {}

    @EventHandler
    public void onGameJoined(GameJoinedEvent event) {
        // Der allererste Join der Sitzung ist kein Relog.
        if (joinedBefore) relogs++;
        joinedBefore = true;
    }

    public int getRelogs() {
        return relogs;
    }

    public void reset() {
        relogs = 0;
    }
}
