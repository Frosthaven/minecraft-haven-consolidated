package dev.frosthaven.havenconsolidated;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registers the items and blocks of every metal in {@link Consolidated}, and one creative tab for them. */
public final class Content {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HavenConsolidated.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenConsolidated.ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HavenConsolidated.ID);

    /** Our item per form id (block items included), in registration order. */
    public static final Map<ResourceLocation, DeferredItem<? extends Item>> ITEM_BY_ID = new LinkedHashMap<>();
    public static final Map<ResourceLocation, DeferredBlock<? extends Block>> BLOCK_BY_ID = new LinkedHashMap<>();

    static void register(IEventBus modBus) {
        for (Consolidated.Metal metal : Consolidated.metals()) {
            for (Consolidated.Entry entry : metal.entries()) {
                String name = entry.id().getPath();
                if (entry.kind().isBlock()) {
                    DeferredBlock<Block> block = BLOCKS.register(name, () -> new DropExperienceBlock(ConstantInt.of(0), properties(entry.kind())));
                    BLOCK_BY_ID.put(entry.id(), block);
                    ITEM_BY_ID.put(entry.id(), ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
                } else {
                    ITEM_BY_ID.put(entry.id(), ITEMS.register(name, () -> new Item(new Item.Properties())));
                }
            }
        }
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + HavenConsolidated.ID))
                .icon(() -> ITEM_BY_ID.values().iterator().next().toStack())
                .displayItems((params, out) -> ITEM_BY_ID.values().forEach(i -> out.accept(i.get())))
                .build());
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    private static BlockBehaviour.Properties properties(Consolidated.Kind kind) {
        return switch (kind) {
            case ORE -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(3.0F, 3.0F);
            case DEEPSLATE_ORE -> BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE);
            case BLOCK -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL);
            case RAW_BLOCK -> BlockBehaviour.Properties.of().mapColor(MapColor.RAW_IRON).requiresCorrectToolForDrops().strength(5.0F, 6.0F);
            case ITEM -> throw new IllegalArgumentException("not a block");
        };
    }

    /** Any registered holder's item, for the alias table. */
    static Item item(ResourceLocation id) {
        DeferredHolder<Item, ? extends Item> holder = ITEM_BY_ID.get(id);
        return holder == null ? null : holder.get();
    }

    private Content() {}
}
