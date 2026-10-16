package dev.frosthaven.havenconsolidated;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What is left of the old ids, written to {@code <game dir>/haven-consolidated-audit.json}: whether each old id
 * resolves to ours, recipes whose result is still an old item, recipes that still look like duplicates of each other,
 * and, in the chunks around players and spawn, old and new blocks in the ground and old and new items in containers
 * and inventories. {@code /consolidated audit} (ops) or, for the headless test, {@code -Dhaven_consolidated.audit=true}
 * runs it {@code haven_consolidated.audit.ticks} (default 600) ticks after the server started;
 * {@code -Dhaven_consolidated.audit.quit=true} then quits the game.
 */
public final class Audit {
    public static final String FILE = "haven-consolidated-audit.json";
    private static final int RADIUS = 8;   // chunks around each player and the spawn
    private static int countdown = -1;
    private static volatile boolean quit;

    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("consolidated").requires(s -> s.hasPermission(2))
                .then(Commands.literal("audit").executes(ctx -> {
                    Path out = run(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Haven Consolidated audit written to " + out), true);
                    return 1;
                })));
    }

    static void onServerStarted(ServerStartedEvent event) {
        if (Boolean.getBoolean("haven_consolidated.audit")) {
            countdown = Integer.getInteger("haven_consolidated.audit.ticks", 600);
            HavenConsolidated.LOG.info("Haven Consolidated: audit in {} ticks", countdown);
        }
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (countdown < 0) {
            return;
        }
        if (countdown-- == 0) {
            run(event.getServer());
            if (Boolean.getBoolean("haven_consolidated.audit.quit")) {
                quit = true;
                if (!FMLEnvironment.dist.isClient()) {
                    event.getServer().halt(false);
                }
            }
        }
    }

    /** Set once the automatic audit asked for the game to quit; the client subscriber acts on it. */
    public static boolean quitRequested() {
        return quit;
    }

    static Path run(MinecraftServer server) {
        JsonObject out = new JsonObject();
        out.add("aliases", aliases());
        out.add("recipes", recipes(server));
        out.add("world", world(server));
        Path path = FMLPaths.GAMEDIR.get().resolve(FILE);
        try {
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(out), StandardCharsets.UTF_8);
        } catch (IOException e) {
            HavenConsolidated.LOG.error("Haven Consolidated: cannot write {}", path, e);
        }
        HavenConsolidated.LOG.info("Haven Consolidated audit: {}", summary(out));
        return path;
    }

    private static JsonObject aliases() {
        JsonObject o = new JsonObject();
        int ok = 0;
        JsonArray failed = new JsonArray();
        for (Map.Entry<ResourceLocation, ResourceLocation> e : Aliases.items().entrySet()) {
            ResourceLocation got = BuiltInRegistries.ITEM.getHolder(e.getKey()).map(h -> h.key().location()).orElse(null);
            if (e.getValue().equals(got)) {
                ok++;
            } else {
                failed.add(e.getKey() + " -> " + got);
            }
        }
        for (Map.Entry<ResourceLocation, ResourceLocation> e : Aliases.blocks().entrySet()) {
            ResourceLocation got = BuiltInRegistries.BLOCK.getHolder(e.getKey()).map(h -> h.key().location()).orElse(null);
            if (e.getValue().equals(got)) {
                ok++;
            } else {
                failed.add(e.getKey() + " -> " + got);
            }
        }
        for (Map.Entry<ResourceLocation, ResourceLocation> e : Aliases.fluids().entrySet()) {
            ResourceLocation got = BuiltInRegistries.FLUID.getHolder(e.getKey()).map(h -> h.key().location()).orElse(null);
            if (e.getValue().equals(got)) {
                ok++;
            } else {
                failed.add(e.getKey() + " -> " + got);
            }
        }
        o.addProperty("items", Aliases.items().size());
        o.addProperty("blocks", Aliases.blocks().size());
        o.addProperty("fluids", Aliases.fluids().size());
        o.addProperty("resolve_ok", ok);
        o.add("resolve_failed", failed);
        o.add("absent_mods_ids", strings(Aliases.absent()));
        return o;
    }

    private static JsonObject recipes(MinecraftServer server) {
        JsonObject o = new JsonObject();
        JsonArray oldResults = new JsonArray();
        Map<String, Integer> ourOutputs = new TreeMap<>();
        Map<String, List<String>> groups = new TreeMap<>();
        int total = 0;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            total++;
            ItemStack result;
            try {
                result = holder.value().getResultItem(server.registryAccess());
            } catch (RuntimeException e) {
                continue;   // a recipe type that cannot answer without a container
            }
            if (result == null || result.isEmpty()) {
                continue;
            }
            Item item = result.getItem();
            if (Aliased.targetOf(item) != null) {
                oldResults.add(holder.id().toString());
            }
            if (Content.ITEM_BY_ID.containsKey(BuiltInRegistries.ITEM.getKey(item))) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                ourOutputs.merge(id, 1, Integer::sum);
                String key = holder.value().getType() + " " + id + " x" + result.getCount() + " <- " + ingredients(holder);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(holder.id().toString());
            }
        }
        JsonObject dupes = new JsonObject();
        groups.forEach((k, ids) -> {
            if (ids.size() > 1) {
                dupes.add(k, strings(ids));
            }
        });
        o.addProperty("total", total);
        o.add("old_item_results", oldResults);
        o.add("our_outputs", map(ourOutputs));
        o.add("lookalike_groups", dupes);
        return o;
    }

    private static String ingredients(RecipeHolder<?> holder) {
        List<String> parts = new ArrayList<>();
        for (Ingredient ing : holder.value().getIngredients()) {
            Set<String> items = new HashSet<>();
            for (ItemStack s : ing.getItems()) {
                items.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            }
            List<String> sorted = new ArrayList<>(items);
            sorted.sort(null);
            parts.add(String.join("/", sorted));
        }
        parts.sort(null);
        return String.join(" + ", parts);
    }

    private static JsonObject world(MinecraftServer server) {
        JsonObject o = new JsonObject();
        Map<String, Integer> oldBlocks = new TreeMap<>(), ourBlocks = new TreeMap<>();
        Map<String, Integer> oldItems = new TreeMap<>(), ourItems = new TreeMap<>();
        Map<String, Integer> oldFluids = new TreeMap<>(), ourFluids = new TreeMap<>();   // in mB, tanks in loaded chunks
        Set<ResourceLocation> oldBlockIds = Aliases.blocks().keySet();
        int chunks = 0, unsaved = 0;
        for (ServerLevel level : server.getAllLevels()) {
            Set<ChunkPos> centers = new HashSet<>();
            if (level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                centers.add(new ChunkPos(level.getSharedSpawnPos()));
            }
            for (ServerPlayer p : level.players()) {
                centers.add(p.chunkPosition());
                count(p.getInventory().items, oldItems, ourItems);
                count(p.getInventory().armor, oldItems, ourItems);
                count(p.getInventory().offhand, oldItems, ourItems);
            }
            Set<ChunkPos> seen = new HashSet<>();
            for (ChunkPos c : centers) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                        ChunkPos pos = new ChunkPos(c.x + dx, c.z + dz);
                        if (!seen.add(pos)) {
                            continue;
                        }
                        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                        if (chunk == null) {
                            continue;
                        }
                        chunks++;
                        if (chunk.isUnsaved()) {
                            unsaved++;
                        }
                        for (LevelChunkSection section : chunk.getSections()) {
                            if (section.hasOnlyAir()) {
                                continue;
                            }
                            section.getStates().count((state, n) -> {
                                Block b = state.getBlock();
                                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
                                if (oldBlockIds.contains(id)) {
                                    oldBlocks.merge(id.toString(), n, Integer::sum);
                                } else if (Content.BLOCK_BY_ID.containsKey(id)) {
                                    ourBlocks.merge(id.toString(), n, Integer::sum);
                                }
                            });
                        }
                        for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, e.getKey(), e.getValue().getBlockState(), e.getValue(), null);
                            if (handler != null) {
                                for (int i = 0; i < handler.getSlots(); i++) {
                                    count(List.of(handler.getStackInSlot(i)), oldItems, ourItems);
                                }
                            }
                            IFluidHandler tank = level.getCapability(Capabilities.FluidHandler.BLOCK, e.getKey(), e.getValue().getBlockState(), e.getValue(), null);
                            if (tank != null) {
                                for (int i = 0; i < tank.getTanks(); i++) {
                                    FluidStack f = tank.getFluidInTank(i);
                                    if (f.isEmpty()) {
                                        continue;
                                    }
                                    ResourceLocation id = BuiltInRegistries.FLUID.getKey(f.getFluid());
                                    if (AliasedFluid.targetOf(f.getFluid()) != null) {
                                        oldFluids.merge(id.toString(), f.getAmount(), Integer::sum);
                                    } else if (Content.FLUID_BY_ID.containsKey(id)) {
                                        ourFluids.merge(id.toString(), f.getAmount(), Integer::sum);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        o.addProperty("chunks", chunks);
        o.addProperty("chunks_unsaved", unsaved);
        o.addProperty("chunks_marked_since_start", AliasHits.markedChunks());
        o.addProperty("chunks_rewritten_since_start", AliasHits.rewrittenChunks());
        o.add("old_blocks", map(oldBlocks));
        o.add("our_blocks", map(ourBlocks));
        o.add("old_items", map(oldItems));
        o.add("our_items", map(ourItems));
        o.add("old_fluids_mb", map(oldFluids));
        o.add("our_fluids_mb", map(ourFluids));
        return o;
    }

    private static void count(Iterable<ItemStack> stacks, Map<String, Integer> old, Map<String, Integer> ours) {
        for (ItemStack s : stacks) {
            if (s.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
            if (Aliased.targetOf(s.getItem()) != null) {
                old.merge(id.toString(), s.getCount(), Integer::sum);
            } else if (Content.ITEM_BY_ID.containsKey(id)) {
                ours.merge(id.toString(), s.getCount(), Integer::sum);
            }
        }
    }

    private static String summary(JsonObject out) {
        JsonObject a = out.getAsJsonObject("aliases"), r = out.getAsJsonObject("recipes"), w = out.getAsJsonObject("world");
        return "aliases " + a.get("resolve_ok") + " ok / " + a.getAsJsonArray("resolve_failed").size() + " failed; recipes "
                + r.get("total") + " total, " + r.getAsJsonArray("old_item_results").size() + " with old results, "
                + r.getAsJsonObject("lookalike_groups").size() + " lookalike groups; world " + w.get("chunks") + " chunks ("
                + w.get("chunks_unsaved") + " unsaved, " + w.get("chunks_marked_since_start") + " marked), old blocks "
                + w.getAsJsonObject("old_blocks") + ", our blocks " + w.getAsJsonObject("our_blocks") + ", old items "
                + w.getAsJsonObject("old_items") + ", our items " + w.getAsJsonObject("our_items") + ", old fluids "
                + w.getAsJsonObject("old_fluids_mb") + ", our fluids " + w.getAsJsonObject("our_fluids_mb");
    }

    private static JsonArray strings(Iterable<?> values) {
        JsonArray a = new JsonArray();
        values.forEach(v -> a.add(v.toString()));
        return a;
    }

    private static JsonObject map(Map<String, Integer> m) {
        JsonObject o = new JsonObject();
        m.forEach(o::addProperty);
        return o;
    }

    private Audit() {}
}
