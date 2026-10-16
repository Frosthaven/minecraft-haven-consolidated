package dev.frosthaven.havenconsolidated;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The alias table, built once every mod has registered (common setup): old id -> our id, for the items, blocks and
 * fluids of mods that are present. Ids of mods that are not loaded are skipped and listed, so one data file serves
 * every pack. For a fluid, the old still fluid is named in the data; its flowing fluid (same namespace,
 * {@code flowing_} prefix), its block in the world (the LiquidBlock holding it) and its bucket (the BucketItem holding
 * it) are found in the registries.
 */
public final class Aliases {
    private static final Map<ResourceLocation, ResourceLocation> ITEMS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> BLOCKS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> FLUIDS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Fluid> FLUID_ART = new HashMap<>();   // our fluid id -> first old fluid present
    private static final List<ResourceLocation> ABSENT = new ArrayList<>();
    private static boolean active;

    static void activate() {
        for (Consolidated.Metal metal : Consolidated.metals()) {
            for (Consolidated.Entry entry : metal.entries()) {
                Item ours = Content.item(entry.id());
                boolean block = entry.kind().isBlock();
                for (ResourceLocation old : entry.replaces()) {
                    boolean found = aliasItem(old, entry.id(), ours);
                    if (block && BuiltInRegistries.BLOCK.containsKey(old)) {
                        aliasBlock(old, entry.id());
                        found = true;
                    }
                    if (!found) {
                        ABSENT.add(old);
                    }
                }
            }
        }
        Map<Fluid, List<Block>> liquidBlocks = new HashMap<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            if (b instanceof LiquidBlock lb) {
                liquidBlocks.computeIfAbsent(lb.fluid, k -> new ArrayList<>()).add(b);
            }
        }
        Map<Fluid, List<Item>> buckets = new HashMap<>();
        for (Item i : BuiltInRegistries.ITEM) {
            if (i instanceof BucketItem bucket) {
                buckets.computeIfAbsent(bucket.content, k -> new ArrayList<>()).add(i);
            }
        }
        for (Consolidated.FluidEntry f : Consolidated.fluids()) {
            Fluid still = Content.fluid(f.fluid()), flowing = Content.fluid(f.flowing());
            Item bucket = Content.item(f.bucket());
            for (ResourceLocation old : f.replaces()) {
                if (!BuiltInRegistries.FLUID.containsKey(old)) {
                    ABSENT.add(old);
                    continue;
                }
                Fluid oldStill = BuiltInRegistries.FLUID.get(old);
                aliasFluid(old, f.fluid(), oldStill, still);
                FLUID_ART.putIfAbsent(f.fluid(), oldStill);
                ResourceLocation oldFlowing = ResourceLocation.fromNamespaceAndPath(old.getNamespace(), "flowing_" + old.getPath());
                if (BuiltInRegistries.FLUID.containsKey(oldFlowing)) {
                    aliasFluid(oldFlowing, f.flowing(), BuiltInRegistries.FLUID.get(oldFlowing), flowing);
                }
                for (Block b : liquidBlocks.getOrDefault(oldStill, List.of())) {
                    aliasBlock(BuiltInRegistries.BLOCK.getKey(b), f.fluid());
                }
                for (Item i : buckets.getOrDefault(oldStill, List.of())) {
                    aliasItem(BuiltInRegistries.ITEM.getKey(i), f.bucket(), bucket);
                }
            }
        }
        active = true;
        HavenConsolidated.LOG.info("Haven Consolidated: {} item id(s), {} block id(s) and {} fluid id(s) now resolve to ours; {} id(s) belong to mods that are not loaded{}",
                ITEMS.size(), BLOCKS.size(), FLUIDS.size(), ABSENT.size(), ABSENT.isEmpty() ? "" : ": " + ABSENT);
    }

    private static boolean aliasItem(ResourceLocation old, ResourceLocation ours, Item oursItem) {
        if (!BuiltInRegistries.ITEM.containsKey(old)) {
            return false;
        }
        ((Aliased) BuiltInRegistries.ITEM.get(old)).haven$setTarget(oursItem);
        ((AliasRegistry) BuiltInRegistries.ITEM).haven$alias(old, ours);
        ITEMS.put(old, ours);
        return true;
    }

    private static void aliasBlock(ResourceLocation old, ResourceLocation ours) {
        ((AliasRegistry) BuiltInRegistries.BLOCK).haven$alias(old, ours);
        BLOCKS.put(old, ours);
    }

    private static void aliasFluid(ResourceLocation old, ResourceLocation ours, Fluid oldFluid, Fluid oursFluid) {
        ((AliasedFluid) oldFluid).haven$setTarget(oursFluid);
        ((AliasRegistry) BuiltInRegistries.FLUID).haven$alias(old, ours);
        FLUIDS.put(old, ours);
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

    /** Old fluid id -> ours (still and flowing). */
    public static Map<ResourceLocation, ResourceLocation> fluids() {
        return Collections.unmodifiableMap(FLUIDS);
    }

    /** The first old fluid present for one of ours: its look is borrowed (client). */
    public static Fluid fluidArt(ResourceLocation ours) {
        return FLUID_ART.get(ours);
    }

    /** Ids from the data files whose mod is not loaded. */
    public static List<ResourceLocation> absent() {
        return Collections.unmodifiableList(ABSENT);
    }

    private Aliases() {}
}
