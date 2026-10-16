package dev.frosthaven.havenconsolidated.mixin;

import dev.frosthaven.havenconsolidated.AliasedFluid;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A stack of an old fluid cannot exist: the constructor every path ends in swaps the fluid for ours (a mod building
 * {@code new FluidStack(ITS_OIL, 1000)} in code gets ours, and JEI's fluid list, built from the registry, shows ours),
 * and {@code is(oldFluid)} is true for our fluid.
 */
@Mixin(value = FluidStack.class, remap = false)
abstract class FluidStackMixin {
    @Shadow @Final @Mutable
    private Fluid fluid;

    @Inject(method = "<init>(Lnet/minecraft/world/level/material/Fluid;ILnet/minecraft/core/component/PatchedDataComponentMap;)V", at = @At("RETURN"))
    private void haven$consolidate(Fluid f, int amount, PatchedDataComponentMap components, CallbackInfo ci) {
        if (fluid != null) {
            Fluid target = ((AliasedFluid) fluid).haven$target();
            if (target != null) {
                fluid = target;
            }
        }
    }

    @Inject(method = "is(Lnet/minecraft/world/level/material/Fluid;)Z", at = @At("HEAD"), cancellable = true)
    private void haven$isAliased(Fluid other, CallbackInfoReturnable<Boolean> cir) {
        Fluid target = ((AliasedFluid) other).haven$target();
        if (target != null && target == fluid) {
            cir.setReturnValue(true);
        }
    }
}
