package dev.redscope.registry;

import dev.redscope.RedScope;
import dev.redscope.scope.OscilloscopeBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final ResourceKey<Block> OSCILLOSCOPE_KEY = ResourceKey.create(Registries.BLOCK, RedScope.id("oscilloscope"));
	public static final ResourceKey<Item> OSCILLOSCOPE_ITEM_KEY = ResourceKey.create(Registries.ITEM, RedScope.id("oscilloscope"));

	public static final Block OSCILLOSCOPE = Blocks.register(
		OSCILLOSCOPE_KEY,
		OscilloscopeBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
	);

	public static final BlockItem OSCILLOSCOPE_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		OSCILLOSCOPE_ITEM_KEY,
		new BlockItem(OSCILLOSCOPE, new Item.Properties().setId(OSCILLOSCOPE_ITEM_KEY).useBlockDescriptionPrefix())
	);

	private ModBlocks() {
	}

	public static void init() {
		// Touching this class runs the static initializers above, which do the registration.
		OSCILLOSCOPE_ITEM.registerBlocks(Item.BY_BLOCK, OSCILLOSCOPE_ITEM);
	}
}
