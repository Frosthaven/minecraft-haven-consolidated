package dev.frosthaven.havenconsolidated;

import net.minecraft.resources.ResourceLocation;

/**
 * Implemented on NeoForge's {@code BaseMappedRegistry} by {@code BaseMappedRegistryMixin}. NeoForge's own
 * {@code addAlias} only answers for ids that are not registered; these aliases win even when the old id still is
 * (the other mod keeps registering it), so every lookup by the old id, from stored stacks to tags and chunk palettes,
 * gets Haven Consolidated's entry.
 */
public interface AliasRegistry {
    void haven$alias(ResourceLocation from, ResourceLocation to);

    /** Enter (+1) or leave (-1) a lookup that must see the registry as it is: {@code getId(name)}, which the registry
     *  sync uses to build the id table sent to clients. */
    void haven$raw(int delta);
}
