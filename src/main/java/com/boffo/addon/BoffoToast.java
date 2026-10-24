package com.boffo.addon;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Benachrichtigung oben rechts. Die gewaehlte Farbe faerbt Rand, Hintergrund, Titel, Icon und Balken ein. */
public class BoffoToast implements Toast {
    private static final long DURATION = 7000;

    private final String title, coords;
    private final int accent, background, border, tile, titleColor, coordColor;
    private long start = -1, now = 0;
    private boolean playedSound = false;
    private Visibility visibility = Visibility.HIDE;

    public BoffoToast(String title, String coords, int r, int g, int b) {
        this.title = title;
        this.coords = coords;
        int rgb = (r << 16) | (g << 8) | b;
        this.accent = 0xFF000000 | rgb;
        this.background = 0xF5000000 | mix(0x0E0E10, rgb, 0.14);   // fast schwarz, leicht in der Farbe getoent
        this.border = 0xFF000000 | mix(0x1C1C1E, rgb, 0.65);
        this.tile = 0xFF000000 | mix(0x0E0E10, rgb, 0.32);
        this.titleColor = 0xFF000000 | mix(rgb, 0xFFFFFF, 0.35);   // helle Variante der Farbe
        this.coordColor = 0xFF000000 | mix(0xFFFFFF, rgb, 0.22);   // fast weiss, leicht getoent
    }

    /** Mischt zwei RGB-Farben (t = Anteil von b). */
    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    public int getWidth() { return 200; }

    @Override
    public int getHeight() { return 40; }

    @Override
    public Visibility getVisibility() { return visibility; }

    @Override
    public void update(ToastManager manager, long time) {
        if (start == -1) start = time;
        now = time;
        visibility = time - start >= DURATION ? Visibility.HIDE : Visibility.SHOW;

        if (!playedSound) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.2f, 1));
            playedSound = true;
        }
    }

    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
        int w = getWidth(), h = getHeight();

        // Koerper + Rand (Ecken leicht abgeschnitten)
        context.fill(1, 0, w - 1, h, background);
        context.fill(0, 1, w, h - 1, background);
        context.fill(1, 0, w - 1, 1, border);
        context.fill(1, h - 1, w - 1, h, border);
        context.fill(w - 1, 1, w, h - 1, border);
        // Farbkante links
        context.fill(0, 4, 2, h - 4, accent);

        // Icon-Kachel mit drei Balken
        context.fill(9, 7, 35, 33, tile);
        context.fill(8, 8, 36, 32, tile);
        context.fill(14, 15, 17, 25, accent);
        context.fill(20, 11, 23, 29, accent);
        context.fill(26, 15, 29, 25, accent);

        context.drawText(textRenderer, Text.literal(title), 44, 9, titleColor, false);
        context.drawText(textRenderer, Text.literal(coords), 44, 22, coordColor, false);

        // Restzeit-Balken unten
        double frac = Math.max(0, 1 - (now - start) / (double) DURATION);
        context.fill(2, h - 2, 2 + (int) ((w - 4) * frac), h - 1, accent);
    }
}

