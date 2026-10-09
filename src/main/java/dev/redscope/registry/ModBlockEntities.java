package dev.redscope.registry;

import dev.redscope.RedScope;
import dev.redscope.scope.OscilloscopeBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<OscilloscopeBlockEntity> OSCILLOSCOPE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		RedScope.id("oscilloscope"),
		new BlockEntityType<>(OscilloscopeBlockEntity::new, Set.of(ModBlocks.OSCILLOSCOPE))
	);

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
