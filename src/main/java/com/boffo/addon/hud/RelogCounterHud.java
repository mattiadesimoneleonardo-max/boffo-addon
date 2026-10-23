package com.boffo.addon.hud;

import com.boffo.addon.BoffoAddon;
import com.boffo.addon.RelogTracker;
import com.boffo.addon.modules.RelogsModule;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Zeichnet den Relog-Zaehler. Design, Farben usw. stellst du im Modul "relogs" ein.
 */
public class RelogCounterHud extends HudElement {
    public static final HudElementInfo<RelogCounterHud> INFO = new HudElementInfo<>(
        BoffoAddon.HUD_GROUP, "relog-counter",
        "Zeigt an, wie oft du auf diesem Server relogged hast. Design im Modul \"relogs\" einstellen.",
        RelogCounterHud::new);

    public enum Design {
        Aurora("Aurora"),
        Glass("Glass"),
        Neon("Neon"),
        Pill("Pill"),
        Minimal("Minimal"),
        Split("Split"),
        Gradient("Gradient"),
        Terminal("Terminal"),
        Meter("Meter"),
        Badge("Badge"),
        Outline("Outline"),
        Compact("Compact");

        private final String title;
        Design(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    // --- pro Frame gesetzt ---
    private HudRenderer r;
    private double s;
    private boolean sh;
    private double rad;
    private Color txt, acc, acc2, bg, muted;
    private String lbl, srv, num;
    private int count;
    private double pulse;

    public RelogCounterHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        RelogsModule m = Modules.get().get(RelogsModule.class);
        boolean active = m != null && m.isActive();
        if (m == null || (!active && !isInEditor())) return;

        r = renderer;
        s = m.scale.get();
        sh = m.shadow.get();
        rad = m.radius.get() * s;
        txt = m.textColor.get();
        acc = m.accent();
        acc2 = m.accent2();
        bg = m.background.get();
        muted = a(txt, 150);
        lbl = m.label.get().toUpperCase();
        count = RelogTracker.INSTANCE.getRelogs();
        num = String.valueOf(count);
        pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 380.0);

        String server = RelogTracker.INSTANCE.getCurrentServer();
        if (server == null) server = isInEditor() ? "mc.beispiel.net" : "-";
        srv = m.showServer.get() ? shorten(server) : "";

        switch (m.design.get()) {
            case Aurora -> aurora();
            case Glass -> glass();
            case Neon -> neon();
            case Pill -> pill();
            case Minimal -> minimal();
            case Split -> split();
            case Gradient -> gradient();
            case Terminal -> terminal();
            case Meter -> meter(m.meterMax.get());
            case Badge -> badge();
            case Outline -> outline();
            case Compact -> compact();
        }
    }

    // =============================================================== Designs

    /** Hauptdesign: dunkle Karte, Farbverlauf-Rahmen, Live-Punkt, grosse Zahl, leuchtende Linie. */
    private void aurora() {
        double pad = 7 * s, g = 3 * s;
        double small = s * 0.72, big = s * 2.3;
        double dot = th(small) * 0.55;
        double top = (dot + g + tw(lbl, small)) + (srv.isEmpty() ? 0 : 10 * s + tw(srv, small));
        double w = Math.ceil(Math.max(2 * pad + Math.max(top, tw(num, big)), 78 * s));
        double ul = Math.max(2, 2 * s);
        double h = Math.ceil(pad + th(small) + g + th(big) + g * 1.5 + ul + pad * 0.9);
        setSize(w, h);

        shadow(x, y, w, h, rad);
        fill(x, y, w, h, rad, lighten(bg, 14), bg);
        ring(x, y, w, h, rad, Math.max(1, s * 0.8), a(acc, 210), a(acc2, 120));
        quad(x + rad, y + Math.max(1, s), w - 2 * rad, 1, a(255, 255, 255, 28)); // Glanzlinie

        // Live-Punkt mit pulsierendem Halo
        double dx = x + pad, dy = y + pad + (th(small) - dot) / 2;
        fill(dx - s * (1 + pulse), dy - s * (1 + pulse), dot + 2 * s * (1 + pulse), dot + 2 * s * (1 + pulse),
            dot, a(80, 220, 120, (int) (40 * (1 - pulse))));
        fill(dx, dy, dot, dot, dot / 2, new Color(80, 220, 120, 255));
        text(lbl, dx + dot + g, y + pad, muted, small);
        if (!srv.isEmpty()) text(srv, x + w - pad - tw(srv, small), y + pad, a(txt, 120), small);

        // grosse Zahl mit weichem Leuchten
        double ny = y + pad + th(small) + g;
        glowText(num, x + pad, ny, txt, big, acc);

        // leuchtende Verlaufslinie
        double ly = y + h - pad * 0.9 - ul;
        r.quad(x + pad, ly - 3 * s, w - 2 * pad, 3 * s, a(acc, 0), a(acc2, 0), a(acc2, 55), a(acc, 55));
        r.quad(x + pad, ly, w - 2 * pad, ul, acc, acc2, acc2, acc);
    }

    /** Milchglas-Karte: grosse Zahl links, Trennlinie, Text rechts. */
    private void glass() {
        double pad = 7 * s, g = 4 * s;
        double small = s * 0.72, big = s * 2.4;
        double colW = Math.max(tw(lbl, small), srv.isEmpty() ? 0 : tw(srv, small));
        double h = Math.ceil(Math.max(th(big), th(small) * 2 + 2 * s) + 2 * pad);
        double w = Math.ceil(pad + tw(num, big) + g + 1 + g + colW + pad);
        setSize(w, h);

        shadow(x, y, w, h, rad);
        fill(x, y, w, h, rad, a(bg, Math.min(255, (int) (bg.a * 0.75))));
        fill(x, y, w, h / 2, rad, a(255, 255, 255, 26), a(255, 255, 255, 4)); // Milchglas-Glanz
        ring(x, y, w, h, rad, Math.max(1, s * 0.75), a(255, 255, 255, 90), a(255, 255, 255, 25));

        text(num, x + pad, y + (h - th(big)) / 2, txt, big);
        double dx = x + pad + tw(num, big) + g;
        r.quad(dx, y + pad * 0.8, Math.max(1, s * 0.6), h - pad * 1.6, a(acc, 230), a(acc, 230), a(acc2, 230), a(acc2, 230));
        double tx = dx + g + 1;
        if (srv.isEmpty()) {
            text(lbl, tx, y + (h - th(small)) / 2, acc, small);
        } else {
            double bh = th(small) * 2 + 2 * s;
            text(lbl, tx, y + (h - bh) / 2, acc, small);
            text(srv, tx, y + (h - bh) / 2 + th(small) + 2 * s, muted, small);
        }
    }

    /** Dunkle Karte mit leuchtendem Neon-Rahmen. */
    private void neon() {
        double pad = 7 * s, g = 3 * s;
        double small = s * 0.72, big = s * 2.1;
        double top = tw(lbl, small) + (srv.isEmpty() ? 0 : 10 * s + tw(srv, small));
        double w = Math.ceil(Math.max(2 * pad + Math.max(top, tw(num, big)), 70 * s));
        double h = Math.ceil(2 * pad + th(small) + g + th(big));
        setSize(w, h);

        double b = Math.max(1, s * 0.9);
        for (int i = 4; i >= 1; i--) {
            ring(x - i * b, y - i * b, w + 2 * i * b, h + 2 * i * b, rad + i * b, b, a(acc, 70 / i), a(acc2, 70 / i));
        }
        fill(x, y, w, h, rad, a(8, 8, 14, 235));
        ring(x, y, w, h, rad, b, acc, acc2);

        text(lbl, x + pad, y + pad, a(acc, 230), small);
        if (!srv.isEmpty()) text(srv, x + w - pad - tw(srv, small), y + pad, a(txt, 110), small);
        glowText(num, x + pad, y + pad + th(small) + g, txt, big, acc);
    }

    /** Kapsel: runder Zahlen-Kreis mit Verlauf + Text. */
    private void pill() {
        double pad = 4 * s, g = 5 * s;
        double small = s * 0.72, mid = s * 1.15;
        double ch = th(mid) + 2 * pad;                       // Kreis-Durchmesser
        double cw = Math.max(ch, tw(num, mid) + 2 * pad);
        double inner = 3 * s;
        double textW = Math.max(tw(lbl, small), srv.isEmpty() ? 0 : tw(srv, small));
        double h = Math.ceil(ch + 2 * inner);
        double w = Math.ceil(inner + cw + g + textW + pad * 1.5 + inner);
        setSize(w, h);

        shadow(x, y, w, h, h / 2);
        fill(x, y, w, h, h / 2, lighten(bg, 10), bg);
        ring(x, y, w, h, h / 2, Math.max(1, s * 0.75), a(acc, 150), a(acc2, 80));
        fill(x + inner, y + inner, cw, ch, ch / 2, acc, acc2, acc2, acc);
        fill(x + inner, y + inner, cw, ch / 2, ch / 2, a(255, 255, 255, 50), a(255, 255, 255, 0)); // Glanz
        text(num, x + inner + (cw - tw(num, mid)) / 2, y + inner + (ch - th(mid)) / 2, contrast(acc), mid);

        double tx = x + inner + cw + g;
        if (srv.isEmpty()) {
            text(lbl, tx, y + (h - th(small)) / 2, txt, small);
        } else {
            double bh = th(small) * 2 + 2 * s;
            text(lbl, tx, y + (h - bh) / 2, txt, small);
            text(srv, tx, y + (h - bh) / 2 + th(small) + 2 * s, muted, small);
        }
    }

    /** Ohne Hintergrund: Beschriftung, riesige Zahl, Verlaufslinie. */
    private void minimal() {
        double g = 2 * s, small = s * 0.75, big = s * 2.8, ul = Math.max(2, 1.6 * s);
        double w = Math.ceil(Math.max(Math.max(tw(num, big), tw(lbl, small)), srv.isEmpty() ? 0 : tw(srv, small)));
        double h = Math.ceil(th(small) + g + th(big) + g + ul + (srv.isEmpty() ? 0 : g + th(small)));
        setSize(w, h);
        text(lbl, x, y, acc, small);
        glowText(num, x, y + th(small) + g, txt, big, acc);
        double ly = y + th(small) + g + th(big) + g;
        r.quad(x, ly, w, ul, acc, acc2, acc2, acc);
        if (!srv.isEmpty()) text(srv, x, ly + ul + g, muted, small);
    }

    /** Kopfzeile mit Verlauf, darunter die Zahl mittig. */
    private void split() {
        double pad = 7 * s, g = 3 * s;
        double small = s * 0.72, big = s * 2.3;
        double hh = Math.ceil(th(small) + 2 * s * 2.2);
        double w = Math.ceil(Math.max(Math.max(tw(lbl, small), tw(num, big)), srv.isEmpty() ? 0 : tw(srv, small)) + 2 * pad);
        w = Math.max(w, 64 * s);
        double h = Math.ceil(hh + g + th(big) + (srv.isEmpty() ? 0 : g + th(small)) + pad);
        setSize(w, h);

        shadow(x, y, w, h, rad);
        fill(x, y, w, h, rad, lighten(bg, 10), bg);
        fill(x, y, w, hh, rad, acc, acc2, acc2, acc);
        quad(x, y + hh - rad, w, rad, acc, acc2, acc2, acc);
        ring(x, y, w, h, rad, Math.max(1, s * 0.6), a(255, 255, 255, 40), a(255, 255, 255, 14));
        text(lbl, x + (w - tw(lbl, small)) / 2, y + (hh - th(small)) / 2, contrast(acc), small);
        text(num, x + (w - tw(num, big)) / 2, y + hh + g, txt, big);
        if (!srv.isEmpty()) text(srv, x + (w - tw(srv, small)) / 2, y + hh + g + th(big) + g * 0.8, muted, small);
    }

    /** Komplette Karte als Farbverlauf mit Glanz, weisser Text. */
    private void gradient() {
        double pad = 7 * s, g = 3 * s;
        double small = s * 0.72, big = s * 2.3;
        double top = tw(lbl, small) + (srv.isEmpty() ? 0 : 10 * s + tw(srv, small));
        double w = Math.ceil(Math.max(2 * pad + Math.max(top, tw(num, big)), 74 * s));
        double h = Math.ceil(2 * pad + th(small) + g + th(big));
        setSize(w, h);

        shadow(x, y, w, h, rad);
        fill(x, y, w, h, rad, acc, acc2, darken(acc2, 70), darken(acc, 70));
        fill(x, y, w, h * 0.5, rad, a(255, 255, 255, 40), a(255, 255, 255, 0));
        ring(x, y, w, h, rad, Math.max(1, s * 0.6), a(255, 255, 255, 70), a(255, 255, 255, 20));
        Color white = new Color(255, 255, 255, 255);
        text(lbl, x + pad, y + pad, a(255, 255, 255, 210), small);
        if (!srv.isEmpty()) text(srv, x + w - pad - tw(srv, small), y + pad, a(255, 255, 255, 170), small);
        text(num, x + pad, y + pad + th(small) + g, white, big);
    }

    /** Mini-Terminalfenster mit blinkendem Cursor. */
    private void terminal() {
        double pad = 6 * s, g = 3 * s;
        double small = s * 0.8;
        String l1 = "> server: " + (srv.isEmpty() ? "hidden" : srv);
        String l2 = "> " + lbl.toLowerCase() + " = " + num;
        double head = Math.ceil(th(small) + 2 * s * 1.6);
        double w = Math.ceil(Math.max(Math.max(tw(l1, small), tw(l2 + "_", small)), 60 * s) + 2 * pad);
        double h = Math.ceil(head + pad * 0.7 + th(small) * 2 + g + pad * 0.7);
        setSize(w, h);

        shadow(x, y, w, h, rad);
        fill(x, y, w, h, rad, new Color(10, 12, 16, Math.max(200, bg.a)));
        fill(x, y, w, head, rad, a(255, 255, 255, 22));
        quad(x, y + head - rad, w, rad, a(255, 255, 255, 22));
        ring(x, y, w, h, rad, Math.max(1, s * 0.7), a(255, 255, 255, 60), a(255, 255, 255, 25));
        double d = head * 0.38;
        Color[] dots = {new Color(255, 95, 86, 255), new Color(255, 189, 46, 255), new Color(39, 201, 63, 255)};
        for (int i = 0; i < 3; i++) fill(x + pad + i * (d + 2.5 * s), y + (head - d) / 2, d, d, d / 2, dots[i]);

        double by = y + head + pad * 0.7;
        text(l1, x + pad, by, muted, small);
        text("> ", x + pad, by + th(small) + g, acc, small);
        text(lbl.toLowerCase() + " = ", x + pad + tw("> ", small), by + th(small) + g, txt, small);
        double vx = x + pad + tw("> " + lbl.toLowerCase() + " = ", small);
        text(num, vx, by + th(small) + g, acc2, small);
        if ((System.currentTimeMillis() / 500) % 2 == 0) text("_", vx + tw(num, small), by + th(small) + g, acc, small);
    }

    /** Fortschrittsbalken Richtung "meter-max". */
    private void meter(int max) {
        double pad = 7 * s, g = 3 * s;
        double small = s * 0.72, mid = s * 1.5;
        double bh = Math.max(4, 5 * s);
        double barW = Math.max(84 * s, tw(lbl, small) + tw(num, mid) + 12 * s);
        double w = Math.ceil(barW + 2 * pad);
        double h = Math.ceil(pad + Math.max(th(small), th(mid)) + g + bh + (srv.isEmpty() ? 0 : g + th(small)) + pad);
        setSize(w, h);

        shadow(x, y, w, h, rad);
        fill(x, y, w, h, rad, lighten(bg, 12), bg);
        ring(x, y, w, h, rad, Math.max(1, s * 0.7), a(255, 255, 255, 55), a(255, 255, 255, 18));
        double rowH = Math.max(th(small), th(mid));
        text(lbl, x + pad, y + pad + rowH - th(small), muted, small);
        text(num, x + w - pad - tw(num, mid), y + pad, txt, mid);

        double by = y + pad + rowH + g;
        fill(x + pad, by, barW, bh, bh / 2, a(255, 255, 255, 30));
        double frac = Math.min(1.0, count / (double) Math.max(1, max));
        double fw = count > 0 ? Math.max(bh, barW * frac) : 0;
        if (fw > 0) fill(x + pad, by, fw, bh, bh / 2, acc, acc2, acc2, acc);
        if (!srv.isEmpty()) text(srv, x + pad, by + bh + g, a(txt, 120), small);
    }

    /** Zweiteiliges Label wie bei Status-Badges. */
    private void badge() {
        double pad = 5 * s;
        double small = s * 0.85;
        double h = Math.ceil(th(small) + 2 * pad * 0.8);
        double lw = Math.ceil(tw(lbl, small) + 2 * pad);
        double vw = Math.ceil(Math.max(tw(num, small) + 2 * pad, h));
        double w = lw + vw;
        setSize(w, h);

        shadow(x, y, w, h, rad);
        fill(x, y, w, h, rad, bg);
        fill(x, y, lw, h, rad, acc, acc2, acc2, acc);
        quad(x + lw - rad, y, rad, h, lerp(acc, acc2, 0.9), acc2, acc2, lerp(acc, acc2, 0.9));
        fill(x, y, w, h / 2, rad, a(255, 255, 255, 30), a(255, 255, 255, 0));
        text(lbl, x + pad, y + (h - th(small)) / 2, contrast(lerp(acc, acc2, 0.5)), small);
        text(num, x + lw + (vw - tw(num, small)) / 2, y + (h - th(small)) / 2, txt, small);
    }

    /** Nur ein Verlaufsrahmen, innen fast durchsichtig. */
    private void outline() {
        double pad = 7 * s, g = 3 * s;
        double small = s * 0.72, big = s * 2.2;
        double top = tw(lbl, small) + (srv.isEmpty() ? 0 : 10 * s + tw(srv, small));
        double w = Math.ceil(Math.max(2 * pad + Math.max(top, tw(num, big)), 70 * s));
        double h = Math.ceil(2 * pad + th(small) + g + th(big));
        setSize(w, h);

        fill(x, y, w, h, rad, a(bg, 70));
        ring(x, y, w, h, rad, Math.max(1.5, s * 1.4), acc, acc2);
        text(lbl, x + pad, y + pad, acc, small);
        if (!srv.isEmpty()) text(srv, x + w - pad - tw(srv, small), y + pad, a(txt, 120), small);
        text(num, x + pad, y + pad + th(small) + g, txt, big);
    }

    /** Eine schmale Zeile: Balken | LABEL  Zahl  Server. */
    private void compact() {
        double pad = 5 * s, g = 4 * s, stripe = Math.max(2, 2.2 * s);
        double small = s * 0.75, mid = s * 1.1;
        double h = Math.ceil(Math.max(th(mid), th(small)) + 2 * pad * 0.75);
        double w = Math.ceil(stripe + pad + tw(lbl, small) + g + tw(num, mid)
            + (srv.isEmpty() ? 0 : g + 1 + g + tw(srv, small)) + pad);
        setSize(w, h);

        shadow(x, y, w, h, rad);
        fill(x, y, w, h, rad, bg);
        fill(x, y, stripe + rad, h, rad, acc, acc, acc2, acc2);
        quad(x + stripe, y, rad + 1, h, bg);
        double cx = x + stripe + pad;
        text(lbl, cx, y + (h - th(small)) / 2, muted, small);
        cx += tw(lbl, small) + g;
        text(num, cx, y + (h - th(mid)) / 2, txt, mid);
        cx += tw(num, mid) + g;
        if (!srv.isEmpty()) {
            quad(cx, y + pad * 0.7, 1, h - pad * 1.4, a(255, 255, 255, 50));
            text(srv, cx + g + 1, y + (h - th(small)) / 2, a(txt, 120), small);
        }
    }

    // =============================================================== Zeichen-Helfer

    private double tw(String t, double sc) { return r.textWidth(t, sh, sc); }
    private double th(double sc) { return r.textHeight(sh, sc); }
    private void text(String t, double px, double py, Color c, double sc) { r.text(t, px, py, c, sh, sc); }
    private void quad(double px, double py, double w, double h, Color c) { if (w > 0 && h > 0) r.quad(px, py, w, h, c); }
    private void quad(double px, double py, double w, double h, Color tl, Color tr, Color br, Color bl) {
        if (w > 0 && h > 0) r.quad(px, py, w, h, tl, tr, br, bl);
    }

    /** Text mit weichem farbigem Leuchten dahinter. */
    private void glowText(String t, double px, double py, Color c, double sc, Color glow) {
        double o = Math.max(1, sc * 0.45);
        Color gc = a(glow, 38);
        boolean old = sh;
        sh = false;
        text(t, px - o, py, gc, sc);
        text(t, px + o, py, gc, sc);
        text(t, px, py - o, gc, sc);
        text(t, px, py + o, gc, sc);
        sh = old;
        text(t, px, py, c, sc);
    }

    /** Weicher Schlagschatten aus mehreren Lagen. */
    private void shadow(double px, double py, double w, double h, double rd) {
        for (int i = 5; i >= 1; i--) {
            double sp = i * s * 1.1;
            fill(px - sp, py - sp + 2 * s, w + 2 * sp, h + 2 * sp, rd + sp, a(0, 0, 0, 13));
        }
    }

    private void fill(double px, double py, double w, double h, double rd, Color c) {
        fill(px, py, w, h, rd, c, c, c, c);
    }

    private void fill(double px, double py, double w, double h, double rd, Color top, Color bottom) {
        fill(px, py, w, h, rd, top, top, bottom, bottom);
    }

    /** Abgerundetes Rechteck mit Farben fuer jede Ecke (oben-links, oben-rechts, unten-rechts, unten-links). */
    private void fill(double px, double py, double w, double h, double rd, Color tl, Color tr, Color br, Color bl) {
        if (w <= 0 || h <= 0) return;
        rd = Math.min(rd, Math.min(w, h) / 2);
        int steps = rd < 1 ? 0 : (int) Math.ceil(rd);
        if (steps == 0) { r.quad(px, py, w, h, tl, tr, br, bl); return; }
        for (int i = 0; i < steps; i++) {
            double ins = inset(rd, i);
            fillRow(px, py, w, h, ins, i, tl, tr, br, bl);
            fillRow(px, py, w, h, ins, h - 1 - i, tl, tr, br, bl);
        }
        double y0 = steps, y1 = h - steps;
        if (y1 > y0) {
            double t0 = y0 / h, t1 = y1 / h;
            r.quad(px, py + y0, w, y1 - y0,
                lerp(tl, bl, t0), lerp(tr, br, t0), lerp(tr, br, t1), lerp(tl, bl, t1));
        }
    }

    private void fillRow(double px, double py, double w, double h, double ins, double j, Color tl, Color tr, Color br, Color bl) {
        double t = (j + 0.5) / h;
        Color l = lerp(tl, bl, t), rr = lerp(tr, br, t);
        double x0 = ins, x1 = w - ins;
        Color cl = lerp(l, rr, x0 / w), cr = lerp(l, rr, x1 / w);
        r.quad(px + x0, py + j, x1 - x0, 1, cl, cr, cr, cl);
    }

    /** Rahmen (nur der Rand) mit abgerundeten Ecken und Farbverlauf von links nach rechts. */
    private void ring(double px, double py, double w, double h, double rd, double thick, Color left, Color right) {
        rd = Math.min(rd, Math.min(w, h) / 2);
        int rows = (int) Math.round(h);
        int bt = Math.max(1, (int) Math.round(thick));
        double ird = Math.max(0, rd - bt);
        for (int j = 0; j < rows; j++) {
            int d = Math.min(j, rows - 1 - j);
            double o = inset(rd, d);
            if (d < bt) {
                strip(px, py + j, o, w - o, w, left, right);
            } else {
                double ii = Math.max(o, bt + inset(ird, d - bt));
                strip(px, py + j, o, ii, w, left, right);
                strip(px, py + j, w - ii, w - o, w, left, right);
            }
        }
    }

    private void strip(double px, double py, double xa, double xb, double w, Color left, Color right) {
        if (xb <= xa) return;
        Color ca = lerp(left, right, xa / w), cb = lerp(left, right, xb / w);
        r.quad(px + xa, py, xb - xa, 1, ca, cb, cb, ca);
    }

    /** Wie weit eine Pixelreihe wegen der Rundung eingerueckt ist (d = Abstand zur oberen/unteren Kante). */
    private static double inset(double rd, double d) {
        if (d >= rd) return 0;
        double dy = rd - d - 0.5;
        return rd - Math.sqrt(Math.max(0, rd * rd - dy * dy));
    }

    // --- Farben ---
    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
    private static Color a(int r, int g, int b, int al) { return new Color(r, g, b, clamp(al)); }
    private static Color a(Color c, int al) { return new Color(c.r, c.g, c.b, clamp(al)); }
    private static Color lighten(Color c, int amt) { return new Color(clamp(c.r + amt), clamp(c.g + amt), clamp(c.b + amt), c.a); }
    private static Color darken(Color c, int amt) { return lighten(c, -amt); }

    private static Color lerp(Color x, Color y, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(clamp(x.r + (y.r - x.r) * t), clamp(x.g + (y.g - x.g) * t),
            clamp(x.b + (y.b - x.b) * t), clamp(x.a + (y.a - x.a) * t));
    }

    private static Color contrast(Color c) {
        double lum = 0.299 * c.r + 0.587 * c.g + 0.114 * c.b;
        return lum > 150 ? new Color(20, 20, 24, 255) : new Color(255, 255, 255, 255);
    }

    private static String shorten(String server) {
        String t = server.replaceAll(":25565$", "");
        return t.length() > 22 ? t.substring(0, 20) + ".." : t;
    }
}
