package dev.frosthaven.havenconsolidated;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * One item per metal. For every form in consolidated/*.json (ingot, nugget, raw, dust, sheet, ore, block...) the mod
 * registers its own item or block and, once every mod has registered, aliases the other mods' ids to it:
 * <ul>
 * <li>{@link Aliases}: the item and block registries answer every lookup of an old id with ours (stored stacks,
 *     recipes, loot, tags, worldgen, chunk palettes all load by id), and a stack built in code from an old item
 *     becomes ours as it is constructed ({@code ItemStackMixin});</li>
 * <li>{@link RecipeJson}: recipe JSON names the old ids literally as well, so they are rewritten before parsing and the
 *     copies that become identical are dropped;</li>
 * <li>{@link Audit}: {@code /consolidated audit}, or {@code -Dhaven_consolidated.audit=true} at server start, writes
 *     what is left of the old ids (nothing, when it works) to {@code haven-consolidated-audit.json}.</li>
 * </ul>
 */
@Mod(HavenConsolidated.ID)
public final class HavenConsolidated {
    public static final String ID = "haven_consolidated";
    public static final Logger LOG = LogUtils.getLogger();

    public HavenConsolidated(IEventBus modBus, ModContainer container) {
        Consolidated.load();
        Content.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(Aliases::activate));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, Audit::register);
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, Audit::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, Audit::onServerTick);
    }
}
