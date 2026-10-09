package dev.redscope.registry;

import dev.redscope.net.ResyncRequestPayload;
import dev.redscope.net.SamplePayload;
import dev.redscope.net.SnapshotPayload;
import dev.redscope.scope.OscilloscopeMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModPayloads {
	private ModPayloads() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(SamplePayload.TYPE, SamplePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SnapshotPayload.TYPE, SnapshotPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ResyncRequestPayload.TYPE, ResyncRequestPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ResyncRequestPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof OscilloscopeMenu menu && menu.containerId == payload.containerId()) {
				menu.sendSnapshot();
			}
		});
	}
}
