package com.boffo.addon;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Dunkle Benachrichtigung oben rechts: Icon, Titel und die Koordinaten des Fundes. */
public class BoffoToast implements Toast {
    private static final long DURATION = 7000;

    private final String title, coords;
    private final int accent;   // Hauptfarbe (ARGB)
    private final int tile;     // dunkle Variante fuer die Icon-Kachel (ARGB)
    private long start = -1, now = 0;
    private boolean playedSound = false;
    private Visibility visibility = Visibility.HIDE;

    public BoffoToast(String title, String coords, int r, int g, int b) {
        this.title = title;
        this.coords = coords;
        this.accent = 0xFF000000 | (r << 16) | (g << 8) | b;
        this.tile = 0xFF000000 | ((r * 28 / 100) << 16) | ((g * 28 / 100) << 8) | (b * 28 / 100);
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
        context.fill(1, 0, w - 1, h, 0xF2141210);
        context.fill(0, 1, w, h - 1, 0xF2141210);
        context.fill(1, 0, w - 1, 1, 0xFF2E2823);
        context.fill(1, h - 1, w - 1, h, 0xFF2E2823);
        context.fill(w - 1, 1, w, h - 1, 0xFF2E2823);
        // Farbkante links
        context.fill(0, 4, 2, h - 4, accent);

        // Icon-Kachel mit drei Balken
        context.fill(9, 7, 35, 33, tile);
        context.fill(8, 8, 36, 32, tile);
        context.fill(14, 15, 17, 25, accent);
        context.fill(20, 11, 23, 29, accent);
        context.fill(26, 15, 29, 25, accent);

        context.drawText(textRenderer, Text.literal(title), 44, 9, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.literal(coords), 44, 22, 0xFFD6E8E0, false);

        // Restzeit-Balken unten
        double frac = Math.max(0, 1 - (now - start) / (double) DURATION);
        context.fill(2, h - 2, 2 + (int) ((w - 4) * frac), h - 1, accent);
    }
}
