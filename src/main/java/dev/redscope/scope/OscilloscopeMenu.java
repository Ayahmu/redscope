package dev.redscope.scope;

import dev.redscope.net.SamplePayload;
import dev.redscope.net.SnapshotPayload;
import dev.redscope.registry.ModMenus;
import dev.redscope.scope.logic.SampleBuffer;
import dev.redscope.scope.logic.SampleReceiver;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Has no slots. It exists so the server knows who is watching (it is created on open and its
 * {@link #removed} runs on close, disconnect or when {@link #stillValid} fails) and so the client
 * has a place to keep its copy of the history.
 */
public class OscilloscopeMenu extends AbstractContainerMenu {
	private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

	private final BlockPos pos;
	/** Client side only: this viewer's copy of the history. */
	private final SampleReceiver receiver = new SampleReceiver();
	/** Server side only. */
	private final @Nullable OscilloscopeBlockEntity scope;
	private final @Nullable ServerPlayer viewer;

	/** Client side: built from the data the server sent along with the open-screen packet. */
	public OscilloscopeMenu(final int containerId, final Inventory inventory, final OpenData data) {
		super(ModMenus.OSCILLOSCOPE, containerId);
		this.pos = data.pos();
		this.receiver.onSnapshot(data.count(), data.samples());
		this.scope = null;
		this.viewer = null;
	}

	/** Server side. */
	public OscilloscopeMenu(final int containerId, final OscilloscopeBlockEntity scope, final ServerPlayer viewer) {
		super(ModMenus.OSCILLOSCOPE, containerId);
		this.pos = scope.getBlockPos();
		this.scope = scope;
		this.viewer = viewer;
	}

	public BlockPos pos() {
		return this.pos;
	}

	public SampleBuffer buffer() {
		return this.receiver.buffer();
	}

	/** Server side: forward one new sample to this viewer. */
	void sendSample(final long index, final int value) {
		if (this.viewer != null) {
			ServerPlayNetworking.send(this.viewer, new SamplePayload(this.containerId, index, value));
		}
	}

	/** Server side: send this viewer the whole history. */
	public void sendSnapshot() {
		if (this.viewer != null && this.scope != null) {
			SampleBuffer source = this.scope.buffer();
			ServerPlayNetworking.send(this.viewer, new SnapshotPayload(this.containerId, source.count(), source.snapshot()));
		}
	}

	/** Client side: handles a sample that arrived from the server. */
	public SampleReceiver.Outcome onSample(final long index, final int value) {
		return this.receiver.onSample(index, value);
	}

	/** Client side: replaces the history with a snapshot from the server. */
	public void onSnapshot(final long count, final byte[] samples) {
		this.receiver.onSnapshot(count, samples);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(final Player player) {
		if (this.scope == null) {
			return true;
		}
		return !this.scope.isRemoved() && player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
	}

	@Override
	public void removed(final Player player) {
		super.removed(player);
		if (this.scope != null) {
			this.scope.removeViewer(this);
		}
	}
}
