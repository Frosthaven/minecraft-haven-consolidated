package dev.frosthaven.havenconsolidated.client;

import dev.frosthaven.havenconsolidated.Audit;
import dev.frosthaven.havenconsolidated.HavenConsolidated;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Quits the game once the automatic audit asked for it (headless test: {@code -Dhaven_consolidated.audit.quit=true}). */
@EventBusSubscriber(modid = HavenConsolidated.ID, value = Dist.CLIENT)
public final class ClientAudit {
    private static boolean stopping;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (Audit.quitRequested() && !stopping) {
            stopping = true;
            HavenConsolidated.LOG.info("Haven Consolidated: audit done, quitting");
            Minecraft.getInstance().stop();
        }
    }

    private ClientAudit() {}
}
