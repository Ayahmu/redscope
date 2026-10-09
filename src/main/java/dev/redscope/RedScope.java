package dev.redscope;

import dev.redscope.registry.ModBlockEntities;
import dev.redscope.registry.ModBlocks;
import dev.redscope.registry.ModCreativeTabs;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedScope implements ModInitializer {
	public static final String MOD_ID = "redscope";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModBlockEntities.init();
		ModCreativeTabs.init();
		LOGGER.info("Red Scope loaded");
	}
}
