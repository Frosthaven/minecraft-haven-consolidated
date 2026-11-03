package dev.frosthaven.havenconsolidated.calm;

import dev.frosthaven.havenconsolidated.HavenConsolidated;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A Curios accessory that calms the mobs in its entity tag {@code haven_consolidated:calmed_by_<item>} ({@link Calm}).
 * The tooltip lines come from the lang file ({@code item.haven_consolidated.<item>.tooltip.<n>}).
 */
public final class CalmingItem extends Item {
    private static final int TOOLTIP_LINES = 2;
    private final String name;
    private final TagKey<EntityType<?>> calms;

    CalmingItem(String name) {
        super(new Item.Properties().stacksTo(1));
        this.name = name;
        this.calms = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(HavenConsolidated.ID, "calmed_by_" + name));
    }

    boolean calms(EntityType<?> type) {
        return type.is(calms);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        for (int i = 0; i < TOOLTIP_LINES; i++) {
            lines.add(Component.translatable("item." + HavenConsolidated.ID + "." + name + ".tooltip." + i).withStyle(ChatFormatting.GRAY));
        }
    }
}
