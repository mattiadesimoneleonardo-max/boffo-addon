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
 * Boffo Debug: sucht unter Y = -5 nach Bloecken, die nur Spieler setzen koennen.
 * Ein Fundort zaehlt erst, wenn dort mindestens "min-blocks" solcher Bloecke zusammenliegen
 * und keine Struktur (Ancient City, Trial Chamber, Stronghold, Spawner, ...) in der Naehe ist.
 */
public class BoffoDebugModule extends Module {
    private static final int MAX_Y = -6;               // "unter -5"
    private static final int MERGE_DIST_SQ = 16 * 16;   // Bloecke naeher als 16 Bloecke gehoeren zum selben Fundort
    private static final int CHUNKS_PER_TICK = 3;
    private static final int CONFIRM_TICKS = 40;        // Fund muss 2 Sekunden stabil sein
    private static final int STRUCTURE_CHUNK_RADIUS = 2; // Struktur-Bloecke im Umkreis von 2 Chunks (ca. 32 Bloecke) schliessen den Fund aus

    // Bloecke, die es unter der Erde nirgends natuerlich gibt
    private static final Set<String> PLAYER_EXACT = Set.of(
        "furnace", "blast_furnace", "smoker", "ender_chest", "anvil", "chipped_anvil", "damaged_anvil",
        "enchanting_table", "brewing_stand", "cauldron", "beacon", "hopper", "dropper", "observer",
        "piston", "sticky_piston", "repeater", "comparator", "redstone_lamp", "redstone_wire", "note_block",
        "jukebox", "tnt", "crafting_table", "grindstone", "stonecutter", "loom", "cartography_table",
        "fletching_table", "smithing_table", "composter", "tripwire_hook", "target", "slime_block", "honey_block",
        "glowstone", "daylight_detector", "iron_block", "diamond_block", "emerald_block", "lapis_block",
        "redstone_block", "netherite_block", "coal_block", "bricks", "quartz_block", "quartz_bricks",
        "quartz_pillar", "chiseled_quartz_block", "smooth_quartz", "end_stone_bricks", "purpur_block", "purpur_pillar"
    );
    private static final String[] PLAYER_SUFFIX = {
        "_concrete", "_concrete_powder", "_glazed_terracotta", "_bed", "shulker_box", "_stained_glass"
    };

    // Bloecke, die auf eine Struktur hindeuten (Ancient City, Trial Chamber, Stronghold, Dungeon, ...)
    private static final Set<String> STRUCTURE_EXACT = Set.of(
        "sculk", "sculk_sensor", "calibrated_sculk_sensor", "sculk_shrieker", "sculk_catalyst", "sculk_vein",
        "reinforced_deepslate", "spawner", "trial_spawner", "vault", "crafter", "heavy_core",
        "end_portal_frame", "end_portal", "ominous_trial_spawner"
    );
    private static final String[] STRUCTURE_CONTAINS = {"copper_bulb", "copper_grate", "chiseled_tuff", "tuff_brick"};

    private static final byte NONE = 0, PLAYER = 1, STRUCTURE = 2;

    private static class Spot {
        final int x, y, z;                          // erster Fund (hier steht der Strahl)
        final Set<Long> blocks = new HashSet<>();    // alle Spieler-Bloecke an diesem Fundort
        boolean nearStructure = false;
        boolean confirmed = false;
        int validTicks = 0;

        Spot(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> minBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("min-blocks").description("Mindestanzahl Spieler-Bloecke an einer Stelle, damit sie angezeigt wird.")
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
    private final Set<Long> structureCells = new HashSet<>(); // Chunk-Spalten, in denen Struktur-Bloecke gesehen wurden
    private final Map<Block, Byte> cache = new HashMap<>();
    private final BlockPos.Mutable pos = new BlockPos.Mutable();
    private int toastCooldown = 0;

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
            int dx = spot.x - x, dz = spot.z - z;
            if (dx * dx + dz * dz <= MERGE_DIST_SQ) {
                spot.blocks.add(key);
                return;
            }
        }
        Spot spot = new Spot(x, y, z);
        spot.blocks.add(key);
        spots.add(spot);
    }

    private void removeFind(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        for (Spot spot : spots) spot.blocks.remove(key);
        spots.removeIf(spot -> spot.blocks.isEmpty());
    }

    /** Prueft jeden Fundort: genug Bloecke, keine Struktur in der Naehe, 2 Sekunden stabil. */
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
        int cx = spot.x >> 4, cz = spot.z >> 4;
        for (int dx = -STRUCTURE_CHUNK_RADIUS; dx <= STRUCTURE_CHUNK_RADIUS; dx++) {
            for (int dz = -STRUCTURE_CHUNK_RADIUS; dz <= STRUCTURE_CHUNK_RADIUS; dz++) {
                if (structureCells.contains(cell(cx + dx, cz + dz))) return true;
            }
        }
        return false;
    }

    private static long cell(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Eine Benachrichtigung pro neuem Fundort, hoechstens eine alle 0,5 Sekunden. */
    private void showToasts() {
        if (toastCooldown > 0) toastCooldown--;
        if (toastCooldown > 0) return;

        Spot spot;
        do {
            spot = pendingToasts.poll();
        } while (spot != null && !spot.confirmed);
        if (spot == null) return;

        SettingColor c = toastColor.get();
        String coords = "X " + spot.x + "  Y " + spot.y + "  Z " + spot.z;
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
        Color side = new Color(c.r, c.g, c.b, 70);
        Color line = new Color(Math.min(255, c.r + 40), Math.min(255, c.g + 40), Math.min(255, c.b + 40), 255);
        Color core = new Color((c.r + 255 * 2) / 3, (c.g + 255 * 2) / 3, (c.b + 255 * 2) / 3, 200);

        for (Spot spot : spots) {
            if (!spot.confirmed) continue;
            double cx = spot.x + 0.5, cz = spot.z + 0.5;
            // breiter Strahl durch die ganze Welt
            event.renderer.box(cx - 0.3, -64, cz - 0.3, cx + 0.3, 256, cz + 0.3, side, line, ShapeMode.Both, 0);
            // heller Kern
            event.renderer.box(cx - 0.08, -64, cz - 0.08, cx + 0.08, 256, cz + 0.08, core, core, ShapeMode.Sides, 0);
            // Markierung am ersten Fund
            event.renderer.box(spot.x, spot.y, spot.z, spot.x + 1, spot.y + 1, spot.z + 1, side, line, ShapeMode.Both, 0);
        }
    }
}

