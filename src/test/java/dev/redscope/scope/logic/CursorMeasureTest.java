package dev.redscope.scope.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CursorMeasureTest {
	@Test
	void convertsTicksToUnits() {
		CursorMeasure measure = CursorMeasure.between(100, 120);
		assertEquals(20, measure.ticks());
		assertEquals(10.0, measure.redstoneTicks(), 1e-9);
		assertEquals(1.0, measure.seconds(), 1e-9);
		assertEquals(1.0, measure.frequencyHz().getAsDouble(), 1e-9);
	}

	@Test
	void orderOfCursorsDoesNotMatter() {
		assertEquals(CursorMeasure.between(5, 25), CursorMeasure.between(25, 5));
	}

	@Test
	void zeroDifferenceHasNoFrequency() {
		CursorMeasure measure = CursorMeasure.between(7, 7);
		assertEquals(0, measure.ticks());
		assertEquals(0.0, measure.seconds(), 1e-9);
		assertTrue(measure.frequencyHz().isEmpty());
	}

	@Test
	void oneRedstoneTickIsTwoGameTicks() {
		CursorMeasure measure = CursorMeasure.between(0, 2);
		assertEquals(1.0, measure.redstoneTicks(), 1e-9);
		assertEquals(10.0, measure.frequencyHz().getAsDouble(), 1e-9);
	}
}
