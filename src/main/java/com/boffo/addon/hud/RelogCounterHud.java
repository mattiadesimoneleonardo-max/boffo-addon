package com.boffo.addon.hud;

import com.boffo.addon.BoffoAddon;
import com.boffo.addon.RelogTracker;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class RelogCounterHud extends HudElement {
    public static final HudElementInfo<RelogCounterHud> INFO = new HudElementInfo<>(
        BoffoAddon.HUD_GROUP,
        "relog-counter",
        "Zeigt an, wie oft du relogged hast.",
        RelogCounterHud::new
    );

    private static final Color TITLE_COLOR = new Color(145, 61, 226);
    private static final String TITLE = "Boffo Addon";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

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
        .description("Zeigt die Ueberschrift \"Boffo Addon\" ueber dem Zaehler.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Textfarbe des Zaehlers.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Textschatten.")
        .defaultValue(true)
        .build()
    );

    public RelogCounterHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double s = scale.get();
        boolean sh = shadow.get();
        String line = "Relogs: " + RelogTracker.INSTANCE.getRelogs();

        double lineHeight = renderer.textHeight(sh, s);
        double gap = 2 * s;

        double width = renderer.textWidth(line, sh, s);
        double height = lineHeight;

        if (showTitle.get()) {
            width = Math.max(width, renderer.textWidth(TITLE, sh, s));
            height = lineHeight * 2 + gap;
        }

        setSize(width, height);

        double yy = y;
        if (showTitle.get()) {
            renderer.text(TITLE, x, yy, TITLE_COLOR, sh, s);
            yy += lineHeight + gap;
        }
        renderer.text(line, x, yy, color.get(), sh, s);
    }
}
