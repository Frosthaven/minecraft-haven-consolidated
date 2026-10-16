package dev.frosthaven.havenconsolidated;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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

/** The metals the mod owns: data/haven_consolidated/consolidated/*.json, written by tools/gen.py from consolidated/. */
public final class Consolidated {
    /** One registered item or block and the ids of other mods it replaces. */
    public record Entry(String form, Kind kind, ResourceLocation id, List<ResourceLocation> replaces) {}

    public record Metal(String id, String name, List<Entry> entries) {}

    public enum Kind { ITEM, ORE, DEEPSLATE_ORE, BLOCK, RAW_BLOCK;
        boolean isBlock() { return this != ITEM; }
    }

    private static final Gson GSON = new Gson();
    private static List<Metal> metals = List.of();

    public static List<Metal> metals() {
        return metals;
    }

    static void load() {
        JsonObject index = read("index.json");
        List<Metal> out = new ArrayList<>();
        for (JsonElement name : index.getAsJsonArray("metals")) {
            JsonObject json = read(name.getAsString() + ".json");
            List<Entry> entries = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("entries")) {
                JsonObject o = e.getAsJsonObject();
                List<ResourceLocation> replaces = new ArrayList<>();
                JsonArray ids = o.getAsJsonArray("replaces");
                for (JsonElement id : ids) {
                    replaces.add(ResourceLocation.parse(id.getAsString()));
                }
                entries.add(new Entry(o.get("form").getAsString(),
                        Kind.valueOf(o.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT)),
                        ResourceLocation.parse(o.get("id").getAsString()), Collections.unmodifiableList(replaces)));
            }
            out.add(new Metal(json.get("metal").getAsString(), json.get("name").getAsString(),
                    Collections.unmodifiableList(entries)));
        }
        metals = Collections.unmodifiableList(out);
        HavenConsolidated.LOG.info("Haven Consolidated owns {} metal(s): {}", metals.size(),
                metals.stream().map(Metal::id).toList());
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
