package com.boffo.addon.modules;

import com.boffo.addon.BoffoAddon;
import com.boffo.addon.hud.RelogCounterHud.Design;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class RelogsModule extends Module {
    public enum Theme {
        Purple("Purple", new int[]{145, 61, 226}, new int[]{60, 140, 255}),
        Ocean("Ocean", new int[]{0, 180, 216}, new int[]{72, 120, 255}),
        Sunset("Sunset", new int[]{255, 94, 98}, new int[]{255, 183, 94}),
        Emerald("Emerald", new int[]{17, 190, 140}, new int[]{86, 235, 130}),
        Rose("Rose", new int[]{236, 72, 153}, new int[]{168, 85, 247}),
        Gold("Gold", new int[]{255, 190, 60}, new int[]{255, 120, 60}),
        Mono("Mono", new int[]{210, 210, 225}, new int[]{130, 130, 150}),
        Custom("Custom", null, null);

        public final String title;
        public final int[] a, b;
        Theme(String title, int[] a, int[] b) { this.title = title; this.a = a; this.b = b; }
        @Override public String toString() { return title; }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Farben");

    public final Setting<Design> design = sgGeneral.add(new EnumSetting.Builder<Design>()
        .name("design").description("Aussehen der Anzeige.").defaultValue(Design.Aurora).build());
    public final Setting<String> label = sgGeneral.add(new StringSetting.Builder()
        .name("label").description("Beschriftung.").defaultValue("Relogs").build());
    public final Setting<Boolean> showServer = sgGeneral.add(new BoolSetting.Builder()
        .name("show-server").description("Zeigt den Servernamen an.")
        .defaultValue(true).build());
    public final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale").description("Groesse der Anzeige.").defaultValue(1.2).min(0.3).sliderRange(0.5, 4).build());
    public final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("corner-radius").description("Rundung der Ecken (0 = eckig).")
        .defaultValue(5).min(0).sliderRange(0, 12).build());
    public final Setting<Integer> meterMax = sgGeneral.add(new IntSetting.Builder()
        .name("meter-max").description("Wert, bei dem der Balken im Design Meter voll ist.")
        .defaultValue(10).min(1).sliderRange(1, 50).build());
    public final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Textschatten.").defaultValue(true).build());

    public final Setting<Theme> theme = sgColors.add(new EnumSetting.Builder<Theme>()
        .name("theme").description("Farbschema. Custom nutzt die Farben darunter.").defaultValue(Theme.Purple).build());
    public final Setting<SettingColor> customA = sgColors.add(new ColorSetting.Builder()
        .name("custom-color-1").description("Erste Farbe (nur bei Theme Custom).")
        .defaultValue(new SettingColor(145, 61, 226)).build());
    public final Setting<SettingColor> customB = sgColors.add(new ColorSetting.Builder()
        .name("custom-color-2").description("Zweite Farbe (nur bei Theme Custom).")
        .defaultValue(new SettingColor(60, 140, 255)).build());
    public final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color").description("Textfarbe.").defaultValue(new SettingColor(255, 255, 255)).build());
    public final Setting<SettingColor> background = sgColors.add(new ColorSetting.Builder()
        .name("background-color").description("Hintergrundfarbe.")
        .defaultValue(new SettingColor(18, 18, 26, 215)).build());

    public RelogsModule() {
        super(BoffoAddon.CATEGORY, "relogs", "Zaehlt pro Server, wie oft du relogged hast, und zeigt es im HUD an. Design und Farben stellst du hier ein.");
    }

    public Color accent() {
        Theme t = theme.get();
        return t == Theme.Custom ? customA.get() : new Color(t.a[0], t.a[1], t.a[2], 255);
    }

    public Color accent2() {
        Theme t = theme.get();
        return t == Theme.Custom ? customB.get() : new Color(t.b[0], t.b[1], t.b[2], 255);
    }
}
