package com.boffo.addon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.orbit.EventHandler;

import java.util.Locale;

public class RelogTracker {
    public static final RelogTracker INSTANCE = new RelogTracker();

    private String currentServer = null;  // Server, für den der Zähler gerade gilt
    private int relogs = 0;
    private boolean disconnected = false; // gab es seit dem letzten Join einen echten Disconnect?

    private RelogTracker() {}

    @EventHandler
    public void onGameJoined(GameJoinedEvent event) {
        handleJoin(getServerKey());
    }

    @EventHandler
    public void onGameLeft(GameLeftEvent event) {
        handleLeave();
    }

    void handleJoin(String serverKey) {
        if (serverKey.equals(currentServer)) {
            if (disconnected) relogs++;   // echter Relog auf demselben Server
        } else {
            currentServer = serverKey;    // anderer Server -> bei 0 anfangen
            relogs = 0;
        }
        disconnected = false;
    }

    void handleLeave() {
        disconnected = true;
    }

    private String getServerKey() {
        var info = MeteorClient.mc.getCurrentServerEntry();
        if (info != null && info.address != null) return info.address.toLowerCase(Locale.ROOT).trim();
        if (MeteorClient.mc.isInSingleplayer()) return "singleplayer";
        return "unbekannt";
    }

    public int getRelogs() {
        return currentServer == null ? 0 : relogs;
    }

    public String getCurrentServer() {
        return currentServer;
    }

    public void resetCurrent() {
        relogs = 0;
    }
}
