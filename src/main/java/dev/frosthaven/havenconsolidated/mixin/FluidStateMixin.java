package dev.frosthaven.havenconsolidated.mixin;

import dev.frosthaven.havenconsolidated.AliasedFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A fluid in the world is ours; code asking "is this my oil?" with the old fluid gets yes. */
@Mixin(FluidState.class)
abstract class FluidStateMixin {
    @Shadow
    public abstract Fluid getType();

    @Inject(method = "is(Lnet/minecraft/world/level/material/Fluid;)Z", at = @At("HEAD"), cancellable = true)
    private void haven$isAliased(Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        Fluid target = AliasedFluid.targetOf(fluid);
        if (target != null && target == getType()) {
            cir.setReturnValue(true);
        }
    }
}
