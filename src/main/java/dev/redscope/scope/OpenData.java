package dev.redscope.scope;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What the server sends along with "open this screen": where the scope is and its history so far. */
public record OpenData(BlockPos pos, long count, byte[] samples) {
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, OpenData::pos,
		ByteBufCodecs.VAR_LONG, OpenData::count,
		ByteBufCodecs.BYTE_ARRAY, OpenData::samples,
		OpenData::new
	);
}
