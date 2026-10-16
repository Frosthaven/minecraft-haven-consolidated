package dev.frosthaven.havenconsolidated;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Recipe JSON, before the game parses it: every string equal to an old id becomes ours, and recipes that then say the
 * same thing as an earlier one are dropped (else three mods' "nine nuggets make an ingot" all show, and Polymorph asks
 * which). Pure Java (no Minecraft types) so it is unit tested.
 *
 * <p>Sameness ignores the fields that do not change what a recipe does ("group", "category", "show_notification"),
 * the letters of a shaped pattern (the grid of ingredients is compared), the order of shapeless ingredients, and
 * {@code count: 1}. Recipes under {@code neoforge:conditions} compare with their conditions, so a copy kept for one
 * mod set is never thrown away in favor of one that may not load. Our own recipes come first and win.
 */
public final class RecipeJson {
    private static final Set<String> NOISE = Set.of("group", "category", "show_notification");

    public record Stats(int rewritten, List<String> removed) {}

    /**
     * @param recipes   id -> JSON, modified in place (strings rewritten, duplicates removed)
     * @param name      the id as text, e.g. {@code ResourceLocation::toString}
     * @param rewrite   old id -> our id
     * @param ownPrefix our namespace with the colon, e.g. {@code "haven_consolidated:"}; those recipes are kept first
     */
    public static <K> Stats consolidate(Map<K, JsonElement> recipes, Function<K, String> name, Map<String, String> rewrite, String ownPrefix) {
        if (rewrite.isEmpty()) {
            return new Stats(0, List.of());
        }
        int rewritten = 0;
        List<K> touched = new ArrayList<>();
        for (Map.Entry<K, JsonElement> e : recipes.entrySet()) {
            int n = rewriteStrings(e.getValue(), rewrite);
            if (n > 0 || name.apply(e.getKey()).startsWith(ownPrefix)) {
                touched.add(e.getKey());
            }
            rewritten += n;
        }
        touched.sort(Comparator.comparing((K k) -> !name.apply(k).startsWith(ownPrefix)).thenComparing(name));
        Map<String, K> seen = new HashMap<>();
        List<String> removed = new ArrayList<>();
        for (K key : touched) {
            String canonical = canonical(recipes.get(key));
            K first = seen.putIfAbsent(canonical, key);
            if (first != null) {
                recipes.remove(key);
                removed.add(name.apply(key) + " = " + name.apply(first));
            }
        }
        return new Stats(rewritten, removed);
    }

    /** Replaces string values (not keys) that equal an old id; returns how many. */
    static int rewriteStrings(JsonElement element, Map<String, String> rewrite) {
        int n = 0;
        if (element instanceof JsonObject o) {
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                JsonElement v = e.getValue();
                if (v instanceof JsonPrimitive p && p.isString()) {
                    String to = rewrite.get(p.getAsString());
                    if (to != null) {
                        e.setValue(new JsonPrimitive(to));
                        n++;
                    }
                } else {
                    n += rewriteStrings(v, rewrite);
                }
            }
        } else if (element instanceof JsonArray a) {
            for (int i = 0; i < a.size(); i++) {
                JsonElement v = a.get(i);
                if (v instanceof JsonPrimitive p && p.isString()) {
                    String to = rewrite.get(p.getAsString());
                    if (to != null) {
                        a.set(i, new JsonPrimitive(to));
                        n++;
                    }
                } else {
                    n += rewriteStrings(v, rewrite);
                }
            }
        }
        return n;
    }

    /** A text form that is equal for recipes that do the same thing. */
    static String canonical(JsonElement recipe) {
        if (!(recipe instanceof JsonObject o)) {
            return recipe.toString();
        }
        JsonObject copy = o.deepCopy();
        NOISE.forEach(copy::remove);
        String type = copy.has("type") ? copy.get("type").getAsString() : "";
        if (type.equals("minecraft:crafting_shaped") && copy.has("pattern") && copy.has("key")) {
            JsonArray grid = new JsonArray();
            JsonObject key = copy.getAsJsonObject("key");
            for (JsonElement row : copy.getAsJsonArray("pattern")) {
                JsonArray cells = new JsonArray();
                for (char c : row.getAsString().toCharArray()) {
                    cells.add(c == ' ' || !key.has(String.valueOf(c)) ? "" : normalize(key.get(String.valueOf(c))));
                }
                grid.add(cells);
            }
            copy.remove("pattern");
            copy.remove("key");
            copy.add("grid", grid);
        }
        if (type.equals("minecraft:crafting_shapeless") && copy.has("ingredients")) {
            List<String> parts = new ArrayList<>();
            for (JsonElement ing : copy.getAsJsonArray("ingredients")) {
                parts.add(normalize(ing));
            }
            parts.sort(null);
            copy.remove("ingredients");
            copy.addProperty("ingredients", String.join("|", parts));
        }
        return normalize(copy);
    }

    /** Sorted keys, "item" read as "id", count 1 dropped: the comparison form of any JSON value. */
    static String normalize(JsonElement element) {
        if (element instanceof JsonObject o) {
            List<String> keys = new ArrayList<>(o.keySet());
            keys.sort(null);
            StringBuilder sb = new StringBuilder("{");
            for (Iterator<String> it = keys.iterator(); it.hasNext(); ) {
                String k = it.next();
                JsonElement v = o.get(k);
                if (k.equals("count") && v instanceof JsonPrimitive p && p.isNumber() && p.getAsInt() == 1) {
                    continue;
                }
                sb.append(k.equals("item") && !o.has("id") ? "id" : k).append(':').append(normalize(v));
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            return sb.append('}').toString();
        }
        if (element instanceof JsonArray a) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(normalize(a.get(i)));
            }
            return sb.append(']').toString();
        }
        return element.toString();
    }

    private RecipeJson() {}
}
