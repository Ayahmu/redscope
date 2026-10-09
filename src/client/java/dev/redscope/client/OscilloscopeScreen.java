package dev.redscope.client;

import dev.redscope.scope.OscilloscopeMenu;
import dev.redscope.scope.logic.Edge;
import dev.redscope.scope.logic.SampleBuffer;
import dev.redscope.scope.logic.TriggerSearch;
import dev.redscope.scope.logic.ViewWindow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Read-only first version: live trace of the last 100 ticks. Controls are added later. */
public class OscilloscopeScreen extends Screen implements MenuAccess<OscilloscopeMenu> {
	private static final int WINDOW = 100;

	private static final int COLOR_PLOT_BG = 0xFF0B1A10;
	private static final int COLOR_GRID_MINOR = 0xFF173022;
	private static final int COLOR_GRID_MAJOR = 0xFF2B5A3A;
	private static final int COLOR_FRAME = 0xFF8A8A8A;
	private static final int COLOR_TRACE = 0xFF4CFF7A;
	private static final int COLOR_TEXT = 0xFFE0E0E0;

	private final OscilloscopeMenu menu;

	private int plotX;
	private int plotY;
	private int plotW;
	private int plotH;

	public OscilloscopeScreen(final OscilloscopeMenu menu, final Inventory inventory, final Component title) {
		super(title);
		this.menu = menu;
	}

	@Override
	public OscilloscopeMenu getMenu() {
		return this.menu;
	}

	@Override
	protected void init() {
		this.plotW = Math.min(this.width - 60, 400);
		this.plotH = Math.max(64, Math.min(this.height - 110, 176));
		this.plotX = (this.width - this.plotW) / 2;
		this.plotY = 24;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		SampleBuffer buffer = this.menu.buffer();
		long newest = buffer.count() - 1;
		ViewWindow view = TriggerSearch.resolve(buffer, newest, WINDOW, Edge.OFF, 8);

		graphics.centeredText(this.font, this.title, this.width / 2, 8, COLOR_TEXT);
		this.drawGrid(graphics);
		this.drawTrace(graphics, buffer, view, newest);
		graphics.outline(this.plotX - 1, this.plotY - 1, this.plotW + 2, this.plotH + 2, COLOR_FRAME);
	}

	private int yOf(final int value) {
		return this.plotY + this.plotH - 1 - Math.round(value * (this.plotH - 1) / 15.0F);
	}

	private void drawGrid(final GuiGraphicsExtractor graphics) {
		graphics.fill(this.plotX, this.plotY, this.plotX + this.plotW, this.plotY + this.plotH, COLOR_PLOT_BG);
		for (int value = 0; value <= 15; value++) {
			boolean major = value % 5 == 0;
			graphics.horizontalLine(this.plotX, this.plotX + this.plotW - 1, this.yOf(value), major ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR);
			if (major) {
				graphics.text(this.font, Integer.toString(value), this.plotX - 14, this.yOf(value) - 4, COLOR_TEXT);
			}
		}
		for (int i = 0; i <= 10; i++) {
			int gx = this.plotX + i * (this.plotW - 1) / 10;
			graphics.verticalLine(gx, this.plotY, this.plotY + this.plotH - 1, i % 5 == 0 ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR);
		}
	}

	private void drawTrace(final GuiGraphicsExtractor graphics, final SampleBuffer buffer, final ViewWindow view, final long newest) {
		int previousY = -1;
		for (int px = 0; px < this.plotW; px++) {
			long from = view.start() + (long)px * WINDOW / this.plotW;
			long to = Math.max(from + 1, view.start() + (long)(px + 1) * WINDOW / this.plotW);
			int low = Integer.MAX_VALUE;
			int high = -1;
			int last = -1;
			for (long index = from; index < to; index++) {
				if (index < buffer.oldestIndex() || index >= buffer.count() || index > newest) {
					continue;
				}
				int value = buffer.get(index);
				low = Math.min(low, value);
				high = Math.max(high, value);
				last = value;
			}
			if (last < 0) {
				previousY = -1;
				continue;
			}
			int top = this.yOf(high);
			int bottom = this.yOf(low);
			if (previousY >= 0) {
				top = Math.min(top, previousY);
				bottom = Math.max(bottom, previousY);
			}
			graphics.fill(this.plotX + px, top, this.plotX + px + 1, bottom + 2, COLOR_TRACE);
			previousY = this.yOf(last);
		}
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		if (this.minecraft.options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
			this.minecraft.player.closeContainer();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.player.closeContainer();
		super.onClose();
	}

	/** Must stay false: a paused game stops ticking, so the block would stop sampling. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
