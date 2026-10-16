package dev.frosthaven.havenconsolidated.mixin;

import dev.frosthaven.havenconsolidated.AliasedFluid;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An old fluid carries the fluid that replaces it, and {@code isSame(oldFluid)} is true for ours. */
@Mixin(Fluid.class)
abstract class FluidMixin implements AliasedFluid {
    @Unique
    private @Nullable Fluid haven$target;

    @Override
    public @Nullable Fluid haven$target() {
        return haven$target;
    }

    @Override
    public void haven$setTarget(Fluid target) {
        haven$target = target;
    }

    /** Either way round: {@code ours.isSame(old)} (Deep Seas asks a tank's fluid) and {@code old.isSame(ours)}. */
    @Inject(method = "isSame", at = @At("HEAD"), cancellable = true)
    private void haven$isSameAliased(Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        Fluid target = ((AliasedFluid) fluid).haven$target();
        if ((target != null && target == (Object) this) || (haven$target != null && haven$target == fluid)) {
            cir.setReturnValue(true);
        }
    }
}
