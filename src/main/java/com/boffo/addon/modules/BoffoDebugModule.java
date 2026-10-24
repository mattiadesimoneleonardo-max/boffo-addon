package com.boffo.addon.modules;

import com.boffo.addon.BoffoAddon;
import com.boffo.addon.BoffoToast;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.*;

/**
 * Boffo Debug – markiert NUR Spieler-Basen (1 Strahl pro Base).
 *
 * Prinzip:
 *  1. "Spieler-Blöcke" (Storage, Redstone, Beton, Betten ...) werden gezählt.
 *  2. "Struktur-Marker" (Spawner, Vault, Glocke, Pfad-Blöcke, Sculk, ...) haben ein Gewicht.
 *     Liegt in der Nähe eines Clusters genug Struktur-Gewicht, ist es ein Village /
 *     Trial Chamber / Dungeon / etc. und wird NICHT markiert.
 *  3. Sensitivity 5-50: je höher, desto weniger Blöcke braucht ein Cluster (55 - sensitivity).
 */
public class BoffoDebugModule extends Module {
    private static final int CELL_SHIFT = 5;          // Zellen 32x32 Blöcke
    private static final int STRUCTURE_LIMIT = 3;     // ab diesem Gewicht = Struktur, keine Base
    private static final int STRUCTURE_CELL_RANGE = 2; // Zellen um den Cluster, in denen Marker zählen
    private static final int CHUNKS_PER_TICK = 2;
    private static final int EVAL_INTERVAL = 20;

    // ---- Spieler-Blöcke (können nicht natürlich entstehen) ----
    private static final Set<String> PLAYER_EXACT = Set.of(
        "chest", "trapped_chest", "barrel", "ender_chest", "furnace", "blast_furnace", "smoker",
        "anvil", "chipped_anvil", "damaged_anvil", "enchanting_table", "brewing_stand", "cauldron",
        "beacon", "hopper", "dropper", "dispenser", "observer", "piston", "sticky_piston",
        "repeater", "comparator", "redstone_lamp", "redstone_wire", "redstone_torch", "note_block",
        "jukebox", "tnt", "crafting_table", "grindstone", "stonecutter", "loom", "cartography_table",
        "fletching_table", "smithing_table", "composter", "tripwire_hook", "target", "slime_block",
        "honey_block", "glowstone", "daylight_detector", "scaffolding", "iron_block", "diamond_block",
        "emerald_block", "lapis_block", "redstone_block", "netherite_block", "coal_block", "bricks",
        "quartz_block", "quartz_bricks", "quartz_pillar", "chiseled_quartz_block", "smooth_quartz",
        "crafter"
    );
    private static final String[] PLAYER_SUFFIX = {
        "_concrete", "_concrete_powder", "_glazed_terracotta", "_bed", "shulker_box",
        "_stained_glass", "_pressure_plate"
    };

    // ---- Struktur-Marker mit Gewicht ----
    private static final Map<String, Integer> STRUCTURE_EXACT = Map.ofEntries(
        Map.entry("spawner", 4), Map.entry("trial_spawner", 4), Map.entry("vault", 4),
        Map.entry("end_portal_frame", 4), Map.entry("end_portal", 4), Map.entry("end_gateway", 4),
        Map.entry("reinforced_deepslate", 2), Map.entry("sculk_shrieker", 4), Map.entry("sculk_catalyst", 4),
        Map.entry("sculk_sensor", 1), Map.entry("calibrated_sculk_sensor", 1), Map.entry("sculk", 1),
        Map.entry("sculk_vein", 1), Map.entry("suspicious_sand", 4), Map.entry("suspicious_gravel", 4),
        Map.entry("crying_obsidian", 2), Map.entry("bell", 2), Map.entry("hay_block", 1),
        Map.entry("dirt_path", 1), Map.entry("cobweb", 1), Map.entry("chiseled_tuff", 1),
        Map.entry("tuff_bricks", 1), Map.entry("copper_grate", 1), Map.entry("decorated_pot", 2),
        Map.entry("mossy_cobblestone", 1), Map.entry("nether_bricks", 1), Map.entry("purpur_block", 1),
        Map.entry("purpur_pillar", 1), Map.entry("end_stone_bricks", 1), Map.entry("prismarine_bricks", 1),
        Map.entry("dark_prismarine", 1), Map.entry("sea_lantern", 1), Map.entry("heavy_core", 4)
    );
    private static final String[] STRUCTURE_CONTAINS = { "copper_bulb" };       // Gewicht 1
    private static final String[] STRUCTURE_PREFIX = { "infested_" };           // Gewicht 2

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .description("Empfindlichkeit (5-50). Hoeher = schon wenige Spieler-Bloecke loesen aus.")
        .defaultValue(25)
        .range(5, 50)
        .sliderRange(5, 50)
        .build());

    private final Setting<SettingColor> beamColor = sgGeneral.add(new ColorSetting.Builder()
        .name("beam-color")
        .description("Farbe des Strahls.")
        .defaultValue(new SettingColor(255, 60, 60))
        .build());

    private final Setting<SettingColor> toastColor = sgGeneral.add(new ColorSetting.Builder()
        .name("toast-color")
        .description("Farbe der Benachrichtigung oben rechts.")
        .defaultValue(new SettingColor(255, 60, 60))
        .build());

    private static final class Cell {
        final Set<Long> player = new HashSet<>();
        final Map<Long, Integer> structure = new HashMap<>();
        boolean empty() { return player.isEmpty() && structure.isEmpty(); }
    }

    private record Beam(double x, double z, int y) {}

    private final Queue<Chunk> queue = new ArrayDeque<>();
    private final Map<Long, Cell> cells = new HashMap<>();
    private final Map<Block, Integer> cache = new HashMap<>();
    private final Set<Long> toasted = new HashSet<>();
    private List<Beam> beams = new ArrayList<>();
    private int evalTimer;

    public BoffoDebugModule() {
        super(BoffoAddon.CATEGORY, "boffo-debug", "Markiert Spieler-Basen mit einem Strahl (keine Strukturen).");
    }

    @Override
    public void onActivate() {
        reset();
        for (Chunk chunk : Utils.chunks()) queue.add(chunk);
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
        beams = new ArrayList<>();
        evalTimer = 0;
    }

    // ------------------------------------------------------------------ Events

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        queue.add(event.chunk()); // falls Compile-Fehler: event.chunk statt event.chunk()
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!inOverworld()) return;
        BlockPos pos = event.pos;
        removeAt(pos);
        add(pos.getX(), pos.getY(), pos.getZ(), event.newState.getBlock());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (!inOverworld()) return;

        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            Chunk chunk = queue.poll();
            if (chunk == null) break;
            scan(chunk);
        }

        // erst auswerten, wenn alle Chunks gescannt sind -> keine falschen Strahlen,
        // weil Struktur-Marker in Nachbar-Chunks noch fehlen
        if (!queue.isEmpty()) return;
        if (++evalTimer < EVAL_INTERVAL) return;
        evalTimer = 0;
        evaluate();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;
        SettingColor c = beamColor.get();
        double bottom = mc.world.getBottomY();
        double top = bottom + mc.world.getHeight();
        for (Beam b : beams) {
            event.renderer.line(b.x(), bottom, b.z(), b.x(), top, b.z(), c);
        }
    }

    private boolean inOverworld() {
        return mc.world != null && mc.world.getRegistryKey() == World.OVERWORLD;
    }

    // ------------------------------------------------------------------ Scan

    private void scan(Chunk chunk) {
        removeChunk(chunk.getPos().x, chunk.getPos().z);

        ChunkSection[] sections = chunk.getSectionArray();
        int baseX = chunk.getPos().getStartX();
        int baseZ = chunk.getPos().getStartZ();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(s -> classify(s.getBlock()) != 0)) continue;

            int baseY = chunk.getBottomY() + i * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        add(baseX + x, baseY + y, baseZ + z, state.getBlock());
                    }
                }
            }
        }
    }

    /** -1 = Spieler-Block, 0 = egal, >0 = Struktur-Marker (Gewicht). */
    private int classify(Block block) {
        return cache.computeIfAbsent(block, b -> {
            String path = Registries.BLOCK.getId(b).getPath();

            Integer w = STRUCTURE_EXACT.get(path);
            if (w != null) return w;
            for (String part : STRUCTURE_CONTAINS) if (path.contains(part)) return 1;
            for (String prefix : STRUCTURE_PREFIX) if (path.startsWith(prefix)) return 2;

            if (PLAYER_EXACT.contains(path)) return -1;
            for (String suffix : PLAYER_SUFFIX) if (path.endsWith(suffix)) return -1;
            return 0;
        });
    }

    private static long cellKey(int x, int z) {
        return (((long) (x >> CELL_SHIFT)) << 32) | ((z >> CELL_SHIFT) & 0xFFFFFFFFL);
    }

    private static long cellKey(int cx, int cz, boolean raw) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    private void add(int x, int y, int z, Block block) {
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
        int minBlocks = 55 - sensitivity.get(); // 5 -> 50 Blöcke, 50 -> 5 Blöcke

        Set<Long> visited = new HashSet<>();
        List<Beam> result = new ArrayList<>();

        for (Map.Entry<Long, Cell> entry : cells.entrySet()) {
            long start = entry.getKey();
            if (entry.getValue().player.isEmpty() || visited.contains(start)) continue;

            // Cluster = zusammenhängende Zellen mit Spieler-Blöcken
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
                        long nk = cellKey(cx + dx, cz + dz, true);
                        Cell n = cells.get(nk);
                        if (n != null && !n.player.isEmpty() && visited.add(nk)) todo.add(nk);
                    }
                }
            }

            int count = 0;
            double sx = 0, sz = 0;
            int maxY = Integer.MIN_VALUE;
            Set<Long> near = new HashSet<>();

            for (long k : comp) {
                Cell cell = cells.get(k);
                count += cell.player.size();
                for (long p : cell.player) {
                    sx += BlockPos.unpackLongX(p);
                    sz += BlockPos.unpackLongZ(p);
                    maxY = Math.max(maxY, BlockPos.unpackLongY(p));
                }
                int cx = (int) (k >> 32);
                int cz = (int) k;
                for (int dx = -STRUCTURE_CELL_RANGE; dx <= STRUCTURE_CELL_RANGE; dx++)
                    for (int dz = -STRUCTURE_CELL_RANGE; dz <= STRUCTURE_CELL_RANGE; dz++)
                        near.add(cellKey(cx + dx, cz + dz, true));
            }

            if (count < minBlocks) continue;

            int structureWeight = 0;
            for (long nk : near) {
                Cell c = cells.get(nk);
                if (c == null) continue;
                for (int w : c.structure.values()) structureWeight += w;
            }
            if (structureWeight >= STRUCTURE_LIMIT) continue; // Village, Trial Chamber, Dungeon ...

            result.add(new Beam(sx / count + 0.5, sz / count + 0.5, maxY));
        }

        beams = result;
        showToasts(result);
    }

    private void showToasts(List<Beam> found) {
        SettingColor c = toastColor.get();
        for (Beam b : found) {
            long key = (((long) ((int) b.x() >> 4)) << 32) | (((int) b.z() >> 4) & 0xFFFFFFFFL);
            if (!toasted.add(key)) continue;
            String coords = (int) b.x() + "  Y " + b.y() + "  Z " + (int) b.z();
            mc.getToastManager().add(new BoffoToast("Boffo Debug V1", coords, c.r, c.g, c.b));
        }
    }
}
