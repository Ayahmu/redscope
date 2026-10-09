package dev.redscope.scope.logic;

/**
 * Fixed-size ring buffer of redstone signal strengths (0-15).
 *
 * <p>Samples are addressed by absolute index: the first sample ever appended has index 0, the next
 * index 1 and so on. Only the newest {@link #CAPACITY} samples are retained.
 */
public final class SampleBuffer {
	public static final int CAPACITY = 1024;

	private final byte[] data = new byte[CAPACITY];
	/** Index of the first sample that exists; non-zero only after {@link #restore}. */
	private long start;
	private long count;

	/** Total number of samples appended so far; also the index the next sample will get. */
	public long count() {
		return this.count;
	}

	/** Number of samples currently retained. */
	public int size() {
		return (int)(this.count - this.oldestIndex());
	}

	/** Absolute index of the oldest retained sample. */
	public long oldestIndex() {
		return Math.max(this.start, this.count - CAPACITY);
	}

	public void append(final int value) {
		if (value < 0 || value > 15) {
			throw new IllegalArgumentException("signal strength out of range: " + value);
		}
		this.data[(int)(this.count % CAPACITY)] = (byte)value;
		this.count++;
	}

	public int get(final long index) {
		if (index < this.oldestIndex() || index >= this.count) {
			throw new IllegalArgumentException("index " + index + " not retained, valid range is [" + this.oldestIndex() + ", " + this.count + ")");
		}
		return this.data[(int)(index % CAPACITY)];
	}

	/** Retained samples, oldest first. */
	public byte[] snapshot() {
		byte[] out = new byte[this.size()];
		long first = this.oldestIndex();
		for (int i = 0; i < out.length; i++) {
			out[i] = this.data[(int)((first + i) % CAPACITY)];
		}
		return out;
	}

	/**
	 * Replaces the contents so that {@code samples} are the newest samples and {@code endCount} is
	 * the total sample count, as produced by {@link #count()} and {@link #snapshot()}.
	 */
	public void restore(final long endCount, final byte[] samples) {
		if (samples.length > CAPACITY || endCount < samples.length) {
			throw new IllegalArgumentException("inconsistent snapshot");
		}
		this.start = endCount - samples.length;
		this.count = this.start;
		for (byte sample : samples) {
			this.append(sample);
		}
	}
}
