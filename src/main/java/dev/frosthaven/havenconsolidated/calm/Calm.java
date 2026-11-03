package dev.frosthaven.havenconsolidated.calm;

import dev.frosthaven.havenconsolidated.HavenConsolidated;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Accessories that calm mobs: a mob in an item's entity tag ({@link CalmingItem}) does not take a player who wears that
 * item as a new target. The Shark Tooth Necklace calms Living Things' shark (not Create Submarine's Abyss sharks, on
 * purpose); the Pufferfish Spine Bracelet calms Creeper Overhaul's Ocean Creeper. Only a new target is refused
 * ({@link LivingChangeTargetEvent}): a mob that already hunts the player keeps hunting when the item goes on, a mob the
 * player hurt may strike back (its last attacker, which vanilla keeps for 100 ticks), and without the item the mob's
 * next target search finds the player again. The worn check is Curios' API by reflection (no build dependency); without
 * Curios the items cannot be worn and calm nothing.
 */
public final class Calm {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenConsolidated.ID);
    static final List<DeferredItem<CalmingItem>> ALL = List.of(
            ITEMS.register("shark_tooth_necklace", () -> new CalmingItem("shark_tooth_necklace")),
            ITEMS.register("pufferfish_spine_bracelet", () -> new CalmingItem("pufferfish_spine_bracelet")));
    private static MethodHandle inventory;   // CuriosApi.getCuriosInventory(LivingEntity) -> Optional<ICuriosItemHandler>
    private static MethodHandle isEquipped;  // ICuriosItemHandler.isEquipped(Item) -> boolean
    private static volatile boolean readFailed;

    private Calm() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, Calm::tab);
        if (!ModList.get().isLoaded("curios")) {
            HavenConsolidated.LOG.info("Haven Consolidated: no Curios, so the calming accessories cannot be worn and calm no mob");
            return;
        }
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            inventory = lookup.findStatic(api, "getCuriosInventory", MethodType.methodType(Optional.class, LivingEntity.class));
            isEquipped = lookup.findVirtual(handler, "isEquipped", MethodType.methodType(boolean.class, Item.class));
        } catch (ReflectiveOperationException e) {
            HavenConsolidated.LOG.error("Haven Consolidated: Curios' API changed, so the calming accessories calm no mob", e);
            return;
        }
        NeoForge.EVENT_BUS.addListener(LivingChangeTargetEvent.class, Calm::onChangeTarget);
    }

    private static void tab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath(HavenConsolidated.ID, "main"))) ALL.forEach(event::accept);
    }

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getNewAboutToBeSetTarget() instanceof Player player && ignores(mob, player)) {
            event.setCanceled(true);
        }
    }

    /** A new target only: the player it already hunts, or the one that last hurt it, stays a target. */
    public static boolean ignores(Mob mob, Player player) {
        if (mob.getTarget() == player || mob.getLastHurtByMob() == player) return false;
        for (DeferredItem<CalmingItem> item : ALL) {
            if (item.get().calms(mob.getType()) && wears(player, item.get())) return true;
        }
        return false;
    }

    private static boolean wears(Player player, Item item) {
        try {
            Optional<?> handler = (Optional<?>) inventory.invoke((LivingEntity) player);
            return handler.isPresent() && (boolean) isEquipped.invoke(handler.get(), item);
        } catch (Throwable t) {
            if (!readFailed) {   // once: target searches run every few ticks per mob
                readFailed = true;
                HavenConsolidated.LOG.error("Haven Consolidated: reading a player's Curios failed, so the accessories calm no mob", t);
            }
            return false;
        }
    }
}
