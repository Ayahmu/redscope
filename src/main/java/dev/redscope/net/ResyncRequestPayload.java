package dev.redscope.net;

import dev.redscope.RedScope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client noticed a gap in the sample indices and wants a fresh snapshot. */
public record ResyncRequestPayload(int containerId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ResyncRequestPayload> TYPE = new CustomPacketPayload.Type<>(RedScope.id("resync_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ResyncRequestPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ResyncRequestPayload::containerId,
		ResyncRequestPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
