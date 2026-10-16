package dev.frosthaven.havenconsolidated.mixin;

import com.google.gson.JsonElement;
import dev.frosthaven.havenconsolidated.Aliases;
import dev.frosthaven.havenconsolidated.HavenConsolidated;
import dev.frosthaven.havenconsolidated.RecipeJson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Rewrites and dedupes the recipe JSON before the game parses it (see {@link RecipeJson}). */
@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"))
    private void haven$consolidate(Map<ResourceLocation, JsonElement> recipes, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        if (!Aliases.active()) {
            return;
        }
        RecipeJson.Stats stats = RecipeJson.consolidate(recipes, ResourceLocation::toString, Aliases.rewrite(), HavenConsolidated.ID + ":");
        HavenConsolidated.LOG.info("Haven Consolidated: {} recipe id reference(s) rewritten, {} duplicate recipe(s) removed", stats.rewritten(), stats.removed().size());
        for (String line : stats.removed()) {
            HavenConsolidated.LOG.debug("  removed {}", line);
        }
    }
}
