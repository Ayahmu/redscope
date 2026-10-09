package dev.redscope.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.item.CreativeModeTabs;

public final class ModCreativeTabs {
	private ModCreativeTabs() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> output.accept(ModBlocks.OSCILLOSCOPE_ITEM));
	}
}
