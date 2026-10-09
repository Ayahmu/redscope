package dev.redscope.scope.logic;

import java.util.OptionalLong;

public final class TriggerSearch {
	private TriggerSearch() {
	}

	/**
	 * Finds the most recent edge crossing {@code level} with an index in {@code [minIndex, maxIndex]}.
	 *
	 * <p>A rising edge at index i means sample i-1 is below {@code level} and sample i is at or above
	 * it. A falling edge is the opposite. The bounds are clamped to what the buffer retains.
	 * {@link Edge#OFF} never triggers.
	 *
	 * @return the absolute index of sample i, or empty if there is no such edge
	 */
	public static OptionalLong find(final SampleBuffer buffer, final Edge edge, final int level, final long minIndex, final long maxIndex) {
		if (edge == Edge.OFF || buffer.size() < 2) {
			return OptionalLong.empty();
		}
		long low = Math.max(minIndex, buffer.oldestIndex() + 1);
		long high = Math.min(maxIndex, buffer.count() - 1);
		for (long i = high; i >= low; i--) {
			boolean before = buffer.get(i - 1) >= level;
			boolean now = buffer.get(i) >= level;
			if (edge == Edge.RISING && !before && now) {
				return OptionalLong.of(i);
			}
			if (edge == Edge.FALLING && before && !now) {
				return OptionalLong.of(i);
			}
		}
		return OptionalLong.empty();
	}

	/**
	 * Decides which samples one screen of {@code windowTicks} columns shows.
	 *
	 * <p>With a trigger the newest usable edge is placed one quarter from the left. An edge is
	 * usable if the whole window around it is available: enough samples before it are retained and
	 * enough after it have arrived by {@code newest}. Without a usable edge, or with
	 * {@link Edge#OFF}, the window free-runs and ends at {@code newest}.
	 *
	 * @param newest the newest absolute index to show; clamped to what the buffer holds
	 */
	public static ViewWindow resolve(final SampleBuffer buffer, final long newest, final int windowTicks, final Edge edge, final int level) {
		long last = Math.min(newest, buffer.count() - 1);
		int before = windowTicks / 4;
		int after = windowTicks - before - 1;
		long minEdge = buffer.oldestIndex() + before;
		long maxEdge = last - after;
		OptionalLong found = find(buffer, edge, level, minEdge, maxEdge);
		if (found.isPresent()) {
			return new ViewWindow(found.getAsLong() - before, true, found.getAsLong());
		}
		return new ViewWindow(last - windowTicks + 1, false, -1);
	}
}
