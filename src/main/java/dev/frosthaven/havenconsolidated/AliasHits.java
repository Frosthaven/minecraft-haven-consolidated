package dev.frosthaven.havenconsolidated;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chunks and the alias. Two cases:
 * <ul>
 * <li>a chunk read from disk whose palette or containers named an old id was converted by the registry alias as it
 *     loaded; {@link ChunkDataEvent.Load} (posted at the end of each read on the server thread) finds the per-thread
 *     flag set and marks the chunk unsaved, so the conversion reaches the region file and the map;</li>
 * <li>a block placed by code rather than by id (TFMG's oil wells and deposits set their fluid block object in a new
 *     chunk) is still the old block; {@link ChunkEvent.Load} checks each section's palette (cheap) and rewrites the
 *     old states to ours, keeping properties of the same name (a fluid's level).</li>
 * </ul>
 */
public final class AliasHits {
    private static final ThreadLocal<boolean[]> HIT = ThreadLocal.withInitial(() -> new boolean[1]);
    private static final AtomicInteger MARKED = new AtomicInteger(), REWRITTEN = new AtomicInteger();
    private static Map<Block, Block> oldBlocks;

    public static void mark() {
        HIT.get()[0] = true;
    }

    static void onChunkRead(ChunkDataEvent.Load event) {
        boolean[] hit = HIT.get();
        if (hit[0]) {
            event.getChunk().setUnsaved(true);
            MARKED.incrementAndGet();
            hit[0] = false;
        }
    }

    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk) || !Aliases.active()) {
            return;
        }
        Map<Block, Block> map = oldBlocks();
        if (map.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir() || !section.maybeHas(state -> map.containsKey(state.getBlock()))) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block ours = map.get(state.getBlock());
                        if (ours != null) {
                            section.setBlockState(x, y, z, convert(state, ours), false);
                            changed = true;
                        }
                    }
                }
            }
        }
        if (changed) {
            chunk.setUnsaved(true);
            REWRITTEN.incrementAndGet();
        }
    }

    /** Our block's default state with every property of the old state that ours has under the same name. */
    private static BlockState convert(BlockState old, Block ours) {
        BlockState state = ours.defaultBlockState();
        for (Property<?> p : old.getProperties()) {
            Property<?> q = ours.getStateDefinition().getProperty(p.getName());
            if (q != null) {
                state = with(state, q, old.getValue(p));
            }
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState with(BlockState state, Property q, Comparable<?> value) {
        return q.getPossibleValues().contains(value) ? state.setValue(q, (Comparable) value) : state;
    }

    private static Map<Block, Block> oldBlocks() {
        if (oldBlocks == null) {
            Map<Block, Block> m = new HashMap<>();
            for (Map.Entry<ResourceLocation, ResourceLocation> e : Aliases.blocks().entrySet()) {
                m.put(BuiltInRegistries.BLOCK.get(e.getKey()), Content.BLOCK_BY_ID.get(e.getValue()).get());
            }
            oldBlocks = m;
        }
        return oldBlocks;
    }

    /** Chunks marked unsaved after a converting read, and chunks whose code-placed old blocks were rewritten (audit). */
    public static int markedChunks() {
        return MARKED.get();
    }

    public static int rewrittenChunks() {
        return REWRITTEN.get();
    }

    private AliasHits() {}
}
