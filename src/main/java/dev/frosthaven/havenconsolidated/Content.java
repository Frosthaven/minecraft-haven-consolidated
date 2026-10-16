package dev.frosthaven.havenconsolidated;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registers the items, blocks and fluids of everything in {@link Consolidated}, and one creative tab for them. */
public final class Content {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HavenConsolidated.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenConsolidated.ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, HavenConsolidated.ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, HavenConsolidated.ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HavenConsolidated.ID);

    /** Our item per id (block items and buckets included), in registration order. */
    public static final Map<ResourceLocation, DeferredItem<? extends Item>> ITEM_BY_ID = new LinkedHashMap<>();
    public static final Map<ResourceLocation, DeferredBlock<? extends Block>> BLOCK_BY_ID = new LinkedHashMap<>();
    /** Our fluids per id: the still fluid under the fluid id, the flowing one under its id. */
    public static final Map<ResourceLocation, DeferredHolder<Fluid, ? extends Fluid>> FLUID_BY_ID = new LinkedHashMap<>();
    public static final Map<ResourceLocation, DeferredHolder<FluidType, FluidType>> FLUID_TYPE_BY_ID = new LinkedHashMap<>();

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
        for (Consolidated.FluidEntry f : Consolidated.fluids()) {
            registerFluid(f);
        }
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + HavenConsolidated.ID))
                .icon(() -> ITEM_BY_ID.values().iterator().next().toStack())
                .displayItems((params, out) -> ITEM_BY_ID.values().forEach(i -> out.accept(i.get())))
                .build());
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** Still and flowing fluid, the fluid type, the block in the world and the bucket, all under the fluid's id. */
    private static void registerFluid(Consolidated.FluidEntry f) {
        String name = f.fluid().getPath();
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(typeProperties(f.kind())));
        var refs = new Object() {
            DeferredHolder<Fluid, BaseFlowingFluid.Source> still;
            DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
            DeferredBlock<LiquidBlock> block;
            DeferredItem<BucketItem> bucket;
        };
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(type, () -> refs.still.get(), () -> refs.flowing.get())
                .bucket(() -> refs.bucket.get()).block(() -> refs.block.get());
        refs.still = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(props));
        refs.flowing = FLUIDS.register(f.flowing().getPath(), () -> new BaseFlowingFluid.Flowing(props));
        refs.block = BLOCKS.register(name, () -> new LiquidBlock(refs.still.get(),
                BlockBehaviour.Properties.ofFullCopy(f.kind() == Consolidated.FluidKind.MOLTEN ? Blocks.LAVA : Blocks.WATER)));
        refs.bucket = ITEMS.register(f.bucket().getPath(), () -> new BucketItem(refs.still.get(),
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        FLUID_TYPE_BY_ID.put(f.fluid(), type);
        FLUID_BY_ID.put(f.fluid(), refs.still);
        FLUID_BY_ID.put(f.flowing(), refs.flowing);
        BLOCK_BY_ID.put(f.fluid(), refs.block);
        ITEM_BY_ID.put(f.bucket(), refs.bucket);
    }

    private static FluidType.Properties typeProperties(Consolidated.FluidKind kind) {
        FluidType.Properties p = FluidType.Properties.create();
        return switch (kind) {
            case OIL -> p.density(900).viscosity(2000).temperature(300);
            case GAS -> p.density(-10).viscosity(100).temperature(300).canSwim(false).canDrown(false).canExtinguish(false).canConvertToSource(false);
            case MOLTEN -> p.density(6000).viscosity(6000).temperature(1800).lightLevel(15).canSwim(false).canDrown(false)
                    .canExtinguish(false).canConvertToSource(false).canHydrate(false);
            case LIQUID -> p;
        };
    }

    private static BlockBehaviour.Properties properties(Consolidated.Kind kind) {
        return switch (kind) {
            case ORE -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(3.0F, 3.0F);
            case DEEPSLATE_ORE -> BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE);
            case NETHER_ORE -> BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).requiresCorrectToolForDrops().strength(3.0F, 3.0F).sound(SoundType.NETHER_ORE);
            case MOON_ORE -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).requiresCorrectToolForDrops().strength(3.0F, 3.0F);
            case BASALT_ORE -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(3.0F, 4.2F).sound(SoundType.BASALT);
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

    static Fluid fluid(ResourceLocation id) {
        DeferredHolder<Fluid, ? extends Fluid> holder = FLUID_BY_ID.get(id);
        return holder == null ? null : holder.get();
    }

    private Content() {}
}
