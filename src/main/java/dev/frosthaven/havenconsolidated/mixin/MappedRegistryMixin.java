package dev.frosthaven.havenconsolidated.mixin;

import dev.frosthaven.havenconsolidated.AliasRegistry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code getId(name)} asks for the numeric id behind a name. The registry sync builds the server's id table from
 * it over every key, so it must see the registry as it is: the alias is off while it runs (see
 * {@code BaseMappedRegistryMixin}). MappedRegistry overrides both forms, so the hooks live here.
 */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryMixin<T> {
    @Inject(method = "getId(Lnet/minecraft/resources/ResourceLocation;)I", at = @At("HEAD"), remap = false)
    private void haven$rawByName(ResourceLocation name, CallbackInfoReturnable<Integer> cir) {
        ((AliasRegistry) this).haven$raw(1);
    }

    @Inject(method = "getId(Lnet/minecraft/resources/ResourceLocation;)I", at = @At("RETURN"), remap = false)
    private void haven$rawByNameDone(ResourceLocation name, CallbackInfoReturnable<Integer> cir) {
        ((AliasRegistry) this).haven$raw(-1);
    }

    @Inject(method = "getId(Lnet/minecraft/resources/ResourceKey;)I", at = @At("HEAD"), remap = false)
    private void haven$rawByKey(ResourceKey<T> key, CallbackInfoReturnable<Integer> cir) {
        ((AliasRegistry) this).haven$raw(1);
    }

    @Inject(method = "getId(Lnet/minecraft/resources/ResourceKey;)I", at = @At("RETURN"), remap = false)
    private void haven$rawByKeyDone(ResourceKey<T> key, CallbackInfoReturnable<Integer> cir) {
        ((AliasRegistry) this).haven$raw(-1);
    }
}
