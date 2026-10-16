package dev.frosthaven.havenconsolidated;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * After the recipes are parsed (so every id is already ours): among the recipes whose result is one of our items,
 * those with the same type, result and ingredients as an earlier one are dropped. Three mods' "raw lead smelts to a
 * lead ingot" become one, whether a mod wrote the input as its own item or as the tag. Ours come first and win, then
 * ids in order. Only recipes that produce our items are touched (vanilla's special recipes have no result).
 */
public final class RecipeDedupe {
    public record Result(Set<ResourceLocation> dropped, List<String> lines) {}

    public static Result duplicates(Iterable<RecipeHolder<?>> recipes, HolderLookup.Provider registries) {
        List<RecipeHolder<?>> ours = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes) {   // not filtered by isSpecial(): Create's processing recipes say so
            ItemStack result;
            try {
                result = holder.value().getResultItem(registries);
            } catch (RuntimeException e) {
                continue;
            }
            if (result != null && !result.isEmpty() && Content.ITEM_BY_ID.containsKey(BuiltInRegistries.ITEM.getKey(result.getItem()))) {
                ours.add(holder);
            }
        }
        String own = HavenConsolidated.ID + ":";
        ours.sort(Comparator.comparing((RecipeHolder<?> h) -> !h.id().toString().startsWith(own)).thenComparing(h -> h.id().toString()));
        Map<String, ResourceLocation> seen = new HashMap<>();
        Set<ResourceLocation> dropped = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        for (RecipeHolder<?> holder : ours) {
            String key = key(holder, registries);
            ResourceLocation first = seen.putIfAbsent(key, holder.id());
            if (first != null) {
                dropped.add(holder.id());
                lines.add(holder.id() + " = " + first);
            }
        }
        return new Result(dropped, lines);
    }

    /** Type, result (item, count, components) and the ingredients as sorted sets of item ids. */
    static String key(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        ItemStack result = holder.value().getResultItem(registries);
        StringBuilder sb = new StringBuilder();
        sb.append(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType())).append('|')
          .append(BuiltInRegistries.ITEM.getKey(result.getItem())).append('x').append(result.getCount())
          .append(result.getComponentsPatch().isEmpty() ? "" : result.getComponentsPatch().toString()).append('|');
        List<String> parts = new ArrayList<>();
        for (Ingredient ingredient : holder.value().getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            Set<String> items = new TreeSet<>();
            for (ItemStack s : ingredient.getItems()) {
                items.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            }
            parts.add(String.join("/", items));
        }
        parts.sort(null);
        return sb.append(String.join("+", parts)).toString();
    }

    private RecipeDedupe() {}
}
