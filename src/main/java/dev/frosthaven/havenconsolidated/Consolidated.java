package dev.frosthaven.havenconsolidated;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** What the mod owns: data/haven_consolidated/consolidated/*.json and fluids/*.json, written by tools/gen.py. */
public final class Consolidated {
    /** One registered item or block and the ids of other mods it replaces. */
    public record Entry(String form, Kind kind, ResourceLocation id, List<ResourceLocation> replaces) {}

    public record Metal(String id, String name, List<Entry> entries) {}

    /** One registered fluid (still, flowing, block, bucket) and the other mods' still fluids it replaces. */
    public record FluidEntry(String id, String name, FluidKind kind, ResourceLocation fluid, ResourceLocation flowing,
                             ResourceLocation bucket, List<ResourceLocation> replaces) {}

    public enum Kind { ITEM, ORE, DEEPSLATE_ORE, MOON_ORE, BASALT_ORE, BLOCK, RAW_BLOCK;
        boolean isBlock() { return this != ITEM; }
    }

    public enum FluidKind { LIQUID, OIL, GAS, MOLTEN }

    private static final Gson GSON = new Gson();
    private static List<Metal> metals = List.of();
    private static List<FluidEntry> fluids = List.of();

    public static List<Metal> metals() {
        return metals;
    }

    public static List<FluidEntry> fluids() {
        return fluids;
    }

    static void load() {
        JsonObject index = read("index.json");
        List<Metal> out = new ArrayList<>();
        for (JsonElement name : index.getAsJsonArray("metals")) {
            JsonObject json = read(name.getAsString() + ".json");
            List<Entry> entries = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("entries")) {
                JsonObject o = e.getAsJsonObject();
                entries.add(new Entry(o.get("form").getAsString(),
                        Kind.valueOf(o.get("kind").getAsString().toUpperCase(Locale.ROOT)),
                        ResourceLocation.parse(o.get("id").getAsString()), ids(o.getAsJsonArray("replaces"))));
            }
            out.add(new Metal(json.get("metal").getAsString(), json.get("name").getAsString(), Collections.unmodifiableList(entries)));
        }
        metals = Collections.unmodifiableList(out);
        List<FluidEntry> fl = new ArrayList<>();
        if (index.has("fluids")) {
            for (JsonElement name : index.getAsJsonArray("fluids")) {
                JsonObject o = read("fluids/" + name.getAsString() + ".json");
                fl.add(new FluidEntry(o.get("fluid").getAsString(), o.get("name").getAsString(),
                        FluidKind.valueOf(o.get("kind").getAsString().toUpperCase(Locale.ROOT)),
                        ResourceLocation.parse(o.get("id").getAsString()), ResourceLocation.parse(o.get("flowing").getAsString()),
                        ResourceLocation.parse(o.get("bucket").getAsString()), ids(o.getAsJsonArray("replaces"))));
            }
        }
        fluids = Collections.unmodifiableList(fl);
        HavenConsolidated.LOG.info("Haven Consolidated owns {} metal(s) and {} fluid(s): {} / {}", metals.size(), fluids.size(),
                metals.stream().map(Metal::id).toList(), fluids.stream().map(FluidEntry::id).toList());
    }

    private static List<ResourceLocation> ids(Iterable<JsonElement> array) {
        List<ResourceLocation> out = new ArrayList<>();
        for (JsonElement id : array) {
            out.add(ResourceLocation.parse(id.getAsString()));
        }
        return Collections.unmodifiableList(out);
    }

    private static JsonObject read(String file) {
        String path = "/data/" + HavenConsolidated.ID + "/consolidated/" + file;
        try (InputStream in = Consolidated.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path + " (run tools/gen.py)");
            }
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Consolidated() {}
}
