package dev.frosthaven.havenconsolidated.mixin;

import dev.frosthaven.havenconsolidated.Content;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mod's creative tab lists its lead ore and its deepslate lead ore, or three mods list their ingots into the
 * search tab: all of those are now one of our stacks, and NeoForge throws on a stack a tab already has. For our
 * items the check is skipped; the set add that follows ignores the duplicate, so the tab shows ours once.
 */
@Mixin(value = BuildCreativeModeTabContentsEvent.class, remap = false)
abstract class CreativeTabEventMixin {
    @Inject(method = "assertNewEntryDoesNotAlreadyExists", at = @At("HEAD"), cancellable = true)
    private void haven$allowOurDuplicates(InsertableLinkedOpenCustomHashSet<ItemStack> entries, ItemStack newEntry, CallbackInfo ci) {
        if (Content.ITEM_BY_ID.containsKey(BuiltInRegistries.ITEM.getKey(newEntry.getItem()))) {
            ci.cancel();
        }
    }
}
