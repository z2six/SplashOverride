// MainFile: src/main/java/org/z2six/splashoverride/Splashoverride.java
package org.z2six.splashoverride;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Splashoverride – a tiny NeoForge 1.21.1 client mod that overrides main-menu
 * splashes using remote/config-specified text instead of relying solely on
 * assets/minecraft/texts/splashes.txt.
 */
@Mod(Splashoverride.MODID)
public class Splashoverride {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "splashoverride";

    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // These are just the template's example registries; harmless to keep.
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<Block> EXAMPLE_BLOCK =
            BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));

    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    public static final DeferredItem<Item> EXAMPLE_ITEM =
            ITEMS.registerSimpleItem("example_item",
                    new Item.Properties().food(new FoodProperties
                            .Builder()
                            .alwaysEdible()
                            .nutrition(1)
                            .saturationModifier(2f)
                            .build()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB =
            CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.splashoverride"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(EXAMPLE_ITEM.get()))
                    .build());

    public Splashoverride(IEventBus modEventBus, ModContainer modContainer) {
        // Register lifecycle listeners
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        NeoForge.EVENT_BUS.register(this);

        // Register our CLIENT config (where URL + local splashes live)
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);

        // Simple debug log – only use the valid getKey API here
        LOGGER.debug("[SplashOverride] Mod initialization complete. Dirt block key for debug: {}",
                BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Nothing critical here; just a debug log so we see the mod booted.
        LOGGER.info("[SplashOverride] Common setup – core logic lives client-side in SplashManager mixin.");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Keep the template example item registration working; irrelevant to splashes but harmless.
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Server hook kept simple. SplashOverride is primarily client-focused.
        LOGGER.info("[SplashOverride] Server starting – splash override is client-only, but mod is loaded.");
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("[SplashOverride] Client setup – user={}, useRemoteSource={}, remoteUrl={}",
                    Minecraft.getInstance().getUser().getName(),
                    Config.useRemoteSource,
                    Config.remoteUrl);
        }
    }
}
