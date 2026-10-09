package dev.redscope.scope.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TriggerSearchTest {
	private static SampleBuffer bufferOf(int... values) {
		SampleBuffer buffer = new SampleBuffer();
		for (int value : values) {
			buffer.append(value);
		}
		return buffer;
	}

	@Test
	void findsMostRecentRisingEdge() {
		// indices:           0  1  2  3  4  5  6  7  8
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 0);
		assertEquals(6, TriggerSearch.find(buffer, Edge.RISING, 8, 0, 8).getAsLong());
	}

	@Test
	void findsMostRecentFallingEdge() {
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 0);
		assertEquals(8, TriggerSearch.find(buffer, Edge.FALLING, 8, 0, 8).getAsLong());
	}

	@Test
	void upperBoundSkipsLaterEdges() {
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 0);
		assertEquals(2, TriggerSearch.find(buffer, Edge.RISING, 8, 0, 5).getAsLong());
	}

	@Test
	void lowerBoundSkipsEarlierEdges() {
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 0);
		assertTrue(TriggerSearch.find(buffer, Edge.RISING, 8, 7, 8).isEmpty());
	}

	@Test
	void levelIsInclusiveOnTheHighSide() {
		SampleBuffer buffer = bufferOf(3, 4, 5, 4);
		// rising through level 5 happens at index 2 (4 -> 5)
		assertEquals(2, TriggerSearch.find(buffer, Edge.RISING, 5, 0, 3).getAsLong());
		// falling below level 5 happens at index 3 (5 -> 4)
		assertEquals(3, TriggerSearch.find(buffer, Edge.FALLING, 5, 0, 3).getAsLong());
	}

	@Test
	void flatSignalHasNoEdge() {
		SampleBuffer buffer = bufferOf(7, 7, 7, 7);
		assertTrue(TriggerSearch.find(buffer, Edge.RISING, 5, 0, 3).isEmpty());
		assertTrue(TriggerSearch.find(buffer, Edge.FALLING, 5, 0, 3).isEmpty());
	}

	@Test
	void offNeverTriggers() {
		SampleBuffer buffer = bufferOf(0, 15, 0, 15);
		assertTrue(TriggerSearch.find(buffer, Edge.OFF, 5, 0, 3).isEmpty());
	}

	@Test
	void boundsAreClampedToRetainedRange() {
		SampleBuffer buffer = new SampleBuffer();
		for (int i = 0; i < SampleBuffer.CAPACITY + 10; i++) {
			buffer.append(i % 2 == 0 ? 0 : 15);
		}
		// index 0 is no longer retained; the search must not throw
		assertTrue(TriggerSearch.find(buffer, Edge.RISING, 8, -50, buffer.count() + 50).isPresent());
	}

	@Test
	void emptyBufferHasNoEdge() {
		assertTrue(TriggerSearch.find(new SampleBuffer(), Edge.RISING, 8, 0, 100).isEmpty());
	}

	@Test
	void freeRunShowsNewestSamplesWhenTriggerIsOff() {
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 0, 0);
		ViewWindow window = TriggerSearch.resolve(buffer, 9, 6, Edge.OFF, 8);
		assertEquals(4, window.start());
		assertFalse(window.triggered());
	}

	@Test
	void triggeredWindowPlacesEdgeAtOneQuarter() {
		// window of 8 ticks: the edge sits at offset 2 (one quarter) from the left
		SampleBuffer buffer = bufferOf(0, 0, 0, 0, 0, 0, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15);
		ViewWindow window = TriggerSearch.resolve(buffer, 15, 8, Edge.RISING, 8);
		assertTrue(window.triggered());
		assertEquals(6, window.edge());
		assertEquals(4, window.start());
	}

	@Test
	void triggerNeedsEnoughSamplesAfterTheEdge() {
		// the newest rising edge (index 6) has only 3 samples after it, but a window of 8
		// with the edge at offset 2 needs 5, so the search must fall back to the edge at index 2
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 15, 15);
		ViewWindow window = TriggerSearch.resolve(buffer, 9, 8, Edge.RISING, 8);
		assertTrue(window.triggered());
		assertEquals(2, window.edge());
	}

	@Test
	void noEdgeFallsBackToFreeRunUntriggered() {
		SampleBuffer buffer = bufferOf(5, 5, 5, 5, 5, 5, 5, 5, 5, 5);
		ViewWindow window = TriggerSearch.resolve(buffer, 9, 8, Edge.RISING, 8);
		assertFalse(window.triggered());
		assertEquals(2, window.start());
	}

	@Test
	void newestIsClampedToWhatExists() {
		SampleBuffer buffer = bufferOf(1, 2, 3);
		ViewWindow window = TriggerSearch.resolve(buffer, 99, 8, Edge.OFF, 8);
		assertEquals(-5, window.start());
	}
}
