package dev.frosthaven.havenconsolidated;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The alias table, built once every mod has registered (common setup): old id -> our id, for the items and blocks of
 * mods that are present. Ids of mods that are not loaded are skipped and listed, so one data file serves every pack.
 */
public final class Aliases {
    private static final Map<ResourceLocation, ResourceLocation> ITEMS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, String> REWRITE = new LinkedHashMap<>();   // both, as strings, for recipe JSON
    private static final List<ResourceLocation> ABSENT = new ArrayList<>();
    private static boolean active;

    static void activate() {
        for (Consolidated.Metal metal : Consolidated.metals()) {
            for (Consolidated.Entry entry : metal.entries()) {
                Item ours = Content.item(entry.id());
                Block ourBlock = entry.kind().isBlock() ? Content.BLOCK_BY_ID.get(entry.id()).get() : null;
                for (ResourceLocation old : entry.replaces()) {
                    boolean found = false;
                    if (BuiltInRegistries.ITEM.containsKey(old)) {
                        Item oldItem = BuiltInRegistries.ITEM.get(old);
                        ((Aliased) oldItem).haven$setTarget(ours);
                        ((AliasRegistry) BuiltInRegistries.ITEM).haven$alias(old, entry.id());
                        ITEMS.put(old, entry.id());
                        found = true;
                    }
                    if (ourBlock != null && BuiltInRegistries.BLOCK.containsKey(old)) {
                        ((AliasRegistry) BuiltInRegistries.BLOCK).haven$alias(old, entry.id());
                        BLOCKS.put(old, entry.id());
                        found = true;
                    }
                    if (found) {
                        REWRITE.put(old.toString(), entry.id().toString());
                    } else {
                        ABSENT.add(old);
                    }
                }
            }
        }
        active = true;
        HavenConsolidated.LOG.info("Haven Consolidated: {} item id(s) and {} block id(s) now resolve to ours; {} id(s) belong to mods that are not loaded{}",
                ITEMS.size(), BLOCKS.size(), ABSENT.size(), ABSENT.isEmpty() ? "" : ": " + ABSENT);
    }

    public static boolean active() {
        return active;
    }

    /** Old item id -> ours. */
    public static Map<ResourceLocation, ResourceLocation> items() {
        return Collections.unmodifiableMap(ITEMS);
    }

    /** Old block id -> ours. */
    public static Map<ResourceLocation, ResourceLocation> blocks() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    /** Old id -> ours, as strings, items and blocks together (recipe JSON). */
    public static Map<String, String> rewrite() {
        return Collections.unmodifiableMap(REWRITE);
    }

    /** Ids from the data files whose mod is not loaded. */
    public static List<ResourceLocation> absent() {
        return Collections.unmodifiableList(ABSENT);
    }

    private Aliases() {}
}
