package dev.frosthaven.havenconsolidated.client;

import dev.frosthaven.havenconsolidated.Audit;
import dev.frosthaven.havenconsolidated.Content;
import dev.frosthaven.havenconsolidated.HavenConsolidated;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Quits the game once the automatic audit asked for it (headless test: {@code -Dhaven_consolidated.audit.quit=true}):
 * first the pause menu's "Save and Quit" (the integrated server saves every unsaved chunk and stops; a plain
 * {@code Minecraft.stop()} exits without that save), then the game.
 */
@EventBusSubscriber(modid = HavenConsolidated.ID, value = Dist.CLIENT)
public final class ClientAudit {
    private static boolean leaving, stopping;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!Audit.quitRequested()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!leaving) {
            leaving = true;
            if (mc.level != null) {
                creativeTabs(mc);
                HavenConsolidated.LOG.info("Haven Consolidated: audit done, saving and leaving the world");
                mc.level.disconnect();
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null) {
                    server.halt(false);   // the server's own "owner logged out" halt does not fire for an offline user
                }
                mc.disconnect(new GenericMessageScreen(Component.literal("Haven Consolidated: audit done, saving")));
            }
            return;
        }
        if (!stopping && mc.level == null && mc.getSingleplayerServer() == null) {
            stopping = true;
            HavenConsolidated.LOG.info("Haven Consolidated: world saved, quitting");
            mc.stop();
        }
    }

    /** What opening the creative inventory does: every tab is built. A duplicate of ours in a tab used to throw here. */
    private static void creativeTabs(Minecraft mc) {
        try {
            CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess());
            int ours = 0;
            for (ItemStack stack : CreativeModeTabs.searchTab().getSearchTabDisplayItems()) {
                if (Content.ITEM_BY_ID.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                    ours++;
                }
            }
            HavenConsolidated.LOG.info("Haven Consolidated: creative tabs ok, {} of our items in the search tab", ours);
        } catch (Throwable t) {
            HavenConsolidated.LOG.error("Haven Consolidated: creative tabs failed", t);
        }
    }

    private ClientAudit() {}
}
