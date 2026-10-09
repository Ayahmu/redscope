package dev.redscope.scope.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.redscope.scope.logic.SampleReceiver.Outcome;
import org.junit.jupiter.api.Test;

class SampleReceiverTest {
	@Test
	void appliesConsecutiveSamples() {
		SampleReceiver receiver = new SampleReceiver();
		assertEquals(Outcome.APPLIED, receiver.onSample(0, 3));
		assertEquals(Outcome.APPLIED, receiver.onSample(1, 9));
		assertEquals(2, receiver.buffer().count());
		assertEquals(9, receiver.buffer().get(1));
	}

	@Test
	void dropsSamplesItAlreadyHas() {
		SampleReceiver receiver = new SampleReceiver();
		receiver.onSample(0, 1);
		receiver.onSample(1, 2);
		assertEquals(Outcome.DROPPED, receiver.onSample(1, 7));
		assertEquals(2, receiver.buffer().count());
		assertEquals(2, receiver.buffer().get(1));
	}

	@Test
	void reportsAGapOnceThenDropsUntilSnapshot() {
		SampleReceiver receiver = new SampleReceiver();
		receiver.onSample(0, 1);
		assertEquals(Outcome.GAP, receiver.onSample(5, 4));
		assertEquals(Outcome.DROPPED, receiver.onSample(6, 4));
		assertEquals(Outcome.DROPPED, receiver.onSample(7, 4));
		assertEquals(1, receiver.buffer().count());
	}

	@Test
	void snapshotEndsTheGapAndStreamingResumes() {
		SampleReceiver receiver = new SampleReceiver();
		receiver.onSample(0, 1);
		receiver.onSample(5, 4);
		receiver.onSnapshot(8, new byte[] {1, 2, 3});
		assertEquals(8, receiver.buffer().count());
		assertEquals(3, receiver.buffer().get(7));
		assertEquals(Outcome.APPLIED, receiver.onSample(8, 6));
		assertEquals(6, receiver.buffer().get(8));
	}

	@Test
	void canBeSeededFromAnOpenScreenSnapshot() {
		SampleReceiver receiver = new SampleReceiver();
		receiver.onSnapshot(100, new byte[] {5, 6});
		assertEquals(Outcome.APPLIED, receiver.onSample(100, 7));
		assertEquals(5, receiver.buffer().get(98));
		assertEquals(7, receiver.buffer().get(100));
	}
}
