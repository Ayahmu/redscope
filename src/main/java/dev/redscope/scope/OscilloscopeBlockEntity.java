package dev.redscope.scope;

import dev.redscope.registry.ModBlockEntities;
import dev.redscope.scope.logic.SampleBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Samples the redstone signal at its own position once per game tick. The history is deliberately
 * not saved to disk.
 */
public class OscilloscopeBlockEntity extends BlockEntity {
	private final SampleBuffer buffer = new SampleBuffer();

	public OscilloscopeBlockEntity(final BlockPos pos, final BlockState state) {
		super(ModBlockEntities.OSCILLOSCOPE, pos, state);
	}

	public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final OscilloscopeBlockEntity scope) {
		scope.buffer.append(level.getBestNeighborSignal(pos));
	}

	public SampleBuffer buffer() {
		return this.buffer;
	}
}
