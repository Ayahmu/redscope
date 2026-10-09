package dev.redscope.scope.logic;

import java.util.OptionalDouble;

/** Distance between two cursors on the waveform, in game ticks, with unit conversions. */
public record CursorMeasure(long ticks) {
	public static final int GAME_TICKS_PER_SECOND = 20;
	public static final int GAME_TICKS_PER_REDSTONE_TICK = 2;

	public static CursorMeasure between(final long a, final long b) {
		return new CursorMeasure(Math.abs(b - a));
	}

	public double redstoneTicks() {
		return (double)this.ticks / GAME_TICKS_PER_REDSTONE_TICK;
	}

	public double seconds() {
		return (double)this.ticks / GAME_TICKS_PER_SECOND;
	}

	/** Frequency of a signal whose period is this distance; empty when the distance is zero. */
	public OptionalDouble frequencyHz() {
		return this.ticks == 0 ? OptionalDouble.empty() : OptionalDouble.of((double)GAME_TICKS_PER_SECOND / this.ticks);
	}
}
