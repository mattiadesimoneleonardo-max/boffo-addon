package com.boffo.addon;

import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Anubis-Anti-Vanish-Animation (Java-Nachbau der HTML-Vorlage) + Sus-Nachricht.
 *
 * Ablauf (4,8 s): Donut fliegt von oben ein -> kurzes Zittern links/rechts -> Haltephase
 * -> faellt rotierend nach unten weg. Danach erscheint die leuchtende Nachricht.
 * Gezeichnet wird nur mit DrawContext.fill(...) - Kreise, Kurven und Schrift sind
 * selbst berechnete Flaechen.
 */
public final class SusAnimation {
    private SusAnimation() {}

    public static final double ANIM_SECONDS = 4.8;
    private static final double TEXT_IN = 0.5, TEXT_HOLD = 4.0, TEXT_OUT = 1.0;
    public static final double TOTAL_SECONDS = ANIM_SECONDS + TEXT_IN + TEXT_HOLD + TEXT_OUT;

    private static final String LINE_1 = "WATCH OUT BOFFO ADDON USER,";
    private static final String LINE_2 = "YOU'RE ON /SUS NOW!";

    private static volatile long startNanos = -1;

    public static void play() {
        startNanos = System.nanoTime();
    }

    /** true, solange die Animation (inkl. Text) laeuft. */
    public static boolean isActive() {
        return startNanos >= 0;
    }

    public static void render(DrawContext ctx, int w, int h) {
        long s = startNanos;
        if (s < 0) return;
        double t = (System.nanoTime() - s) / 1_000_000_000.0;
        if (t >= TOTAL_SECONDS) {
            startNanos = -1;
            return;
        }
        renderAt(ctx, w, h, t);
    }

    /** Zeichnet den Zustand zum Zeitpunkt t (Sekunden seit Start). */
    public static void renderAt(DrawContext ctx, int w, int h, double t) {
        double dim = Math.min(1, t / 0.3);
        if (t > TOTAL_SECONDS - TEXT_OUT) dim = Math.max(0, (TOTAL_SECONDS - t) / TEXT_OUT);
        int dimAlpha = (int) (110 * dim);
        if (dimAlpha > 0) ctx.fill(0, 0, w, h, dimAlpha << 24);

        if (t < ANIM_SECONDS) drawDonut(ctx, w, h, t / ANIM_SECONDS);
        else drawMessage(ctx, w, h, t - ANIM_SECONDS);
    }

    // ------------------------------------------------------------------ Flugbahn (Keyframes der HTML)

    //                          0     12%   16%   19%    21%   23%    25%   27%    29%   31%    33%   36%  55%  100%
    private static final double[] KT   = {0, .12, .16, .19, .21, .23, .25, .27, .29, .31, .33, .36, .55, 1.0};
    private static final double[] KDX  = {0, 0, 0, -.02, .02, -.02, .02, -.02, .02, -.02, .02, 0, 0, 0};     // x-Versatz in Donut-Groessen
    private static final double[] KSC  = {.6, 1.12, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, .75};                      // Skalierung
    private static final double[] KROT = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 360};                          // Drehung in Grad
    private static final double[] KCYH = {0, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, 1.2};              // Mitte y: Anteil der Bildschirmhoehe
    private static final double[] KCYS = {-.68, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .5};                        // Mitte y: Anteil der Donut-Groesse

    private static void drawDonut(DrawContext ctx, int w, int h, double p) {
        int seg = 0;
        while (seg < KT.length - 2 && p > KT[seg + 1]) seg++;
        double u = (p - KT[seg]) / (KT[seg + 1] - KT[seg]);
        double e = ease(Math.max(0, Math.min(1, u)));

        double size = h * 0.46;
        double scale = lerp(KSC[seg], KSC[seg + 1], e);
        double rot = Math.toRadians(lerp(KROT[seg], KROT[seg + 1], e));
        double cx = w / 2.0 + lerp(KDX[seg], KDX[seg + 1], e) * size;
        double cy = lerp(KCYH[seg], KCYH[seg + 1], e) * h + lerp(KCYS[seg], KCYS[seg + 1], e) * size;
        double k = size * scale / 500.0; // SVG-Einheiten (500x500) -> Pixel

        // weicher blauer Schein (drop-shadow der HTML)
        for (int i = 0; i < 6; i++) {
            fillCircle(ctx, cx, cy + 20 * k, (234 + i * 11) * k, (13 << 24) | 0x0077FF, h);
        }

        fillCircle(ctx, cx, cy, 230 * k, 0xFF060A17, h);   // schwarze Aussenkontur
        fillCircle(ctx, cx, cy, 208 * k, 0xFFD19251, h);   // Teig
        fillPoly(ctx, POLY_OUTER, cx, cy, k, rot, 0xFF0044CC, h);  // dunkelblaue Glasur-Umrandung
        fillPoly(ctx, POLY_GLAZE, cx, cy, k, rot, 0xFF0077FF, h);  // Hauptglasur
        fillPoly(ctx, POLY_LIGHT, cx, cy, k, rot, 0xFF1EA0FF, h);  // helles Inlay
        fillCircle(ctx, cx, cy, 80 * k, 0xFF060A17, h);    // Lochrand
        fillCircle(ctx, cx, cy, 62 * k, 0xFF060606, h);    // Loch

        // Teig-Schatten im Loch (Strich, Breite 8)
        for (int i = 0; i < SHADOW_X.length; i++) {
            double[] q = transform(SHADOW_X[i], SHADOW_Y[i], cx, cy, k, rot);
            fillCircle(ctx, q[0], q[1], 4 * k, 0xFFD19251, h);
        }

        drawBoffo(ctx, cx, cy, size * scale, rot, h);
    }

    // ------------------------------------------------------------------ Formen

    private static final double[][] POLY_OUTER = bezier(
        250, 50, 330, 52, 395, 70, 430, 130, 445, 160, 425, 210, 445, 250, 465, 290, 430, 350, 395, 400,
        340, 440, 290, 425, 250, 440, 210, 425, 160, 440, 105, 400, 70, 350, 35, 290, 55, 250,
        75, 210, 55, 160, 70, 130, 105, 70, 170, 52, 250, 50);
    private static final double[][] POLY_GLAZE = bezier(
        250, 64, 320, 66, 380, 82, 410, 138, 422, 165, 405, 210, 422, 248, 438, 285, 410, 340, 380, 385,
        330, 422, 285, 410, 250, 422, 215, 410, 170, 422, 120, 385, 90, 340, 62, 285, 78, 248,
        94, 210, 78, 165, 90, 138, 120, 82, 180, 66, 250, 64);
    private static final double[][] POLY_LIGHT = bezier(
        250, 85, 305, 87, 355, 100, 380, 148, 390, 170, 375, 210, 390, 245, 405, 280, 380, 325, 355, 362,
        310, 395, 275, 385, 250, 395, 225, 385, 190, 395, 145, 362, 120, 325, 95, 280, 110, 245,
        125, 210, 110, 170, 120, 148, 145, 100, 195, 87, 250, 85);
    private static final double[][] SHADOW = bezier(235, 192, 255, 188, 280, 195, 295, 210);
    private static final double[] SHADOW_X = SHADOW[0], SHADOW_Y = SHADOW[1];

    /** Kubische Bezier-Segmente (Start + je 6 Werte) in Punkte umrechnen. */
    private static double[][] bezier(double... v) {
        int segs = (v.length - 2) / 6;
        int steps = 18;
        double[] xs = new double[segs * steps + 1];
        double[] ys = new double[segs * steps + 1];
        int n = 0;
        xs[n] = v[0];
        ys[n++] = v[1];
        double x0 = v[0], y0 = v[1];
        for (int s = 0; s < segs; s++) {
            double x1 = v[2 + s * 6], y1 = v[3 + s * 6], x2 = v[4 + s * 6], y2 = v[5 + s * 6];
            double x3 = v[6 + s * 6], y3 = v[7 + s * 6];
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps, it = 1 - t;
                xs[n] = it * it * it * x0 + 3 * it * it * t * x1 + 3 * it * t * t * x2 + t * t * t * x3;
                ys[n++] = it * it * it * y0 + 3 * it * it * t * y1 + 3 * it * t * t * y2 + t * t * t * y3;
            }
            x0 = x3;
            y0 = y3;
        }
        return new double[][]{xs, ys};
    }

    /** SVG-Punkt (Mitte 250/250) -> Bildschirm mit Skalierung k und Drehung rot. */
    private static double[] transform(double px, double py, double cx, double cy, double k, double rot) {
        double lx = (px - 250) * k, ly = (py - 250) * k;
        double c = Math.cos(rot), s = Math.sin(rot);
        return new double[]{cx + lx * c - ly * s, cy + lx * s + ly * c};
    }

    private static void fillCircle(DrawContext ctx, double cx, double cy, double r, int argb, int screenH) {
        int y0 = Math.max(0, (int) Math.floor(cy - r));
        int y1 = Math.min(screenH, (int) Math.ceil(cy + r));
        for (int y = y0; y < y1; y++) {
            double dy = (y + 0.5) - cy;
            double sq = r * r - dy * dy;
            if (sq <= 0) continue;
            double half = Math.sqrt(sq);
            ctx.fill((int) Math.round(cx - half), y, (int) Math.round(cx + half), y + 1, argb);
        }
    }

    private static void fillPoly(DrawContext ctx, double[][] poly, double cx, double cy, double k, double rot,
                                 int argb, int screenH) {
        int n = poly[0].length;
        double[] xs = new double[n], ys = new double[n];
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double[] q = transform(poly[0][i], poly[1][i], cx, cy, k, rot);
            xs[i] = q[0];
            ys[i] = q[1];
            minY = Math.min(minY, q[1]);
            maxY = Math.max(maxY, q[1]);
        }
        double[] hits = new double[64];
        int y0 = Math.max(0, (int) Math.floor(minY));
        int y1 = Math.min(screenH, (int) Math.ceil(maxY));
        for (int y = y0; y < y1; y++) {
            double sy = y + 0.5;
            int m = 0;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double ya = ys[j], yb = ys[i];
                if ((ya <= sy && yb > sy) || (yb <= sy && ya > sy)) {
                    if (m < hits.length) hits[m++] = xs[j] + (sy - ya) * (xs[i] - xs[j]) / (yb - ya);
                }
            }
            java.util.Arrays.sort(hits, 0, m);
            for (int i = 0; i + 1 < m; i += 2) {
                int xa = (int) Math.round(hits[i]), xb = (int) Math.round(hits[i + 1]);
                if (xb > xa) ctx.fill(xa, y, xb, y + 1, argb);
            }
        }
    }

    // ------------------------------------------------------------------ Easing (cubic-bezier(0.15, 0.85, 0.35, 1.05))

    private static double ease(double x) {
        double lo = 0, hi = 1, s = x;
        for (int i = 0; i < 28; i++) {
            s = (lo + hi) / 2;
            if (cubic(0.15, 0.35, s) < x) lo = s;
            else hi = s;
        }
        return cubic(0.85, 1.05, s);
    }

    private static double cubic(double a, double b, double s) {
        double inv = 1 - s;
        return 3 * inv * inv * s * a + 3 * inv * s * s * b + s * s * s;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------ Pixel-Schrift (5x7)

    private static final Map<Character, String[]> GLYPHS = new HashMap<>();

    private static void g(char c, String... rows) {
        GLYPHS.put(c, rows);
    }

    static {
        g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        g('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###.");
        g('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..");
        g('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        g('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        g('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        g('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        g('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        g('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#");
        g('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        g('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        g('\'', "..#..", "..#..", ".#...", ".....", ".....", ".....", ".....");
        g(',', ".....", ".....", ".....", ".....", ".##..", "..#..", ".#...");
        g('/', "....#", "....#", "...#.", "..#..", ".#...", "#....", "#....");
        g('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..");
        g('.', ".....", ".....", ".....", ".....", ".....", ".##..", ".##..");
        g(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....");
    }

    /** Leuchtende Pixel-Schrift am Donut-Mittelpunkt: BOFFO (dreht sich mit dem Donut). */
    private static void drawBoffo(DrawContext ctx, double cx, double cy, double donutPx, double rot, int h) {
        String text = "BOFFO";
        double unit = donutPx * 0.03;
        if (unit < 1) return;
        int cols = text.length() * 6 - 1;
        java.util.List<double[]> cells = new java.util.ArrayList<>();
        for (int ci = 0; ci < text.length(); ci++) {
            String[] rows = GLYPHS.get(text.charAt(ci));
            for (int r = 0; r < 7; r++)
                for (int c = 0; c < 5; c++)
                    if (rows[r].charAt(c) == '#') {
                        double lx = (ci * 6 + c + 0.5 - cols / 2.0) * unit;
                        double ly = (r + 0.5 - 3.5) * unit;
                        double co = Math.cos(rot), si = Math.sin(rot);
                        cells.add(new double[]{cx + lx * co - ly * si, cy + lx * si + ly * co});
                    }
        }
        double half = unit * 0.5 * (Math.abs(rot) > 1e-6 ? 1.25 : 1.0);
        double off = unit * 1.0; // harter Schatten (6px 6px der HTML)
        for (double[] p : cells) rect(ctx, p[0] + off, p[1] + off, half, 0xFF060A17);
        for (double[] p : cells) rect(ctx, p[0], p[1], half + unit * 1.8, (14 << 24) | 0x00FFFF);
        for (double[] p : cells) rect(ctx, p[0], p[1], half + unit * 0.9, (34 << 24) | 0x0077FF);
        for (double[] p : cells) rect(ctx, p[0], p[1], half, 0xFFFFFFFF);
    }

    private static void rect(DrawContext ctx, double x, double y, double half, int argb) {
        ctx.fill((int) Math.floor(x - half), (int) Math.floor(y - half),
            (int) Math.ceil(x + half), (int) Math.ceil(y + half), argb);
    }

    // ------------------------------------------------------------------ Nachricht nach der Animation

    private static void drawMessage(DrawContext ctx, int w, int h, double p) {
        double a = Math.min(1, p / TEXT_IN);
        if (p > TEXT_IN + TEXT_HOLD) a = Math.max(0, 1 - (p - TEXT_IN - TEXT_HOLD) / TEXT_OUT);
        if (a <= 0) return;
        double pulse = 0.85 + 0.15 * Math.sin(p * 7);

        int u = Math.max(2, (int) (0.88 * w / (6 * LINE_1.length())));
        int lineH = 7 * u, gap = 3 * u;
        int top = h / 2 - (2 * lineH + gap) / 2;

        drawLine(ctx, LINE_1, w, top, u, a, pulse);
        drawLine(ctx, LINE_2, w, top + lineH + gap, u, a, pulse);
    }

    private static void drawLine(DrawContext ctx, String text, int w, int y0, int u, double a, double pulse) {
        int width = (6 * text.length() - 1) * u;
        int x0 = (w - width) / 2;
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int ci = 0; ci < text.length(); ci++) {
            String[] rows = GLYPHS.get(text.charAt(ci));
            if (rows == null) continue;
            for (int r = 0; r < 7; r++)
                for (int c = 0; c < 5; c++)
                    if (rows[r].charAt(c) == '#') cells.add(new int[]{x0 + (ci * 6 + c) * u, y0 + r * u});
        }
        int shadow = (int) (255 * a) << 24 | 0x060A17;
        int outer = (int) (22 * a * pulse) << 24 | 0x00FFFF;
        int mid = (int) (46 * a * pulse) << 24 | 0x0077FF;
        int core = (int) (255 * a) << 24 | 0xFFFFFF;
        for (int[] c : cells) ctxRect(ctx, c[0] + u / 2 + 1, c[1] + u / 2 + 1, u, 0, shadow);
        for (int[] c : cells) ctxRect(ctx, c[0], c[1], u, 2 * u, outer);
        for (int[] c : cells) ctxRect(ctx, c[0], c[1], u, u, mid);
        for (int[] c : cells) ctxRect(ctx, c[0], c[1], u, 0, core);
    }

    private static void ctxRect(DrawContext ctx, int x, int y, int u, int grow, int argb) {
        ctx.fill(x - grow, y - grow, x + u + grow, y + u + grow, argb);
    }
}
