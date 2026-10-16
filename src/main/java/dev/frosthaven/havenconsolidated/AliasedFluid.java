package dev.frosthaven.havenconsolidated;

import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/** Implemented on {@link Fluid} by {@code FluidMixin}: an old fluid knows the Haven Consolidated fluid that replaces it. */
public interface AliasedFluid {
    @Nullable Fluid haven$target();

    void haven$setTarget(Fluid target);

    static @Nullable Fluid targetOf(Fluid fluid) {
        return ((AliasedFluid) fluid).haven$target();
    }
}
