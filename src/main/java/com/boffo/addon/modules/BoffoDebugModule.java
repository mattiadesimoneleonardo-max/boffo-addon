package com.boffo.addon.modules;

import com.boffo.addon.BoffoAddon;
import com.boffo.addon.BoffoToast;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
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
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Boffo Debug: sucht unter Y = -5 nach Bereichen, die Spieler gebaut haben (Stashes, Farmen, Basen).
 * Ein Bereich zaehlt erst, wenn dort mindestens "min-blocks" typische Spieler-Bloecke zusammenliegen
 * und keine Struktur (Ancient City, Trial Chamber, Stronghold, Dungeon, Endportal, ...) in der Naehe ist.
 * Markiert wird der ganze Bereich als Saeule von Y -64 bis 256.
 */
public class BoffoDebugModule extends Module {
    private static final int MAX_Y = -6;                 // "unter -5"
    private static final int MERGE_DIST = 12;            // Funde naeher als 12 Bloecke gehoeren zum selben Bereich
    private static final int CHUNKS_PER_TICK = 3;
    private static final int CONFIRM_TICKS = 40;         // Bereich muss 2 Sekunden stabil sein
    private static final int STRUCTURE_RANGE = 40;       // Struktur-Bloecke im Umkreis von 40 Bloecken schliessen den Bereich aus

    // Bloecke, die Spieler bauen (auch Kisten: mehrere Kisten zusammen = Stash)
    private static final Set<String> PLAYER_EXACT = Set.of(
        "chest", "trapped_chest", "barrel", "ender_chest", "furnace", "blast_furnace", "smoker",
        "anvil", "chipped_anvil", "damaged_anvil", "enchanting_table", "brewing_stand", "cauldron", "beacon",
        "hopper", "dropper", "dispenser", "observer", "piston", "sticky_piston", "repeater", "comparator",
        "redstone_lamp", "redstone_wire", "redstone_torch", "note_block", "jukebox", "tnt", "crafting_table",
        "grindstone", "stonecutter", "loom", "cartography_table", "fletching_table", "smithing_table", "composter",
        "tripwire_hook", "target", "slime_block", "honey_block", "glowstone", "daylight_detector", "scaffolding",
        "iron_block", "diamond_block", "emerald_block", "lapis_block", "redstone_block", "netherite_block", "coal_block",
        "bricks", "quartz_block", "quartz_bricks", "quartz_pillar", "chiseled_quartz_block", "smooth_quartz",
        "end_stone_bricks", "purpur_block", "purpur_pillar",
        "farmland", "wheat", "carrots", "potatoes", "beetroots", "sugar_cane", "melon_stem", "pumpkin_stem",
        "nether_wart", "cactus", "bamboo"
    );
    private static final String[] PLAYER_SUFFIX = {
        "_concrete", "_concrete_powder", "_glazed_terracotta", "_bed", "shulker_box", "_stained_glass", "_pressure_plate"
    };

    // Bloecke, die auf eine Struktur hindeuten (Ancient City, Trial Chamber, Stronghold, Dungeon, Endportal, Dorf, ...)
    private static final Set<String> STRUCTURE_EXACT = Set.of(
        "sculk", "sculk_sensor", "calibrated_sculk_sensor", "sculk_shrieker", "sculk_catalyst", "sculk_vein",
        "reinforced_deepslate", "spawner", "trial_spawner", "vault", "crafter", "heavy_core",
        "end_portal_frame", "end_portal", "end_gateway", "bell", "crying_obsidian", "suspicious_sand", "suspicious_gravel"
    );
    private static final String[] STRUCTURE_CONTAINS = {
        "copper_bulb", "copper_grate", "chiseled_tuff", "tuff_brick", "infested_", "decorated_pot"
    };

    private static final byte NONE = 0, PLAYER = 1, STRUCTURE = 2;

    /** Ein Bereich mit Spieler-Bloecken. */
    private static class Spot {
        final Set<Long> blocks = new HashSet<>();
        int minX, maxX, minY, maxY, minZ, maxZ;
        boolean nearStructure = false;
        boolean confirmed = false;
        int validTicks = 0;

        Spot(int x, int y, int z) {
            minX = maxX = x;
            minY = maxY = y;
            minZ = maxZ = z;
        }

        void add(long key, int x, int y, int z) {
            if (blocks.isEmpty()) {
                minX = maxX = x; minY = maxY = y; minZ = maxZ = z;
            } else {
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
            blocks.add(key);
        }

        boolean near(int x, int z) {
            return x >= minX - MERGE_DIST && x <= maxX + MERGE_DIST && z >= minZ - MERGE_DIST && z <= maxZ + MERGE_DIST;
        }

        boolean near(Spot o) {
            return o.maxX >= minX - MERGE_DIST && o.minX <= maxX + MERGE_DIST
                && o.maxZ >= minZ - MERGE_DIST && o.minZ <= maxZ + MERGE_DIST;
        }

        void recalc() {
            boolean first = true;
            for (long key : blocks) {
                int x = BlockPos.unpackLongX(key), y = BlockPos.unpackLongY(key), z = BlockPos.unpackLongZ(key);
                if (first) { minX = maxX = x; minY = maxY = y; minZ = maxZ = z; first = false; continue; }
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
        }

        int centerX() { return (minX + maxX) / 2; }
        int centerY() { return (minY + maxY) / 2; }
        int centerZ() { return (minZ + maxZ) / 2; }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> minBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("min-blocks").description("Mindestanzahl Spieler-Bloecke in einem Bereich, damit er markiert wird.")
        .defaultValue(5).range(5, 50).sliderRange(5, 50).build());
    private final Setting<SettingColor> beamColor = sgGeneral.add(new ColorSetting.Builder()
        .name("beam-color").description("Farbe des Strahls.")
        .defaultValue(new SettingColor(255, 138, 31)).build());
    private final Setting<SettingColor> toastColor = sgGeneral.add(new ColorSetting.Builder()
        .name("toast-color").description("Farbe der Benachrichtigung oben rechts.")
        .defaultValue(new SettingColor(255, 138, 31)).build());

    private final Queue<Chunk> queue = new ConcurrentLinkedQueue<>();
    private final List<Spot> spots = new ArrayList<>();
    private final Queue<Spot> pendingToasts = new ArrayDeque<>();
    private final Set<Long> structureCells = new HashSet<>(); // Chunk-Spalten mit Struktur-Bloecken
    private final Map<Block, Byte> cache = new HashMap<>();
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private int toastCooldown = 0;
    private int mergeTimer = 0;

    public BoffoDebugModule() {
        super(BoffoAddon.CATEGORY, "boffo-debug", "Boffo Debug");
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

    private void reset() {
        queue.clear();
        spots.clear();
        pendingToasts.clear();
        structureCells.clear();
        toastCooldown = 0;
        mergeTimer = 0;
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        queue.add(event.chunk()); // kann von einem anderen Thread kommen
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!inOverworld() || event.pos.getY() > MAX_Y) return;
        int x = event.pos.getX(), y = event.pos.getY(), z = event.pos.getZ();

        byte now = classify(event.newState.getBlock());
        if (classify(event.oldState.getBlock()) == PLAYER && now != PLAYER) removeFind(x, y, z);
        if (now == PLAYER) addFind(x, y, z);
        else if (now == STRUCTURE) structureCells.add(cell(x >> 4, z >> 4));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!inOverworld()) {
            queue.clear();
            return;
        }

        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            Chunk chunk = queue.poll();
            if (chunk == null) break;
            scan(chunk);
        }

        if (++mergeTimer >= 20) {
            mergeTimer = 0;
            mergeSpots();
        }

        updateSpots();
        showToasts();
    }

    private boolean inOverworld() {
        return mc.world != null && mc.player != null && mc.world.getRegistryKey() == World.OVERWORLD;
    }

    private void scan(Chunk chunk) {
        int bx = chunk.getPos().x * 16, bz = chunk.getPos().z * 16;
        int minY = Math.max(chunk.getBottomY(), -64);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= MAX_Y; y++) {
                    pos.set(bx + x, y, bz + z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) continue;
                    byte type = classify(state.getBlock());
                    if (type == PLAYER) addFind(pos.getX(), pos.getY(), pos.getZ());
                    else if (type == STRUCTURE) structureCells.add(cell(pos.getX() >> 4, pos.getZ() >> 4));
                }
            }
        }
    }

    private void addFind(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        for (Spot spot : spots) {
            if (spot.near(x, z)) {
                spot.add(key, x, y, z);
                return;
            }
        }
        Spot spot = new Spot(x, y, z);
        spot.add(key, x, y, z);
        spots.add(spot);
    }

    private void removeFind(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        for (Spot spot : spots) {
            if (spot.blocks.remove(key) && !spot.blocks.isEmpty()) spot.recalc();
        }
        spots.removeIf(spot -> spot.blocks.isEmpty());
    }

    /** Bereiche, die zusammenwachsen, werden zu einem vereint. */
    private void mergeSpots() {
        for (int i = 0; i < spots.size(); i++) {
            Spot a = spots.get(i);
            for (int j = spots.size() - 1; j > i; j--) {
                Spot b = spots.get(j);
                if (a.near(b)) {
                    for (long key : b.blocks) {
                        a.add(key, BlockPos.unpackLongX(key), BlockPos.unpackLongY(key), BlockPos.unpackLongZ(key));
                    }
                    spots.remove(j);
                }
            }
        }
    }

    /** Prueft jeden Bereich: genug Bloecke, keine Struktur in der Naehe, 2 Sekunden stabil. */
    private void updateSpots() {
        boolean scanning = !queue.isEmpty();
        int min = minBlocks.get();

        for (Spot spot : spots) {
            spot.nearStructure = nearStructure(spot);
            boolean valid = spot.blocks.size() >= min && !spot.nearStructure;

            if (!valid) {
                spot.validTicks = 0;
                spot.confirmed = false;
            } else if (!spot.confirmed && !scanning) {
                if (++spot.validTicks >= CONFIRM_TICKS) {
                    spot.confirmed = true;
                    pendingToasts.add(spot);
                }
            }
        }
    }

    private boolean nearStructure(Spot spot) {
        if (structureCells.isEmpty()) return false;
        int x0 = (spot.minX - STRUCTURE_RANGE) >> 4, x1 = (spot.maxX + STRUCTURE_RANGE) >> 4;
        int z0 = (spot.minZ - STRUCTURE_RANGE) >> 4, z1 = (spot.maxZ + STRUCTURE_RANGE) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (structureCells.contains(cell(cx, cz))) return true;
            }
        }
        return false;
    }

    private static long cell(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Eine Benachrichtigung pro neuem Bereich (Koordinaten = Mitte), hoechstens eine alle 0,5 Sekunden. */
    private void showToasts() {
        if (toastCooldown > 0) toastCooldown--;
        if (toastCooldown > 0) return;

        Spot spot;
        do {
            spot = pendingToasts.poll();
        } while (spot != null && !spot.confirmed);
        if (spot == null) return;

        SettingColor c = toastColor.get();
        String coords = "X " + spot.centerX() + "  Y " + spot.centerY() + "  Z " + spot.centerZ();
        mc.getToastManager().add(new BoffoToast("Boffo Debug V1", coords, c.r, c.g, c.b));
        toastCooldown = 10;
    }

    private byte classify(Block block) {
        return cache.computeIfAbsent(block, b -> {
            String path = Registries.BLOCK.getId(b).getPath();
            if (STRUCTURE_EXACT.contains(path)) return STRUCTURE;
            for (String part : STRUCTURE_CONTAINS) if (path.contains(part)) return STRUCTURE;
            if (PLAYER_EXACT.contains(path)) return PLAYER;
            for (String suffix : PLAYER_SUFFIX) if (path.endsWith(suffix)) return PLAYER;
            return NONE;
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (spots.isEmpty() || !inOverworld()) return;

        SettingColor c = beamColor.get();
        Color side = new Color(c.r, c.g, c.b, 45);
        Color sideStrong = new Color(c.r, c.g, c.b, 90);
        Color line = new Color(Math.min(255, c.r + 40), Math.min(255, c.g + 40), Math.min(255, c.b + 40), 255);
        Color core = new Color((c.r + 255 * 2) / 3, (c.g + 255 * 2) / 3, (c.b + 255 * 2) / 3, 210);

        for (Spot spot : spots) {
            if (!spot.confirmed) continue;

            double x1 = spot.minX - 1, x2 = spot.maxX + 2;
            double z1 = spot.minZ - 1, z2 = spot.maxZ + 2;

            // ganzer Bereich als Saeule durch die Welt
            event.renderer.box(x1, -64, z1, x2, 256, z2, side, line, ShapeMode.Both, 0);
            // heller Strahl in der Mitte des Bereichs
            double cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
            event.renderer.box(cx - 0.12, -64, cz - 0.12, cx + 0.12, 256, cz + 0.12, core, core, ShapeMode.Sides, 0);
            // Kasten um die tatsaechlichen Bloecke
            event.renderer.box(spot.minX, spot.minY, spot.minZ, spot.maxX + 1, spot.maxY + 1, spot.maxZ + 1,
                sideStrong, line, ShapeMode.Both, 0);
        }
    }
}

