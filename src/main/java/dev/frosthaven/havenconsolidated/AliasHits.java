package dev.frosthaven.havenconsolidated;

import net.neoforged.neoforge.event.level.ChunkDataEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Whether an alias answered a lookup on this thread since the last reset. Chunks are read on the server thread, one
 * after the other, and {@link ChunkDataEvent.Load} is posted at the end of each read: a chunk whose read hit an alias
 * (an old ore in a palette, an old item in a chest) is marked unsaved, so the conversion reaches the region file and
 * the map instead of being redone on every load. A hit between two reads marks the next chunk: one spare save.
 */
public final class AliasHits {
    private static final ThreadLocal<boolean[]> HIT = ThreadLocal.withInitial(() -> new boolean[1]);
    private static final AtomicInteger MARKED = new AtomicInteger();

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

    /** Chunks marked unsaved since startup (audit). */
    public static int markedChunks() {
        return MARKED.get();
    }

    private AliasHits() {}
}
