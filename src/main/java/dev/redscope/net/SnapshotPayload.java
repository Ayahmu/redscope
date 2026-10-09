package dev.redscope.net;

import dev.redscope.RedScope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Full buffer contents, sent in answer to a {@link ResyncRequestPayload}. */
public record SnapshotPayload(int containerId, long count, byte[] samples) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SnapshotPayload> TYPE = new CustomPacketPayload.Type<>(RedScope.id("snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SnapshotPayload::containerId,
		ByteBufCodecs.VAR_LONG, SnapshotPayload::count,
		ByteBufCodecs.BYTE_ARRAY, SnapshotPayload::samples,
		SnapshotPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
