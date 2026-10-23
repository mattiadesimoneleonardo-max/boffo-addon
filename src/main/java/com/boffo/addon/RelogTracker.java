package com.boffo.addon;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Zaehlt Relogs pro Server. Die Zaehler liegen nur im Arbeitsspeicher und
 * sind deshalb nach einem Neustart der Instanz wieder bei 0.
 * Ein Relog ist jeder weitere Join auf einen Server, auf dem man in dieser
 * Sitzung schon war. Der erste Join auf einen Server zaehlt nicht.
 */
public class RelogTracker {
    public static final RelogTracker INSTANCE = new RelogTracker();

    private final Map<String, Integer> relogs = new HashMap<>();
    private String currentServer = null;

    private RelogTracker() {}

    @EventHandler
    public void onGameJoined(GameJoinedEvent event) {
        String key = getServerKey();
        currentServer = key;

        if (relogs.containsKey(key)) relogs.merge(key, 1, Integer::sum);
        else relogs.put(key, 0);
    }

    private String getServerKey() {
        ServerInfo info = mc.getCurrentServerEntry();
        if (info != null && info.address != null) return info.address.toLowerCase(Locale.ROOT);
        if (mc.isInSingleplayer()) return "singleplayer";
        return "unbekannt";
    }

    /** Relogs auf dem Server, auf dem man gerade ist. */
    public int getRelogs() {
        if (currentServer == null) return 0;
        return relogs.getOrDefault(currentServer, 0);
    }

    public String getCurrentServer() {
        return currentServer;
    }

    public void resetCurrent() {
        if (currentServer != null) relogs.put(currentServer, 0);
    }

    public void resetAll() {
        relogs.replaceAll((key, value) -> 0);
    }
}
