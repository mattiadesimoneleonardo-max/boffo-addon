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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BoffoDebugModule extends Module {
    private static final int CHUNKS_PER_TICK = 3;
    private static final int MAX_SCAN_Y = -6; // nur tief unter der Erde suchen

    private static final Set<String> PLAYER_BLOCKS = Set.of(
        "furnace", "blast_furnace", "smoker", "ender_chest", "anvil", "chipped_anvil",
        "damaged_anvil", "enchanting_table", "brewing_stand", "cauldron", "beacon",
        "hopper", "dropper", "observer", "piston", "sticky_piston", "repeater",
        "comparator", "redstone_lamp", "redstone_wire", "note_block", "jukebox", "tnt",
        "crafting_table", "grindstone", "stonecutter", "loom", "cartography_table",
        "fletching_table", "smithing_table", "composter", "tripwire_hook", "target",
        "slime_block", "honey_block", "glowstone", "daylight_detector", "glass",
        "iron_block", "diamond_block", "emerald_block", "lapis_block", "redstone_block",
        "netherite_block", "coal_block", "bricks", "quartz_block", "quartz_bricks",
        "quartz_pillar", "chiseled_quartz_block", "smooth_quartz", "end_stone_bricks",
        "purpur_block", "purpur_pillar"
    );

    private static final String[] SUFFIX = {
        "_concrete", "_concrete_powder", "_glazed_terracotta", "_bed", "shulker_box", "_stained_glass"
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> beamColor = sgGeneral.add(new ColorSetting.Builder()
        .name("beam-color")
        .description("Farbe des Strahls.")
        .defaultValue(new SettingColor(255, 138, 31))
        .build());

    private final Setting<SettingColor> toastColor = sgGeneral.add(new ColorSetting.Builder()
        .name("toast-color")
        .description("Farbe der Benachrichtigung oben rechts.")
        .defaultValue(new SettingColor(255, 138, 31))
        .build());

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .description("Ab wie vielen Spieler-Blöcken pro Base der Strahl erscheint.")
        .defaultValue(5)
        .min(1)
        .sliderMax(100)
        .build());

    private final Setting<Integer> mergeRadius = sgGeneral.add(new IntSetting.Builder()
        .name("merge-radius")
        .description("Funde in diesem Radius gehören zur selben Base (= ein Strahl).")
        .defaultValue(48)
        .min(16)
        .sliderMax(128)
        .build());

    private final Setting<Integer> beamOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("beam-opacity")
        .description("Deckkraft des Strahls (0 = unsichtbar, 255 = voll).")
        .defaultValue(35)
        .min(0)
        .max(255)
        .sliderMax(255)
        .build());

    private final Queue<Spot> pendingToasts = new ArrayDeque<>();
    private int toastCooldown = 0;

    private final Queue<Chunk> queue = new ConcurrentLinkedQueue<>();
    private final List<Spot> spots = new ArrayList<>();
    private final List<Spot> fresh = new ArrayList<>();
    private final Set<Long> counted = new HashSet<>();
    private final Map<Block, Boolean> cache = new HashMap<>();
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    public BoffoDebugModule() {
        super(BoffoAddon.CATEGORY, "boffo-debug", "Boffo Debug");
    }

    @Override
    public void onActivate() {
        reset();
        for (Chunk chunk : Utils.chunks()) {
            queue.add(chunk);
        }
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        queue.clear();
        spots.clear();
        fresh.clear();
        counted.clear();
        pendingToasts.clear();
        toastCooldown = 0;
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        queue.add(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!inOverworld()) return;
        if (event.pos.getY() > MAX_SCAN_Y) return;
        if (isPlayerOnly(event.newState.getBlock())) {
            addFind(event.pos.getX(), event.pos.getY(), event.pos.getZ());
        }
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

        flushToast();
    }

    private boolean inOverworld() {
        return mc.world != null && mc.player != null && mc.world.getRegistryKey() == World.OVERWORLD;
    }

    private void scan(Chunk chunk) {
        int bx = chunk.getPos().x * 16;
        int bz = chunk.getPos().z * 16;
        int minY = Math.max(chunk.getBottomY(), -64);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= MAX_SCAN_Y; y++) {
                    pos.set(bx + x, y, bz + z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (isPlayerOnly(state.getBlock())) {
                        addFind(pos.getX(), pos.getY(), pos.getZ());
                    }
                }
            }
        }
    }

    /**
     * Zählt einen Spieler-Block. Funde im merge-radius gehören zur selben Base.
     * Erst wenn die Anzahl die Sensitivity erreicht, wird die Base markiert (+ Toast).
     */
    private void addFind(int x, int y, int z) {
        // jeden Block nur einmal zählen (Chunks werden evtl. mehrfach gescannt)
        if (!counted.add(BlockPos.asLong(x, y, z))) return;

        long r = mergeRadius.get();
        for (Spot s : spots) {
            long dx = s.x - x;
            long dz = s.z - z;
            if (dx * dx + dz * dz <= r * r) {
                s.count++;
                if (s.count == sensitivity.get()) fresh.add(s);
                return;
            }
        }

        Spot spot = new Spot(x, y, z);
        spots.add(spot);
        if (sensitivity.get() <= 1) fresh.add(spot);
    }

    private void flushToast() {
        pendingToasts.addAll(fresh);
        fresh.clear();

        if (toastCooldown > 0) {
            toastCooldown--;
            return;
        }

        if (pendingToasts.isEmpty()) return;

        Spot spot = pendingToasts.poll();
        SettingColor c = toastColor.get();
        String coords = "X " + spot.x + "  Y " + spot.y + "  Z " + spot.z;
        mc.getToastManager().add(new BoffoToast("Boffo Debug V1", coords, c.r, c.g, c.b));
        toastCooldown = 10;
    }

    private boolean isPlayerOnly(Block block) {
        return cache.computeIfAbsent(block, BoffoDebugModule::checkPlayerOnly);
    }

    private static boolean checkPlayerOnly(Block block) {
        String path = Registries.BLOCK.getId(block).getPath();
        if (PLAYER_BLOCKS.contains(path)) return true;
        for (String suffix : SUFFIX) {
            if (path.endsWith(suffix)) return true;
        }
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (spots.isEmpty() || !inOverworld()) return;

        SettingColor c = beamColor.get();
        Color side = new Color(c.r, c.g, c.b, beamOpacity.get());
        int needed = sensitivity.get();

        for (Spot s : spots) {
            if (s.count < needed) continue;

            double cx = s.x + 0.5;
            double cz = s.z + 0.5;

            // genau EIN transparenter Strahl pro Base, ohne Outline
            event.renderer.box(
                cx - 0.3, -64, cz - 0.3,
                cx + 0.3, 256, cz + 0.3,
                side, side, ShapeMode.Sides, 0
            );
        }
    }

    private static class Spot {
        final int x, y, z;
        int count = 1;

        Spot(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
