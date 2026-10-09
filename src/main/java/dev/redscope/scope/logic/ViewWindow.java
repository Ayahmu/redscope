package dev.redscope.scope.logic;

/**
 * Which samples one screen of the scope shows.
 *
 * @param start     absolute index of the leftmost column; may be negative or older than the
 *                  buffer retains, the renderer skips columns that have no sample
 * @param triggered true if {@code start} was aligned to a trigger edge
 * @param edge      absolute index of the trigger edge, or -1 when not triggered
 */
public record ViewWindow(long start, boolean triggered, long edge) {
}
