package dev.frosthaven.havenconsolidated.mixin;

import dev.frosthaven.havenconsolidated.calm.Calm;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A pufferfish never targets anyone; it stings on touch while puffed. A player it ignores ({@link Calm}) is not stung. */
@Mixin(Pufferfish.class)
abstract class PufferfishStingMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void haven$noSting(Player player, CallbackInfo ci) {
        if (Calm.ignores((Pufferfish) (Object) this, player)) ci.cancel();
    }
}
