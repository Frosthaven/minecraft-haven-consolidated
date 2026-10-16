package dev.frosthaven.havenconsolidated.mixin;

import dev.frosthaven.havenconsolidated.Aliased;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** An old item carries the item that replaces it (null for every other item). */
@Mixin(Item.class)
abstract class ItemMixin implements Aliased {
    @Unique
    private @Nullable Item haven$target;

    @Override
    public @Nullable Item haven$target() {
        return haven$target;
    }

    @Override
    public void haven$setTarget(Item target) {
        haven$target = target;
    }
}
