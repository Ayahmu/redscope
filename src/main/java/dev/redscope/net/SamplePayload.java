package dev.redscope.net;

import dev.redscope.RedScope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** One new sample, sent to every viewer once per game tick. */
public record SamplePayload(int containerId, long index, int value) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SamplePayload> TYPE = new CustomPacketPayload.Type<>(RedScope.id("sample"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SamplePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SamplePayload::containerId,
		ByteBufCodecs.VAR_LONG, SamplePayload::index,
		ByteBufCodecs.VAR_INT, SamplePayload::value,
		SamplePayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
