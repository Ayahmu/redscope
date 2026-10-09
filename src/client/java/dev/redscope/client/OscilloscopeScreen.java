package dev.redscope.client;

import dev.redscope.scope.OscilloscopeMenu;
import dev.redscope.scope.logic.CursorMeasure;
import dev.redscope.scope.logic.Edge;
import dev.redscope.scope.logic.SampleBuffer;
import dev.redscope.scope.logic.TriggerSearch;
import dev.redscope.scope.logic.ViewWindow;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class OscilloscopeScreen extends Screen implements MenuAccess<OscilloscopeMenu> {
	private static final List<Integer> WINDOWS = List.of(40, 100, 400, 1024);
	private static final int MIN_LEVEL = 1;
	private static final int MAX_LEVEL = 15;

	private static final int COLOR_PLOT_BG = 0xFF0B1A10;
	private static final int COLOR_GRID_MINOR = 0xFF173022;
	private static final int COLOR_GRID_MAJOR = 0xFF2B5A3A;
	private static final int COLOR_FRAME = 0xFF8A8A8A;
	private static final int COLOR_TRACE = 0xFF4CFF7A;
	private static final int COLOR_TRIGGER = 0xFFFFD34C;
	private static final int COLOR_CURSOR_A = 0xFF4CC3FF;
	private static final int COLOR_CURSOR_B = 0xFFFF6BD6;
	private static final int COLOR_TEXT = 0xFFE0E0E0;
	private static final int COLOR_WARNING = 0xFFFF8040;

	private final OscilloscopeMenu menu;

	private boolean paused;
	private long frozenNewest;
	private int window = WINDOWS.get(1);
	private Edge edge = Edge.OFF;
	private int level = 8;
	/** Cursor positions as a fraction of the plot width, so they stay put while the trace scrolls. */
	private final double[] cursors = {0.25, 0.5};
	private int dragging = -1;

	private int plotX;
	private int plotY;
	private int plotW;
	private int plotH;
	private Button pauseButton;

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

		int rowY = this.plotY + this.plotH + 6;
		int x = this.plotX;

		this.pauseButton = this.addRenderableWidget(Button.builder(this.pauseLabel(), button -> this.togglePause()).bounds(x, rowY, 56, 20).build());
		x += 60;

		this.addRenderableWidget(
			CycleButton.<Integer>builder(value -> Component.translatable("screen.redscope.window_value", value), this.window)
				.withValues(WINDOWS)
				.create(x, rowY, 86, 20, Component.translatable("screen.redscope.window"), (button, value) -> this.window = value)
		);
		x += 90;

		this.addRenderableWidget(
			CycleButton.<Edge>builder(value -> Component.translatable("screen.redscope.edge." + value.name().toLowerCase(Locale.ROOT)), this.edge)
				.withValues(Edge.values())
				.create(x, rowY, 96, 20, Component.translatable("screen.redscope.trigger"), (button, value) -> this.edge = value)
		);
		x += 100;

		this.addRenderableWidget(Button.builder(Component.literal("-"), button -> this.level = Math.max(MIN_LEVEL, this.level - 1)).bounds(x, rowY, 20, 20).build());
		x += 56;
		this.addRenderableWidget(Button.builder(Component.literal("+"), button -> this.level = Math.min(MAX_LEVEL, this.level + 1)).bounds(x, rowY, 20, 20).build());
	}

	private Component pauseLabel() {
		return Component.translatable(this.paused ? "screen.redscope.run" : "screen.redscope.pause");
	}

	private void togglePause() {
		this.paused = !this.paused;
		if (this.paused) {
			this.frozenNewest = this.menu.buffer().count() - 1;
		}
		this.pauseButton.setMessage(this.pauseLabel());
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		SampleBuffer buffer = this.menu.buffer();
		long newest = this.paused ? this.frozenNewest : buffer.count() - 1;
		ViewWindow view = TriggerSearch.resolve(buffer, newest, this.window, this.edge, this.level);

		graphics.centeredText(this.font, this.title, this.width / 2, 8, COLOR_TEXT);
		this.drawGrid(graphics);
		this.drawTrace(graphics, buffer, view, newest);
		this.drawTriggerLevel(graphics);
		this.drawCursors(graphics);
		graphics.outline(this.plotX - 1, this.plotY - 1, this.plotW + 2, this.plotH + 2, COLOR_FRAME);

		int labelY = this.plotY + this.plotH + 6 + 6;
		graphics.text(this.font, Component.translatable("screen.redscope.level", this.level), this.plotX + 60 + 90 + 100 + 22, labelY, COLOR_TEXT);

		if (this.edge != Edge.OFF && !view.triggered()) {
			graphics.text(this.font, Component.translatable("screen.redscope.no_trigger"), this.plotX + 4, this.plotY + 4, COLOR_WARNING);
		}
		this.drawReadout(graphics);
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
			long from = view.start() + (long)px * this.window / this.plotW;
			long to = Math.max(from + 1, view.start() + (long)(px + 1) * this.window / this.plotW);
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

	private void drawTriggerLevel(final GuiGraphicsExtractor graphics) {
		if (this.edge == Edge.OFF) {
			return;
		}
		int ty = this.yOf(this.level);
		for (int dx = 0; dx < this.plotW; dx += 8) {
			graphics.horizontalLine(this.plotX + dx, Math.min(this.plotX + dx + 3, this.plotX + this.plotW - 1), ty, COLOR_TRIGGER);
		}
	}

	private void drawCursors(final GuiGraphicsExtractor graphics) {
		for (int i = 0; i < 2; i++) {
			int cx = this.plotX + (int)Math.round(this.cursors[i] * (this.plotW - 1));
			graphics.verticalLine(cx, this.plotY, this.plotY + this.plotH - 1, i == 0 ? COLOR_CURSOR_A : COLOR_CURSOR_B);
		}
	}

	private long cursorOffset(final int cursor) {
		return Math.round(this.cursors[cursor] * (this.window - 1));
	}

	private void drawReadout(final GuiGraphicsExtractor graphics) {
		CursorMeasure measure = CursorMeasure.between(this.cursorOffset(0), this.cursorOffset(1));
		String frequency = measure.frequencyHz().isPresent() ? String.format(Locale.ROOT, "%.2f", measure.frequencyHz().getAsDouble()) : "--";
		Component text = Component.translatable(
			"screen.redscope.readout",
			measure.ticks(),
			String.format(Locale.ROOT, "%.1f", measure.redstoneTicks()),
			String.format(Locale.ROOT, "%.2f", measure.seconds()),
			frequency
		);
		graphics.text(this.font, text, this.plotX, this.plotY + this.plotH + 6 + 20 + 8, COLOR_TEXT);
	}

	private boolean inPlot(final double x, final double y) {
		return x >= this.plotX && x < this.plotX + this.plotW && y >= this.plotY && y < this.plotY + this.plotH;
	}

	private double fractionAt(final double x) {
		return Math.max(0.0, Math.min(1.0, (x - this.plotX) / (this.plotW - 1)));
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() == 0 && this.inPlot(event.x(), event.y())) {
			double fraction = this.fractionAt(event.x());
			this.dragging = Math.abs(fraction - this.cursors[0]) <= Math.abs(fraction - this.cursors[1]) ? 0 : 1;
			this.cursors[this.dragging] = fraction;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy) {
		if (this.dragging >= 0) {
			this.cursors[this.dragging] = this.fractionAt(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		this.dragging = -1;
		return super.mouseReleased(event);
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
