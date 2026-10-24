package com.boffo.addon.modules;

import com.boffo.addon.BoffoAddon;
import com.boffo.addon.BoffoToast;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Boffo Debug - markiert Spieler-Basen (Stashes, Farmen) UNTER Y -10.
 *
 * Pro Base gibt es genau
 *   - 1 Strahl (senkrecht, in der Mitte der Base)
 *   - 1 durchsichtigen Kasten um die ganze Base
 *
 * Sensitivity (5-50) = Anzahl Spieler-Bloecke, die eine Base mindestens haben muss.
 *   50 -> 50 Bloecke noetig, 5 -> 5 Bloecke noetig.
 *
 * Strukturen (Trial Chambers, Dungeons, Ancient Cities, Strongholds ...) werden
 * ueber Marker-Bloecke erkannt und nicht markiert.
 */
public class BoffoDebugModule extends Module {
    /** Es zaehlen nur Bloecke mit y KLEINER als dieser Wert. */
    private static final int Y_LIMIT = -10;

    private static final int CELL_SHIFT = 5;            // Zellen von 32x32 Bloecken
    private static final int STRUCTURE_LIMIT = 8;       // ab diesem Gewicht = Struktur, keine Base
    private static final int STRUCTURE_CELL_RANGE = 2;  // Zellen um die Base, in denen Marker zaehlen
    private static final int CHUNKS_PER_TICK = 4;
    private static final int EVAL_INTERVAL = 20;        // Ticks zwischen Auswertungen
    private static final int BOX_PADDING = 1;           // Kasten ragt 1 Block ueber die Base hinaus

    // ---- Spieler-Bloecke (koennen nicht natuerlich entstehen) ----
    private static final Set<String> PLAYER_EXACT = Set.of(
        // Storage / Utility
        "chest", "trapped_chest", "barrel", "ender_chest", "furnace", "blast_furnace", "smoker",
        "anvil", "chipped_anvil", "damaged_anvil", "enchanting_table", "brewing_stand", "cauldron",
        "beacon", "hopper", "dropper", "dispenser", "crafting_table", "grindstone", "stonecutter",
        "loom", "cartography_table", "fletching_table", "smithing_table", "composter", "jukebox",
        "lodestone", "respawn_anchor", "conduit", "crafter", "spawner",
        // Redstone / Farm-Technik
        "observer", "piston", "sticky_piston", "repeater", "comparator", "redstone_lamp",
        "redstone_wire", "redstone_torch", "note_block", "tnt", "tripwire_hook", "target",
        "slime_block", "honey_block", "daylight_detector", "scaffolding", "dried_kelp_block",
        // Farmen
        "farmland", "wheat", "carrots", "potatoes", "beetroots", "melon_stem", "pumpkin_stem",
        "attached_melon_stem", "attached_pumpkin_stem", "nether_wart",
        // Bau-Bloecke
        "glowstone", "iron_block", "diamond_block", "emerald_block", "lapis_block", "redstone_block",
        "netherite_block", "coal_block", "bricks", "quartz_block", "quartz_bricks", "quartz_pillar",
        "chiseled_quartz_block", "smooth_quartz"
    );
    private static final String[] PLAYER_SUFFIX = {
        "_concrete", "_concrete_powder", "_glazed_terracotta", "_bed", "shulker_box",
        "_stained_glass", "_pressure_plate", "copper_chest"
    };

    // ---- Struktur-Marker mit Gewicht ----
    private static final Map<String, Integer> STRUCTURE_EXACT = Map.ofEntries(
        // stark: nur in Strukturen moeglich
        Map.entry("trial_spawner", 8), Map.entry("vault", 8), Map.entry("heavy_core", 8),
        Map.entry("end_portal_frame", 8), Map.entry("end_portal", 8), Map.entry("end_gateway", 8),
        Map.entry("sculk_shrieker", 8), Map.entry("sculk_catalyst", 8),
        Map.entry("suspicious_sand", 8), Map.entry("suspicious_gravel", 8),
        // mittel
        Map.entry("reinforced_deepslate", 3), Map.entry("crying_obsidian", 3), Map.entry("bell", 3),
        Map.entry("decorated_pot", 3), Map.entry("chiseled_tuff", 3), Map.entry("tuff_bricks", 3),
        // schwach: viele davon = Struktur (Dungeon, Mineshaft, Ancient City ...)
        Map.entry("sculk_sensor", 1), Map.entry("calibrated_sculk_sensor", 1), Map.entry("sculk", 1),
        Map.entry("sculk_vein", 1), Map.entry("hay_block", 1), Map.entry("dirt_path", 1),
        Map.entry("cobweb", 1), Map.entry("copper_grate", 1), Map.entry("mossy_cobblestone", 1),
        Map.entry("nether_bricks", 1), Map.entry("purpur_block", 1), Map.entry("purpur_pillar", 1),
        Map.entry("end_stone_bricks", 1), Map.entry("prismarine_bricks", 1),
        Map.entry("dark_prismarine", 1), Map.entry("sea_lantern", 1)
    );
    private static final String[] STRUCTURE_CONTAINS = { "copper_bulb" };  // Gewicht 1
    private static final String[] STRUCTURE_PREFIX = { "infested_" };      // Gewicht 3

    // ------------------------------------------------------------------ Einstellungen

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .description("So viele Spieler-Bloecke muss eine Base mindestens haben (5-50). 50 = 50 Bloecke, 5 = 5 Bloecke.")
        .defaultValue(25)
        .range(5, 50)
        .sliderRange(5, 50)
        .build());

    private final Setting<SettingColor> beamColor = sgGeneral.add(new ColorSetting.Builder()
        .name("beam-color")
        .description("Farbe des Strahls.")
        .defaultValue(new SettingColor(255, 60, 60))
        .build());

    private final Setting<SettingColor> boxColor = sgGeneral.add(new ColorSetting.Builder()
        .name("box-color")
        .description("Farbe des Kastens um die Base.")
        .defaultValue(new SettingColor(255, 60, 60))
        .build());

    private final Setting<Integer> boxOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("box-opacity")
        .description("Wie durchsichtig die Flaechen des Kastens sind (kleiner = durchsichtiger).")
        .defaultValue(35)
        .range(0, 150)
        .sliderRange(0, 150)
        .build());

    private final Setting<SettingColor> toastColor = sgGeneral.add(new ColorSetting.Builder()
        .name("toast-color")
        .description("Farbe der Benachrichtigung oben rechts.")
        .defaultValue(new SettingColor(255, 60, 60))
        .build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-chat")
        .description("Schreibt in den Chat, was das Modul sieht (Bloecke, Basen, gefilterte Strukturen).")
        .defaultValue(true)
        .build());

    // ------------------------------------------------------------------ Daten

    private static final class Cell {
        final Set<Long> player = new HashSet<>();
        final Map<Long, Integer> structure = new HashMap<>();
        boolean empty() { return player.isEmpty() && structure.isEmpty(); }
    }

    /** Eine gefundene Base: Mitte fuer den Strahl, Grenzen fuer den Kasten. */
    private record Base(double cx, double cz, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    private final Queue<Chunk> queue = new ConcurrentLinkedQueue<>();
    private final Map<Long, Cell> cells = new HashMap<>();
    private final Map<Block, Integer> cache = new HashMap<>();
    private final Set<Long> toasted = new HashSet<>();
    private List<Base> bases = new ArrayList<>();
    private int evalTimer;
    private String lastSummary = "";
    private final Set<String> reportedFilters = new HashSet<>();

    public BoffoDebugModule() {
        super(BoffoAddon.CATEGORY, "boffo-debug", "Markiert Spieler-Basen unter Y -10 mit Strahl und Kasten.");
    }

    // ------------------------------------------------------------------ Chat / Fehler

    private String lastError = "";

    private void say(String text) {
        info("%s", text);
    }

    private void fail(String where, Throwable t) {
        String msg = "FEHLER in " + where + ": " + t;
        if (!msg.equals(lastError)) {
            lastError = msg;
            error("%s", msg);
        }
        t.printStackTrace();
    }

    // ------------------------------------------------------------------ Lebenszyklus

    @Override
    public void onActivate() {
        reset();
        for (Chunk chunk : Utils.chunks()) queue.add(chunk);
        say("Boffo Debug V2 gestartet (nur Y unter " + Y_LIMIT + ", Sensitivity " + sensitivity.get() + ")");
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        reset();
    }

    private void reset() {
        queue.clear();
        cells.clear();
        toasted.clear();
        bases = new ArrayList<>();
        evalTimer = 0;
        lastSummary = "";
        reportedFilters.clear();
    }

    // ------------------------------------------------------------------ Events

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        queue.add(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        try {
            BlockPos pos = event.pos;
            if (pos.getY() >= Y_LIMIT) return;
            removeAt(pos);
            add(pos.getX(), pos.getY(), pos.getZ(), event.newState.getBlock());
        } catch (Throwable t) {
            fail("onBlockUpdate", t);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        try {
            for (int i = 0; i < CHUNKS_PER_TICK; i++) {
                Chunk chunk = queue.poll();
                if (chunk == null) break;
                scan(chunk);
            }

            // erst auswerten, wenn alle Chunks gescannt sind, damit Struktur-Marker
            // in Nachbar-Chunks schon bekannt sind
            if (!queue.isEmpty()) return;
            if (++evalTimer < EVAL_INTERVAL) return;
            evalTimer = 0;
            evaluate();
        } catch (Throwable t) {
            fail("onTick", t);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || bases.isEmpty()) return;
        try {
            renderBases(event);
        } catch (Throwable t) {
            fail("onRender", t);
        }
    }

    private void renderBases(Render3DEvent event) {
        SettingColor beam = beamColor.get();
        SettingColor box = boxColor.get();
        Color beamSide = new Color(beam.r, beam.g, beam.b, 90);
        Color beamLine = new Color(beam.r, beam.g, beam.b, 255);
        Color boxSide = new Color(box.r, box.g, box.b, boxOpacity.get());
        Color boxLine = new Color(box.r, box.g, box.b, 220);

        double bottom = mc.world.getBottomY();
        double top = bottom + mc.world.getHeight();

        for (Base b : bases) {
            // durchsichtiger Kasten um die ganze Base
            event.renderer.box(
                b.minX() - BOX_PADDING, b.minY() - BOX_PADDING, b.minZ() - BOX_PADDING,
                b.maxX() + 1 + BOX_PADDING, b.maxY() + 1 + BOX_PADDING, b.maxZ() + 1 + BOX_PADDING,
                boxSide, boxLine, ShapeMode.Both, 0);

            // 1 Strahl pro Base
            event.renderer.box(
                b.cx() - 0.5, bottom, b.cz() - 0.5,
                b.cx() + 0.5, top, b.cz() + 0.5,
                beamSide, beamLine, ShapeMode.Both, 0);
        }
    }

    // ------------------------------------------------------------------ Scan

    private void scan(Chunk chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        removeChunk(chunkX, chunkZ);

        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int bottom = chunk.getBottomY();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bottom; y < Y_LIMIT; y++) { // nur y < Y_LIMIT
                    pos.set(baseX + x, y, baseZ + z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) continue;
                    add(baseX + x, y, baseZ + z, state.getBlock());
                }
            }
        }
    }

    /** -1 = Spieler-Block, 0 = egal, groesser 0 = Struktur-Marker (Gewicht). */
    private int classify(Block block) {
        return cache.computeIfAbsent(block, b -> {
            String path = Registries.BLOCK.getId(b).getPath();

            Integer w = STRUCTURE_EXACT.get(path);
            if (w != null) return w;
            for (String part : STRUCTURE_CONTAINS) if (path.contains(part)) return 1;
            for (String prefix : STRUCTURE_PREFIX) if (path.startsWith(prefix)) return 3;

            if (PLAYER_EXACT.contains(path)) return -1;
            for (String suffix : PLAYER_SUFFIX) if (path.endsWith(suffix)) return -1;
            return 0;
        });
    }

    private static long cellKey(int x, int z) {
        return (((long) (x >> CELL_SHIFT)) << 32) | ((z >> CELL_SHIFT) & 0xFFFFFFFFL);
    }

    private static long cellKeyRaw(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    private void add(int x, int y, int z, Block block) {
        if (y >= Y_LIMIT) return;
        int type = classify(block);
        if (type == 0) return;
        Cell cell = cells.computeIfAbsent(cellKey(x, z), k -> new Cell());
        long pos = BlockPos.asLong(x, y, z);
        if (type < 0) cell.player.add(pos);
        else cell.structure.put(pos, type);
    }

    private void removeAt(BlockPos pos) {
        long key = cellKey(pos.getX(), pos.getZ());
        Cell cell = cells.get(key);
        if (cell == null) return;
        long p = pos.asLong();
        cell.player.remove(p);
        cell.structure.remove(p);
        if (cell.empty()) cells.remove(key);
    }

    private void removeChunk(int chunkX, int chunkZ) {
        // ein Chunk liegt immer komplett in genau einer 32er-Zelle
        long key = cellKey(chunkX << 4, chunkZ << 4);
        Cell cell = cells.get(key);
        if (cell == null) return;
        cell.player.removeIf(p -> (BlockPos.unpackLongX(p) >> 4) == chunkX && (BlockPos.unpackLongZ(p) >> 4) == chunkZ);
        cell.structure.keySet().removeIf(p -> (BlockPos.unpackLongX(p) >> 4) == chunkX && (BlockPos.unpackLongZ(p) >> 4) == chunkZ);
        if (cell.empty()) cells.remove(key);
    }

    // ------------------------------------------------------------------ Auswertung

    private void evaluate() {
        int minBlocks = sensitivity.get(); // 50 -> 50 Bloecke, 5 -> 5 Bloecke

        Set<Long> visited = new HashSet<>();
        List<Base> result = new ArrayList<>();
        int totalPlayer = 0, clusters = 0, tooSmall = 0, filtered = 0, biggest = 0;
        for (Cell c : cells.values()) totalPlayer += c.player.size();

        for (Map.Entry<Long, Cell> entry : cells.entrySet()) {
            long start = entry.getKey();
            if (entry.getValue().player.isEmpty() || visited.contains(start)) continue;

            // Base = zusammenhaengende Zellen mit Spieler-Bloecken
            List<Long> comp = new ArrayList<>();
            ArrayDeque<Long> todo = new ArrayDeque<>();
            todo.add(start);
            visited.add(start);

            while (!todo.isEmpty()) {
                long k = todo.poll();
                comp.add(k);
                int cx = (int) (k >> 32);
                int cz = (int) k;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long nk = cellKeyRaw(cx + dx, cz + dz);
                        Cell n = cells.get(nk);
                        if (n != null && !n.player.isEmpty() && visited.add(nk)) todo.add(nk);
                    }
                }
            }

            int count = 0;
            double sx = 0, sz = 0;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            Set<Long> near = new HashSet<>();

            for (long k : comp) {
                Cell cell = cells.get(k);
                count += cell.player.size();
                for (long p : cell.player) {
                    int px = BlockPos.unpackLongX(p), py = BlockPos.unpackLongY(p), pz = BlockPos.unpackLongZ(p);
                    sx += px;
                    sz += pz;
                    minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                    minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);
                }
                int cx = (int) (k >> 32);
                int cz = (int) k;
                for (int dx = -STRUCTURE_CELL_RANGE; dx <= STRUCTURE_CELL_RANGE; dx++)
                    for (int dz = -STRUCTURE_CELL_RANGE; dz <= STRUCTURE_CELL_RANGE; dz++)
                        near.add(cellKeyRaw(cx + dx, cz + dz));
            }

            clusters++;
            biggest = Math.max(biggest, count);
            if (count < minBlocks) { tooSmall++; continue; }

            int structureWeight = 0;
            for (long nk : near) {
                Cell c = cells.get(nk);
                if (c == null) continue;
                for (int w : c.structure.values()) structureWeight += w;
            }
            if (structureWeight >= STRUCTURE_LIMIT) { // Dungeon, Trial Chamber, Ancient City ...
                filtered++;
                String msg = "Gefiltert (Struktur) bei X " + (int) (sx / count) + " Z " + (int) (sz / count)
                    + ": Bloecke=" + count + ", Struktur-Gewicht=" + structureWeight;
                if (debug.get() && reportedFilters.add(msg)) say(msg);
                continue;
            }

            result.add(new Base(sx / count + 0.5, sz / count + 0.5, minX, minY, minZ, maxX, maxY, maxZ));
        }

        bases = result;
        showToasts(result);

        if (debug.get()) {
            String summary = "Debug: " + totalPlayer + " Spieler-Bloecke unter Y " + Y_LIMIT + ", " + clusters
                + " Basen-Kandidaten (groesster: " + biggest + "), benoetigt: " + minBlocks
                + ", zu klein: " + tooSmall + ", als Struktur gefiltert: " + filtered
                + ", markiert: " + result.size();
            if (!summary.equals(lastSummary)) {
                lastSummary = summary;
                say(summary);
            }
        }
    }

    private void showToasts(List<Base> found) {
        SettingColor c = toastColor.get();
        for (Base b : found) {
            long key = (((long) ((int) b.cx() >> 4)) << 32) | (((int) b.cz() >> 4) & 0xFFFFFFFFL);
            if (!toasted.add(key)) continue;
            String coords = (int) b.cx() + "  Y " + b.minY() + "  Z " + (int) b.cz();
            mc.getToastManager().add(new BoffoToast("Boffo Debug V2", coords, c.r, c.g, c.b));
        }
    }
}

