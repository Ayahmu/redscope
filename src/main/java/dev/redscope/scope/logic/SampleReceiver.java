package dev.redscope.scope.logic;

/**
 * Client-side end of the sample stream. Applies samples in order and notices when some are
 * missing, in which case the caller should ask the server for a snapshot.
 */
public final class SampleReceiver {
	public enum Outcome {
		/** The sample was appended. */
		APPLIED,
		/** The sample was already known, or a resync is under way; nothing changed. */
		DROPPED,
		/** Samples are missing. Reported once; the caller must request a snapshot. */
		GAP
	}

	private final SampleBuffer buffer = new SampleBuffer();
	private boolean resyncPending;

	public SampleBuffer buffer() {
		return this.buffer;
	}

	public Outcome onSample(final long index, final int value) {
		if (this.resyncPending || index < this.buffer.count()) {
			return Outcome.DROPPED;
		}
		if (index > this.buffer.count()) {
			this.resyncPending = true;
			return Outcome.GAP;
		}
		this.buffer.append(value);
		return Outcome.APPLIED;
	}

	public void onSnapshot(final long count, final byte[] samples) {
		this.buffer.restore(count, samples);
		this.resyncPending = false;
	}
}
