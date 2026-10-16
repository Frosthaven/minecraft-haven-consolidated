package dev.frosthaven.havenconsolidated.mixin;

import dev.frosthaven.havenconsolidated.AliasRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.BaseMappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Every lookup by name in a {@code MappedRegistry} ({@code get}, {@code getHolder}, by location or key) goes through
 * {@code resolve}. NeoForge's resolve keeps a name that is registered; ours is consulted first, so an old id that
 * another mod still registers resolves to Haven Consolidated's entry. Registries without aliases pay one null check.
 */
@Mixin(value = BaseMappedRegistry.class, remap = false)
abstract class BaseMappedRegistryMixin<T> implements AliasRegistry {
    @Unique
    private Map<ResourceLocation, ResourceLocation> haven$forced;

    @Override
    public void haven$alias(ResourceLocation from, ResourceLocation to) {
        if (haven$forced == null) {
            haven$forced = new HashMap<>();
        }
        haven$forced.put(from, to);
    }

    @Inject(method = "resolve(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private void haven$resolve(ResourceLocation name, CallbackInfoReturnable<ResourceLocation> cir) {
        if (haven$forced != null) {
            ResourceLocation to = haven$forced.get(name);
            if (to != null) {
                cir.setReturnValue(to);
            }
        }
    }
}
