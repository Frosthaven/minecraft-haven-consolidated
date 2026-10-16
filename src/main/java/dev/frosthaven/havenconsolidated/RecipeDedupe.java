package dev.frosthaven.havenconsolidated;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * After the recipes are parsed (so every id is already ours): among the recipes whose result is one of our items or
 * fluids, those with the same type, results and ingredients as an earlier one are dropped. Three mods' "raw lead
 * smelts to a lead ingot" become one, whether a mod wrote the input as its own item or as the tag. Ours come first
 * and win, then ids in order. Fluid results and fluid ingredients are read through the {@code getFluidResults()} /
 * {@code getFluidIngredients()} methods Create's processing recipes have (by reflection, so Create is no build
 * dependency); recipes without them count items only.
 */
public final class RecipeDedupe {
    public record Result(Set<ResourceLocation> dropped, List<String> lines) {}

    private static final Map<Class<?>, Method[]> FLUID_METHODS = new HashMap<>();

    public static Result duplicates(Iterable<RecipeHolder<?>> recipes, HolderLookup.Provider registries) {
        List<RecipeHolder<?>> ours = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes) {   // not filtered by isSpecial(): Create's processing recipes say so
            if (producesOurs(holder.value(), registries)) {
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

    private static boolean producesOurs(Recipe<?> recipe, HolderLookup.Provider registries) {
        ItemStack result = resultItem(recipe, registries);
        if (result != null && !result.isEmpty() && Content.ITEM_BY_ID.containsKey(BuiltInRegistries.ITEM.getKey(result.getItem()))) {
            return true;
        }
        for (FluidStack f : fluidResults(recipe)) {
            if (Content.FLUID_BY_ID.containsKey(BuiltInRegistries.FLUID.getKey(f.getFluid()))) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack resultItem(Recipe<?> recipe, HolderLookup.Provider registries) {
        try {
            return recipe.getResultItem(registries);
        } catch (RuntimeException e) {
            return null;   // a recipe type that cannot answer without a container
        }
    }

    /** Type, item and fluid results (id, count, components) and the ingredients as sorted sets of ids. */
    static String key(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        Recipe<?> recipe = holder.value();
        StringBuilder sb = new StringBuilder();
        sb.append(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())).append('|');
        ItemStack result = resultItem(recipe, registries);
        if (result != null && !result.isEmpty()) {
            sb.append(BuiltInRegistries.ITEM.getKey(result.getItem())).append('x').append(result.getCount())
              .append(result.getComponentsPatch().isEmpty() ? "" : result.getComponentsPatch().toString());
        }
        for (FluidStack f : fluidResults(recipe)) {
            sb.append('~').append(BuiltInRegistries.FLUID.getKey(f.getFluid())).append('x').append(f.getAmount());
        }
        sb.append('|');
        List<String> parts = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            Set<String> items = new TreeSet<>();
            for (ItemStack s : ingredient.getItems()) {
                items.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            }
            parts.add(String.join("/", items));
        }
        for (List<FluidStack> option : fluidIngredients(recipe)) {
            Set<String> fluids = new TreeSet<>();
            for (FluidStack s : option) {
                fluids.add(BuiltInRegistries.FLUID.getKey(s.getFluid()) + "x" + s.getAmount());
            }
            parts.add("~" + String.join("/", fluids));
        }
        parts.sort(null);
        return sb.append(String.join("+", parts)).toString();
    }

    @SuppressWarnings("unchecked")
    private static List<FluidStack> fluidResults(Recipe<?> recipe) {
        Method m = methods(recipe.getClass())[0];
        if (m == null) {
            return List.of();
        }
        try {
            Object out = m.invoke(recipe);
            return out instanceof List<?> list && (list.isEmpty() || list.get(0) instanceof FluidStack) ? (List<FluidStack>) list : List.of();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    /** Each fluid ingredient as the stacks it accepts (Create's FluidIngredient.getMatchingFluidStacks()). */
    private static List<List<FluidStack>> fluidIngredients(Recipe<?> recipe) {
        Method m = methods(recipe.getClass())[1];
        if (m == null) {
            return List.of();
        }
        try {
            Object out = m.invoke(recipe);
            if (!(out instanceof List<?> list)) {
                return List.of();
            }
            List<List<FluidStack>> options = new ArrayList<>();
            for (Object ingredient : list) {
                Method matching = ingredient.getClass().getMethod("getMatchingFluidStacks");
                Object stacks = matching.invoke(ingredient);
                if (stacks instanceof List<?> l) {
                    List<FluidStack> option = new ArrayList<>();
                    for (Object s : l) {
                        if (s instanceof FluidStack fs) {
                            option.add(fs);
                        }
                    }
                    options.add(option);
                }
            }
            return options;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    private static Method[] methods(Class<?> type) {
        return FLUID_METHODS.computeIfAbsent(type, t -> new Method[] {find(t, "getFluidResults"), find(t, "getFluidIngredients")});
    }

    private static Method find(Class<?> type, String name) {
        try {
            Method m = type.getMethod(name);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException | RuntimeException e) {
            return null;
        }
    }

    private RecipeDedupe() {}
}
