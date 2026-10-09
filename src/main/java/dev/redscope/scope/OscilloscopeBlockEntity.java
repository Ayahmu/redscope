package dev.redscope.scope;

import dev.redscope.registry.ModBlockEntities;
import dev.redscope.scope.logic.SampleBuffer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Samples the redstone signal at its own position once per game tick and streams the samples to
 * every player who has the screen open. The history is deliberately not saved to disk.
 */
public class OscilloscopeBlockEntity extends BlockEntity implements ExtendedMenuProvider<OpenData> {
	private final SampleBuffer buffer = new SampleBuffer();
	private final List<OscilloscopeMenu> viewers = new ArrayList<>();

	public OscilloscopeBlockEntity(final BlockPos pos, final BlockState state) {
		super(ModBlockEntities.OSCILLOSCOPE, pos, state);
	}

	public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final OscilloscopeBlockEntity scope) {
		int signal = level.getBestNeighborSignal(pos);
		scope.buffer.append(signal);
		long index = scope.buffer.count() - 1;
		for (OscilloscopeMenu viewer : scope.viewers) {
			viewer.sendSample(index, signal);
		}
	}

	public SampleBuffer buffer() {
		return this.buffer;
	}

	public void removeViewer(final OscilloscopeMenu menu) {
		this.viewers.remove(menu);
	}

	@Override
	public OpenData getScreenOpeningData(final ServerPlayer player) {
		return new OpenData(this.worldPosition, this.buffer.count(), this.buffer.snapshot());
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.redscope.oscilloscope");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return null;
		}
		OscilloscopeMenu menu = new OscilloscopeMenu(containerId, this, serverPlayer);
		this.viewers.add(menu);
		return menu;
	}
}
