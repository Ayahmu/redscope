package dev.redscope.registry;

import dev.redscope.RedScope;
import dev.redscope.scope.OpenData;
import dev.redscope.scope.OscilloscopeMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModMenus {
	public static final ExtendedMenuType<OscilloscopeMenu, OpenData> OSCILLOSCOPE = Registry.register(
		BuiltInRegistries.MENU,
		RedScope.id("oscilloscope"),
		new ExtendedMenuType<>(OscilloscopeMenu::new, OpenData.CODEC)
	);

	private ModMenus() {
	}

	public static void init() {
	}
}
