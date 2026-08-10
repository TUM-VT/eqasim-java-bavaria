package org.eqasim.core.simulation.mode_choice.utilities;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temporary diagnostic counters to compare how many drt / feeder_drt candidates are
 * actually routed and estimated per DiscreteModeChoice iteration.
 */
public class CandidateCounter {
	public static final AtomicInteger DRT_ESTIMATOR_CALLS = new AtomicInteger();
	public static final AtomicInteger FEEDER_DRT_TRIPS = new AtomicInteger();
	public static final AtomicInteger FEEDER_INTERNAL_DRT_SEGMENTS = new AtomicInteger();

	public static int[] snapshotAndReset() {
		return new int[] { DRT_ESTIMATOR_CALLS.getAndSet(0), FEEDER_DRT_TRIPS.getAndSet(0),
				FEEDER_INTERNAL_DRT_SEGMENTS.getAndSet(0) };
	}
}
