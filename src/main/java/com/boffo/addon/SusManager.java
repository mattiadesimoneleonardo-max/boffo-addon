package com.boffo.addon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * Zaehlt Relogs unter Hoehe 0 und spielt die Sus-Animation zufaellig ab.
 *
 * - Der Zaehler beginnt bei jedem Spielstart bei 0 (nur im Arbeitsspeicher, nichts wird gespeichert).
 * - Es wird ein Zufallsziel zwischen 15 und 30 gezogen. Hat man so oft unter Y 0 relogged,
 *   laeuft die Animation. Danach: Zaehler zurueck auf 0 und neues Zufallsziel.
 * - Der erste Beitritt nach dem Spielstart ist kein Relog.
 */
public class SusManager {
    public static final SusManager INSTANCE = new SusManager();

    public static final int MIN_RELOGS = 15;
    public static final int MAX_RELOGS = 30;
    /** Nur Relogs zaehlen, bei denen der Spieler UNTER dieser Hoehe steht. */
    public static final double COUNT_BELOW_Y = 0;

    private static final int JOIN_WAIT_TICKS = 5;   // kurz warten, bis die Verbindung steht
    private static final int POSITION_WAIT_TICKS = 40; // 2 s warten, bis die Spielerposition stimmt

    private final MinecraftClient mc = MeteorClient.mc;
    private final Random random = new Random();

    private int joinsThisSession;
    private int susRelogs;
    private int target = rollTarget();
    private int lastConnection;
    private boolean joinPending;
    private int joinWait;
    private int positionWait;

    private int rollTarget() {
        return MIN_RELOGS + random.nextInt(MAX_RELOGS - MIN_RELOGS + 1); // 15..30
    }

    /** Fuer den Button im Modul. */
    public void playNow() {
        SusAnimation.play();
    }

    @EventHandler
    public void onGameJoined(GameJoinedEvent event) {
        joinPending = true;
        joinWait = JOIN_WAIT_TICKS;
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (joinPending && --joinWait <= 0) {
            joinPending = false;
            resolveJoin();
        }
        if (positionWait > 0 && --positionWait == 0) countIfUnderground();
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!SusAnimation.isActive()) return;
        var window = mc.getWindow();
        SusAnimation.render(event.drawContext, window.getScaledWidth(), window.getScaledHeight());
    }

    private void resolveJoin() {
        if (mc.isInSingleplayer()) return;

        var handler = mc.getNetworkHandler();
        int connection = handler == null ? 0 : System.identityHashCode(handler.getConnection());
        if (connection != 0 && connection == lastConnection) return; // gleiche Verbindung (z. B. Proxy-Wechsel)
        lastConnection = connection;

        joinsThisSession++;
        if (joinsThisSession <= 1) return; // erster Beitritt = kein Relog
        positionWait = POSITION_WAIT_TICKS;
    }

    private void countIfUnderground() {
        if (mc.player == null) return;
        if (mc.player.getY() >= COUNT_BELOW_Y) return;

        susRelogs++;
        if (susRelogs >= target) {
            susRelogs = 0;
            target = rollTarget();
            SusAnimation.play();
        }
    }
}
