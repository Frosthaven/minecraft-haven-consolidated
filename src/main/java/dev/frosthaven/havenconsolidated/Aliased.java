package dev.frosthaven.havenconsolidated;

import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/** Implemented on {@link Item} by {@code ItemMixin}: an old item knows the Haven Consolidated item that replaces it. */
public interface Aliased {
    @Nullable Item haven$target();

    void haven$setTarget(Item target);

    static @Nullable Item targetOf(Item item) {
        return ((Aliased) item).haven$target();
    }
}
