package dev.frosthaven.havenconsolidated.mixin;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge throws when a creative tab lists a stack it already has. With consolidation that is normal: a mod's tab
 * lists its lead ore and its deepslate lead ore (both ours now), the search tab gets three mods' ingots, Mekanism
 * lists a creative tank per fluid and four hydrogens are one. The check is a developer aid, so it is skipped for
 * every stack; the set add that follows drops the duplicate and the tab shows the first.
 */
@Mixin(value = BuildCreativeModeTabContentsEvent.class, remap = false)
abstract class CreativeTabEventMixin {
    @Inject(method = "assertNewEntryDoesNotAlreadyExists", at = @At("HEAD"), cancellable = true)
    private void haven$allowDuplicates(InsertableLinkedOpenCustomHashSet<ItemStack> entries, ItemStack newEntry, CallbackInfo ci) {
        ci.cancel();
    }
}
