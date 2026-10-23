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
        BoffoAddon.HUD_GROUP,
        "relog-counter",
        "Zeigt an, wie oft du auf diesem Server relogged hast.",
        RelogCounterHud::new
    );

    public enum Design {
        Normal,   // nur Text, wie bisher
        Card,     // dunkle Karte mit Akzentbalken links
        Bar,      // schlanke Zeile mit Linie unten
        Badge,    // zweigeteilte Plakette: Label | Zahl
        Outline   // Rahmen in Akzentfarbe
    }

    private static final String TITLE = "Boffo Addon";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("Style");

    private final Setting<Design> design = sgGeneral.add(new EnumSetting.Builder<Design>()
        .name("design")
        .description("Aussehen der Anzeige.")
        .defaultValue(Design.Normal)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Groesse der Anzeige.")
        .defaultValue(1.0)
        .min(0.3)
        .sliderRange(0.3, 5.0)
        .build()
    );

    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Zeigt die Ueberschrift \"Boffo Addon\" (nur bei Normal, Card und Outline).")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textColor = sgStyle.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Textfarbe.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> accent = sgStyle.add(new ColorSetting.Builder()
        .name("accent-color")
        .description("Akzentfarbe (Balken, Linie, Rahmen, Ueberschrift).")
        .defaultValue(new SettingColor(145, 61, 226))
        .build()
    );

    private final Setting<SettingColor> background = sgStyle.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Hintergrundfarbe der modernen Designs.")
        .defaultValue(new SettingColor(15, 15, 20, 190))
        .build()
    );

    private final Setting<Boolean> shadow = sgStyle.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Textschatten.")
        .defaultValue(true)
        .build()
    );

    public RelogCounterHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer r) {
        // Nur anzeigen, wenn das Modul "Relogs" an ist (im HUD-Editor immer).
        RelogsModule module = Modules.get().get(RelogsModule.class);
        boolean on = module != null && module.isActive();
        if (!on && !isInEditor()) return;

        double s = scale.get();
        boolean sh = shadow.get();
        double lh = r.textHeight(sh, s);
        double p = 4 * s;
        double gap = 2 * s;
        boolean title = showTitle.get();

        String count = String.valueOf(RelogTracker.INSTANCE.getRelogs());
        String line = "Relogs: " + count;

        Color text = textColor.get();
        Color acc = accent.get();
        Color bg = background.get();

        switch (design.get()) {
            case Normal -> {
                double w = r.textWidth(line, sh, s);
                double h = lh;
                if (title) {
                    w = Math.max(w, r.textWidth(TITLE, sh, s));
                    h = lh * 2 + gap;
                }
                setSize(w, h);

                double ty = y;
                if (title) {
                    r.text(TITLE, x, ty, acc, sh, s);
                    ty += lh + gap;
                }
                r.text(line, x, ty, text, sh, s);
            }

            case Card -> {
                double bar = Math.max(2, 2 * s);
                double cw = r.textWidth(line, sh, s);
                double ch = lh;
                if (title) {
                    cw = Math.max(cw, r.textWidth(TITLE, sh, s));
                    ch = lh * 2 + gap;
                }
                double w = bar + p + cw + p;
                double h = ch + 2 * p;
                setSize(w, h);

                r.quad(x, y, w, h, bg);
                r.quad(x, y, bar, h, acc);

                double tx = x + bar + p;
                double ty = y + p;
                if (title) {
                    r.text(TITLE, tx, ty, acc, sh, s);
                    ty += lh + gap;
                }
                r.text(line, tx, ty, text, sh, s);
            }

            case Bar -> {
                double line1 = Math.max(1, 1.5 * s);
                double w = p + r.textWidth(line, sh, s) + p;
                double h = lh + 2 * p;
                setSize(w, h);

                r.quad(x, y, w, h, bg);
                r.quad(x, y + h - line1, w, line1, acc);
                r.text(line, x + p, y + p, text, sh, s);
            }

            case Badge -> {
                String label = "Relogs";
                double lw = p + r.textWidth(label, sh, s) + p;
                double vw = p + r.textWidth(count, sh, s) + p;
                double h = lh + 2 * p;
                setSize(lw + vw, h);

                r.quad(x, y, lw, h, acc);
                r.quad(x + lw, y, vw, h, bg);
                r.text(label, x + p, y + p, text, sh, s);
                r.text(count, x + lw + p, y + p, text, sh, s);
            }

            case Outline -> {
                double th = Math.max(1, s);
                double cw = r.textWidth(line, sh, s);
                double ch = lh;
                if (title) {
                    cw = Math.max(cw, r.textWidth(TITLE, sh, s));
                    ch = lh * 2 + gap;
                }
                double w = p + cw + p;
                double h = ch + 2 * p;
                setSize(w, h);

                r.quad(x, y, w, h, bg);
                r.quad(x, y, w, th, acc);                 // oben
                r.quad(x, y + h - th, w, th, acc);        // unten
                r.quad(x, y, th, h, acc);                 // links
                r.quad(x + w - th, y, th, h, acc);        // rechts

                double tx = x + p;
                double ty = y + p;
                if (title) {
                    r.text(TITLE, tx, ty, acc, sh, s);
                    ty += lh + gap;
                }
                r.text(line, tx, ty, text, sh, s);
            }
        }
    }
}
