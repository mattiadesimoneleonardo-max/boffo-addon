package com.boffo.addon.hud;

import com.boffo.addon.BoffoAddon;
import com.boffo.addon.RelogTracker;
import com.boffo.addon.modules.RelogsModule;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class RelogCounterHud extends HudElement {
    public static final HudElementInfo<RelogCounterHud> INFO = new HudElementInfo<>(
        BoffoAddon.HUD_GROUP, "relog-counter",
        "Zeigt an, wie oft du auf diesem Server relogged hast.", RelogCounterHud::new);

    private static final String TITLE = "Boffo Addon";

    public enum Design {
        Normal("Normal"), Card("Card"), Bar("Bar"), Badge("Badge"), Outline("Outline"),
        Minimal("Minimal"), Pill("Pill"), Glass("Glass"), Neon("Neon"), Gradient("Gradient"),
        Terminal("Terminal"), Split("Split"), Counter("Counter"), Underline("Underline"),
        Brackets("Brackets"), Dots("Dots"), Meter("Meter"), ShadowCard("Shadow Card");

        private final String title;
        Design(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("Style");

    private final Setting<Design> design = sgGeneral.add(new EnumSetting.Builder<Design>()
        .name("design").description("Aussehen der Anzeige.").defaultValue(Design.Card).build());
    private final Setting<String> label = sgGeneral.add(new StringSetting.Builder()
        .name("label").description("Beschriftung vor der Zahl.").defaultValue("Relogs").build());
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale").description("Groesse der Anzeige.").defaultValue(1).min(0.3).sliderRange(0.3, 5).build());
    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title").description("Zeigt die Ueberschrift \"Boffo Addon\".")
        .defaultValue(true).build());
    private final Setting<Integer> meterMax = sgGeneral.add(new IntSetting.Builder()
        .name("meter-max").description("Wert, bei dem der Balken im Design \"Meter\" voll ist.")
        .defaultValue(10).min(1).sliderRange(1, 50).build());

    private final Setting<SettingColor> textColor = sgStyle.add(new ColorSetting.Builder()
        .name("text-color").description("Textfarbe.").defaultValue(new SettingColor(255, 255, 255)).build());
    private final Setting<SettingColor> accent = sgStyle.add(new ColorSetting.Builder()
        .name("accent-color").description("Akzentfarbe.")
        .defaultValue(new SettingColor(145, 61, 226)).build());
    private final Setting<SettingColor> accent2 = sgStyle.add(new ColorSetting.Builder()
        .name("accent-color-2").description("Zweite Farbe fuer Verlaeufe.")
        .defaultValue(new SettingColor(60, 140, 255)).build());
    private final Setting<SettingColor> background = sgStyle.add(new ColorSetting.Builder()
        .name("background-color").description("Hintergrundfarbe.")
        .defaultValue(new SettingColor(15, 15, 20, 190)).build());
    private final Setting<Integer> radius = sgStyle.add(new IntSetting.Builder()
        .name("corner-radius").description("Rundung der Ecken (0 = eckig).")
        .defaultValue(3).min(0).sliderRange(0, 8).build());
    private final Setting<Boolean> shadow = sgStyle.add(new BoolSetting.Builder()
        .name("shadow").description("Textschatten.").defaultValue(true).build());

    private HudRenderer r;
    private double s;
    private boolean sh;
    private double rad;
    private Color txt, acc, acc2, bg;
    private String lbl;

    public RelogCounterHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        RelogsModule module = Modules.get().get(RelogsModule.class);
        boolean active = module != null && module.isActive();
        if (!active && !isInEditor()) return;

        r = renderer;
        s = scale.get();
        sh = shadow.get();
        txt = textColor.get();
        acc = accent.get();
        acc2 = accent2.get();
        bg = background.get();
        rad = radius.get() * s;
        lbl = label.get();

        int count = RelogTracker.INSTANCE.getRelogs();
        String v = String.valueOf(count);

        switch (design.get()) {
            case Normal -> normal(v);
            case Card -> card(v, false);
            case Bar -> bar(v);
            case Badge -> badge(v);
            case Outline -> outline(v);
            case Minimal -> minimal(v);
            case Pill -> pill(v);
            case Glass -> glass(v);
            case Neon -> neon(v);
            case Gradient -> gradient(v);
            case Terminal -> terminal(v);
            case Split -> split(v);
            case Counter -> counter(v);
            case Underline -> underline(v);
            case Brackets -> brackets(v);
            case Dots -> dots(v, count);
            case Meter -> meter(v, count);
            case ShadowCard -> card(v, true);
        }
    }

    // ---------------------------------------------------------------- Designs

    private void normal(String v) {
        String line = lbl + ": " + v;
        double w = tw(line), h = th();
        boolean title = showTitle.get();
        if (title) { w = Math.max(w, tw(TITLE)); h += th() + 2 * s; }
        setSize(w, h);
        double cy = y;
        if (title) { text(TITLE, x, cy, acc); cy += th() + 2 * s; }
        text(line, x, cy, txt);
    }

    private void card(String v, boolean dropShadow) {
        double pad = 4 * s, g = 2 * s;
        String line = lbl + ": " + v;
        boolean title = showTitle.get();
        double w = tw(line) + 2 * pad, h = th() + 2 * pad;
        if (title) { w = Math.max(w, tw(TITLE) + 2 * pad); h += th() + g; }
        setSize(w, h);
        if (dropShadow) {
            for (int i = 3; i >= 1; i--) rounded(x + i * s, y + i * s, w, h, rad, alpha(0, 0, 0, 40 / i));
        }
        rounded(x, y, w, h, rad, bg);
        quad(x + rad, y, w - 2 * rad, Math.max(1, s), acc);
        double cy = y + pad;
        if (title) { text(TITLE, x + pad, cy, acc); cy += th() + g; }
        text(line, x + pad, cy, txt);
    }

    private void bar(String v) {
        double pad = 4 * s, stripe = Math.max(2, 2 * s);
        String line = lbl + ": " + v;
        double w = stripe + tw(line) + 2 * pad, h = th() + 2 * pad;
        setSize(w, h);
        quad(x, y, w, h, bg);
        quad(x, y, stripe, h, acc);
        text(line, x + stripe + pad, y + pad, txt);
    }

    private void badge(String v) {
        double pad = 4 * s;
        double lw = tw(lbl) + 2 * pad, vw = tw(v) + 2 * pad, h = th() + 2 * pad;
        double w = lw + vw;
        setSize(w, h);
        rounded(x, y, w, h, rad, bg);
        rounded(x, y, lw, h, rad, acc);
        quad(x + lw - rad, y, rad, h, acc);
        text(lbl, x + pad, y + pad, contrast(acc));
        text(v, x + lw + pad, y + pad, txt);
    }

    private void outline(String v) {
        double pad = 4 * s, b = Math.max(1, s);
        String line = lbl + ": " + v;
        boolean title = showTitle.get();
        double w = tw(line) + 2 * pad, h = th() + 2 * pad;
        if (title) { w = Math.max(w, tw(TITLE) + 2 * pad); h += th() + 2 * s; }
        setSize(w, h);
        border(x, y, w, h, b, acc);
        double cy = y + pad;
        if (title) { text(TITLE, x + pad, cy, acc); cy += th() + 2 * s; }
        text(line, x + pad, cy, txt);
    }

    private void minimal(String v) {
        double big = s * 2.2, small = s * 0.8, g = 1 * s;
        double w = Math.max(tw(v, big), tw(lbl, small));
        double h = th(small) + g + th(big);
        setSize(w, h);
        text(lbl, x, y, acc, small);
        text(v, x, y + th(small) + g, txt, big);
    }

    private void pill(String v) {
        double b = Math.max(1, s), pad = 4 * s;
        double h = th() + 2 * pad * 0.7;
        double ih = h - 2 * b;
        double cw = Math.max(tw(v) + 2 * pad * 0.7, ih);
        double w = b + cw + pad + tw(lbl) + pad + b;
        setSize(w, h);
        rounded(x, y, w, h, h / 2, bg);
        rounded(x + b, y + b, cw, ih, ih / 2, acc);
        text(v, x + b + (cw - tw(v)) / 2, y + (h - th()) / 2, contrast(acc));
        text(lbl, x + b + cw + pad, y + (h - th()) / 2, txt);
    }

    private void glass(String v) {
        double pad = 4 * s, b = Math.max(1, s * 0.75);
        String line = lbl + ": " + v;
        double w = tw(line) + 2 * pad, h = th() + 2 * pad;
        setSize(w, h);
        quad(x, y, w, h, bg);
        quad(x, y, w, h * 0.5, alpha(255, 255, 255, 18));
        border(x, y, w, h, b, alpha(255, 255, 255, 55));
        quad(x + b, y + b, w - 2 * b, b, alpha(255, 255, 255, 90));
        text(lbl + ":", x + pad, y + pad, alpha(txt, 200));
        text(v, x + pad + tw(lbl + ": "), y + pad, txt);
    }

    private void neon(String v) {
        double pad = 5 * s, b = Math.max(1, s);
        String line = lbl + ": " + v;
        double w = tw(line) + 2 * pad, h = th() + 2 * pad;
        setSize(w, h);
        for (int i = 3; i >= 1; i--) {
            border(x - i * b, y - i * b, w + 2 * i * b, h + 2 * i * b, b, alpha(acc, Math.max(8, acc.a / (i * 3))));
        }
        quad(x, y, w, h, alpha(bg, 235));
        border(x, y, w, h, b, acc);
        text(lbl + ": ", x + pad, y + pad, txt);
        text(v, x + pad + tw(lbl + ": "), y + pad, acc);
    }

    private void gradient(String v) {
        double pad = 4 * s;
        String line = lbl + ": " + v;
        double w = tw(line) + 2 * pad, h = th() + 2 * pad;
        setSize(w, h);
        hGradient(x, y, w, h, acc, acc2);
        text(line, x + pad, y + pad, txt);
    }

    private void terminal(String v) {
        double pad = 4 * s;
        double head = th() + 2 * s;
        String cursor = (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : " ";
        String prompt = "> ";
        String body = lbl.toLowerCase() + " = " + v;
        double w = tw(prompt + body + "_") + 2 * pad;
        w = Math.max(w, 3 * (head * 0.45 + 2 * s) + 2 * pad);
        double h = head + th() + 2 * pad;
        setSize(w, h);
        quad(x, y, w, h, bg);
        quad(x, y, w, head, alpha(0, 0, 0, 90));
        double d = head * 0.45;
        Color[] dots = { new Color(255, 95, 86), new Color(255, 189, 46), new Color(39, 201, 63) };
        for (int i = 0; i < 3; i++) quad(x + pad + i * (d + 2 * s), y + (head - d) / 2, d, d, dots[i]);
        double by = y + head + pad * 0.5;
        text(prompt, x + pad, by, acc);
        text(body, x + pad + tw(prompt), by, txt);
        text(cursor, x + pad + tw(prompt + body), by, acc);
    }

    private void split(String v) {
        double pad = 4 * s, big = s * 1.6;
        double hh = th(s * 0.85) + 2 * s * 1.5;
        double w = Math.max(tw(lbl, s * 0.85), tw(v, big)) + 2 * pad;
        double h = hh + th(big) + 2 * pad * 0.8;
        setSize(w, h);
        rounded(x, y, w, h, rad, bg);
        rounded(x, y, w, hh, rad, acc);
        quad(x, y + hh - rad, w, rad, acc);
        text(lbl, x + (w - tw(lbl, s * 0.85)) / 2, y + s * 1.5, contrast(acc), s * 0.85);
        text(v, x + (w - tw(v, big)) / 2, y + hh + pad * 0.8, txt, big);
    }

    private void counter(String v) {
        double pad = 4 * s, stripe = Math.max(2, 2.5 * s), big = s * 1.9, small = s * 0.8;
        double w = stripe + Math.max(tw(v, big), tw(lbl, small)) + 2 * pad + 2 * s;
        double h = th(small) + th(big) + 2 * pad + s;
        setSize(w, h);
        rounded(x, y, w, h, rad, bg);
        quad(x, y + rad, stripe, h - 2 * rad, acc);
        text(lbl.toUpperCase(), x + stripe + pad, y + pad, acc, small);
        text(v, x + stripe + pad, y + pad + th(small) + s, txt, big);
    }

    private void underline(String v) {
        double g = 2 * s, ul = Math.max(1, 1.5 * s);
        String line = lbl + ": " + v;
        boolean title = showTitle.get();
        double w = tw(line), h = th() + g + ul;
        if (title) { w = Math.max(w, tw(TITLE)); h += th() + g; }
        setSize(w, h);
        double cy = y;
        if (title) { text(TITLE, x, cy, acc); cy += th() + g; }
        text(line, x, cy, txt);
        hGradient(x, cy + th() + g, w, ul, acc, acc2);
    }

    private void brackets(String v) {
        double g = 2 * s;
        String mid = lbl + ": " + v;
        double bw = tw("[");
        double w = bw + g + tw(mid) + g + tw("]");
        setSize(w, th());
        text("[", x, y, acc);
        text(mid, x + bw + g, y, txt);
        text("]", x + bw + g + tw(mid) + g, y, acc);
    }

    private void dots(String v, int count) {
        double pad = 4 * s, g = 2 * s;
        final int n = 10;
        String line = lbl + ": " + v + (count > n ? "+" : "");
        double d = th() * 0.6;
        double rowW = n * d + (n - 1) * g;
        double w = Math.max(tw(line), rowW) + 2 * pad;
        double h = th() + g * 1.5 + d + 2 * pad;
        setSize(w, h);
        rounded(x, y, w, h, rad, bg);
        text(line, x + pad, y + pad, txt);
        int filled = Math.min(count, n);
        double dy = y + pad + th() + g * 1.5;
        for (int i = 0; i < n; i++) {
            Color c = i < filled ? acc : alpha(txt, 50);
            rounded(x + pad + i * (d + g), dy, d, d, Math.min(rad, d / 2), c);
        }
    }

    private void meter(String v, int count) {
        double pad = 4 * s, g = 2 * s, bh = Math.max(2, 3 * s);
        double barW = Math.max(70 * s, tw(lbl) + tw(v) + 8 * s);
        double w = barW + 2 * pad, h = th() + g + bh + 2 * pad;
        setSize(w, h);
        rounded(x, y, w, h, rad, bg);
        text(lbl, x + pad, y + pad, txt);
        text(v, x + w - pad - tw(v), y + pad, acc);
        double by = y + pad + th() + g;
        quad(x + pad, by, barW, bh, alpha(255, 255, 255, 30));
        double frac = Math.min(1, count / (double) meterMax.get());
        double fw = Math.max(count > 0 ? 1 : 0, barW * frac);
        if (fw > 0) hGradient(x + pad, by, fw, bh, acc, acc2);
    }

    // ---------------------------------------------------------------- Helfer

    private double tw(String t) { return r.textWidth(t, sh, s); }
    private double tw(String t, double sc) { return r.textWidth(t, sh, sc); }
    private double th() { return r.textHeight(sh, s); }
    private double th(double sc) { return r.textHeight(sh, sc); }
    private void text(String t, double px, double py, Color c) { r.text(t, px, py, c, sh, s); }
    private void text(String t, double px, double py, Color c, double sc) { r.text(t, px, py, c, sh, sc); }
    private void quad(double px, double py, double w, double h, Color c) { if (w > 0 && h > 0) r.quad(px, py, w, h, c); }

    private void border(double px, double py, double w, double h, double b, Color c) {
        quad(px, py, w, b, c);
        quad(px, py + h - b, w, b, c);
        quad(px, py + b, b, h - 2 * b, c);
        quad(px + w - b, py + b, b, h - 2 * b, c);
    }

    private void rounded(double px, double py, double w, double h, double rd, Color c) {
        rd = Math.min(rd, Math.min(w, h) / 2);
        if (rd < 1) { quad(px, py, w, h, c); return; }
        int steps = (int) Math.ceil(rd);
        for (int i = 0; i < steps; i++) {
            double dy = rd - i - 0.5;
            double inset = rd - Math.sqrt(Math.max(0, rd * rd - dy * dy));
            quad(px + inset, py + i, w - 2 * inset, 1, c);
            quad(px + inset, py + h - i - 1, w - 2 * inset, 1, c);
        }
        quad(px, py + steps, w, h - 2 * steps, c);
    }

    private void hGradient(double px, double py, double w, double h, Color a, Color b) {
        int n = (int) Math.ceil(w);
        for (int i = 0; i < n; i++) {
            double t = n <= 1 ? 0 : i / (double) (n - 1);
            Color c = new Color(
                (int) (a.r + (b.r - a.r) * t), (int) (a.g + (b.g - a.g) * t),
                (int) (a.b + (b.b - a.b) * t), (int) (a.a + (b.a - a.a) * t));
            quad(px + i, py, Math.min(1, w - i), h, c);
        }
    }

    private static Color alpha(int r, int g, int b, int a) { return new Color(r, g, b, Math.max(0, Math.min(255, a))); }
    private static Color alpha(Color c, int a) { return new Color(c.r, c.g, c.b, Math.max(0, Math.min(255, a))); }

    private static Color contrast(Color c) {
        double lum = 0.299 * c.r + 0.587 * c.g + 0.114 * c.b;
        return lum > 150 ? new Color(20, 20, 20, 255) : new Color(255, 255, 255, 255);
    }
}
