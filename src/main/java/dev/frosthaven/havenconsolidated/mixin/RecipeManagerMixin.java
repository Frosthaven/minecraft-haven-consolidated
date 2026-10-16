package dev.frosthaven.havenconsolidated.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import dev.frosthaven.havenconsolidated.Aliases;
import dev.frosthaven.havenconsolidated.HavenConsolidated;
import dev.frosthaven.havenconsolidated.RecipeDedupe;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Once the recipes are parsed, drops the copies that make our items from the same inputs (see {@link RecipeDedupe}). */
@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {
    @Shadow @Final private HolderLookup.Provider registries;
    @Shadow private Multimap<RecipeType<?>, RecipeHolder<?>> byType;
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void haven$dedupe(Map<ResourceLocation, JsonElement> recipes, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        if (!Aliases.active()) {
            return;
        }
        RecipeDedupe.Result result = RecipeDedupe.duplicates(byName.values(), registries);
        if (result.dropped().isEmpty()) {
            HavenConsolidated.LOG.info("Haven Consolidated: no duplicate recipes for our items");
            return;
        }
        ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> types = ImmutableMultimap.builder();
        ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> names = ImmutableMap.builder();
        for (RecipeHolder<?> holder : byName.values()) {
            if (!result.dropped().contains(holder.id())) {
                types.put(holder.value().getType(), holder);
                names.put(holder.id(), holder);
            }
        }
        byType = types.build();
        byName = names.build();
        HavenConsolidated.LOG.info("Haven Consolidated: {} duplicate recipe(s) for our items removed", result.dropped().size());
        for (String line : result.lines()) {
            HavenConsolidated.LOG.debug("  removed {}", line);
        }
    }
}
