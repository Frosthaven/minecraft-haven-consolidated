package dev.frosthaven.havenconsolidated.mixin;

import dev.frosthaven.havenconsolidated.Aliased;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A stack of an old item cannot exist: the constructor every path ends in swaps the item for ours (a mod building
 * {@code new ItemStack(ITS_LEAD_INGOT)} in code gets our ingot), and {@code is(oldItem)} is true for our item, so code
 * that asks "is this my lead ingot?" accepts ours.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Shadow
    public abstract Item getItem();

    @Redirect(method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ItemLike;asItem()Lnet/minecraft/world/item/Item;"))
    private Item haven$consolidate(ItemLike like) {
        Item item = like.asItem();
        Item target = ((Aliased) item).haven$target();
        return target != null ? target : item;
    }

    @Inject(method = "is(Lnet/minecraft/world/item/Item;)Z", at = @At("HEAD"), cancellable = true)
    private void haven$isAliased(Item item, CallbackInfoReturnable<Boolean> cir) {
        Item target = ((Aliased) item).haven$target();
        if (target != null && target == getItem()) {
            cir.setReturnValue(true);
        }
    }
}
