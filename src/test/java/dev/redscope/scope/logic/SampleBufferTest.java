package dev.redscope.scope.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SampleBufferTest {
	@Test
	void startsEmpty() {
		SampleBuffer buffer = new SampleBuffer();
		assertEquals(0, buffer.count());
		assertEquals(0, buffer.size());
		assertEquals(0, buffer.oldestIndex());
	}

	@Test
	void appendAndGetByAbsoluteIndex() {
		SampleBuffer buffer = new SampleBuffer();
		buffer.append(3);
		buffer.append(15);
		buffer.append(0);
		assertEquals(3, buffer.count());
		assertEquals(3, buffer.size());
		assertEquals(3, buffer.get(0));
		assertEquals(15, buffer.get(1));
		assertEquals(0, buffer.get(2));
	}

	@Test
	void overwritesOldestWhenFull() {
		SampleBuffer buffer = new SampleBuffer();
		for (int i = 0; i < SampleBuffer.CAPACITY + 5; i++) {
			buffer.append(i % 16);
		}
		assertEquals(SampleBuffer.CAPACITY + 5, buffer.count());
		assertEquals(SampleBuffer.CAPACITY, buffer.size());
		assertEquals(5, buffer.oldestIndex());
		assertEquals(5 % 16, buffer.get(5));
		assertEquals((SampleBuffer.CAPACITY + 4) % 16, buffer.get(SampleBuffer.CAPACITY + 4));
	}

	@Test
	void getOutsideRetainedRangeThrows() {
		SampleBuffer buffer = new SampleBuffer();
		for (int i = 0; i < SampleBuffer.CAPACITY + 1; i++) {
			buffer.append(1);
		}
		assertThrows(IllegalArgumentException.class, () -> buffer.get(0));
		assertThrows(IllegalArgumentException.class, () -> buffer.get(buffer.count()));
	}

	@Test
	void appendRejectsOutOfRangeValues() {
		SampleBuffer buffer = new SampleBuffer();
		assertThrows(IllegalArgumentException.class, () -> buffer.append(-1));
		assertThrows(IllegalArgumentException.class, () -> buffer.append(16));
	}

	@Test
	void snapshotRoundTripKeepsAbsoluteIndices() {
		SampleBuffer source = new SampleBuffer();
		for (int i = 0; i < SampleBuffer.CAPACITY + 37; i++) {
			source.append((i * 7) % 16);
		}

		SampleBuffer copy = new SampleBuffer();
		copy.restore(source.count(), source.snapshot());

		assertEquals(source.count(), copy.count());
		assertEquals(source.oldestIndex(), copy.oldestIndex());
		for (long i = source.oldestIndex(); i < source.count(); i++) {
			assertEquals(source.get(i), copy.get(i));
		}
	}

	@Test
	void snapshotOfPartialBufferIsOldestFirst() {
		SampleBuffer buffer = new SampleBuffer();
		buffer.append(1);
		buffer.append(2);
		buffer.append(3);
		assertArrayEquals(new byte[] {1, 2, 3}, buffer.snapshot());
	}

	@Test
	void restoreReplacesPreviousContents() {
		SampleBuffer buffer = new SampleBuffer();
		for (int i = 0; i < 50; i++) {
			buffer.append(9);
		}
		buffer.restore(10, new byte[] {4, 5});
		assertEquals(10, buffer.count());
		assertEquals(2, buffer.size());
		assertEquals(8, buffer.oldestIndex());
		assertEquals(4, buffer.get(8));
		assertEquals(5, buffer.get(9));
	}
}
