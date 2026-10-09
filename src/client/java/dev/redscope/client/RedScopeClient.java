package dev.redscope.client;

import dev.redscope.net.ResyncRequestPayload;
import dev.redscope.net.SamplePayload;
import dev.redscope.net.SnapshotPayload;
import dev.redscope.registry.ModMenus;
import dev.redscope.scope.OscilloscopeMenu;
import dev.redscope.scope.logic.SampleReceiver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class RedScopeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.OSCILLOSCOPE, OscilloscopeScreen::new);

		ClientPlayNetworking.registerGlobalReceiver(SamplePayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof OscilloscopeMenu menu && menu.containerId == payload.containerId()) {
				if (menu.onSample(payload.index(), payload.value()) == SampleReceiver.Outcome.GAP) {
					ClientPlayNetworking.send(new ResyncRequestPayload(menu.containerId));
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(SnapshotPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof OscilloscopeMenu menu && menu.containerId == payload.containerId()) {
				menu.onSnapshot(payload.count(), payload.samples());
			}
		});
	}
}
