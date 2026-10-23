package com.boffo.addon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

import java.util.Locale;

public class RelogTracker {
    public static final RelogTracker INSTANCE = new RelogTracker();

    private String currentServer = null;
    private int lastConnection = -1;
    private int relogs = 0;

    private boolean pendingJoin = false;
    private int waitTicks = 0;

    private RelogTracker() {}

    @EventHandler
    public void onGameJoined(GameJoinedEvent event) {
        pendingJoin = true;
        waitTicks = 0;
        resolve();
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (pendingJoin) resolve();
    }

    private void resolve() {
        String key = getServerKey();
        if (key == null) {
            if (++waitTicks < 200) return;
            key = "unbekannt";
        }
        int conn = -1;
        if (MeteorClient.mc.getNetworkHandler() != null && MeteorClient.mc.getNetworkHandler().getConnection() != null) {
            conn = System.identityHashCode(MeteorClient.mc.getNetworkHandler().getConnection());
        }
        handleJoin(key, conn);
        pendingJoin = false;
    }

    void handleJoin(String serverKey, int connection) {
        if (serverKey.equals(currentServer)) {
            if (connection != lastConnection) relogs++;
        } else {
            currentServer = serverKey;
            relogs = 0;
        }
        lastConnection = connection;
    }

    private String getServerKey() {
        var info = MeteorClient.mc.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) {
            return info.address.toLowerCase(Locale.ROOT).trim();
        }
        if (MeteorClient.mc.isInSingleplayer()) return "singleplayer";
        return null;
    }

    public int getRelogs() { return currentServer == null ? 0 : relogs; }
    public String getCurrentServer() { return currentServer; }
    public void resetCurrent() { relogs = 0; }
}
